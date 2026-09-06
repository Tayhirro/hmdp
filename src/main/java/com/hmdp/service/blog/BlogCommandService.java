package com.hmdp.service.blog;

/*
 * 现实业务背景：用户在发布页点击发布，或作者在自己的博客上执行编辑、删除时，需要完整维护博客、图片和权限。
 * 实际触发：POST/PUT/DELETE 博客接口经 BlogServiceImpl 门面（博客模块对 Controller 的统一入口，
 * 再分发到查询/命令/点赞等服务）进入本类，事务边界也落在这些写命令上。
 */

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.BlogPublishRequest;
import com.hmdp.dto.BlogUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.BlogImage;
import com.hmdp.entity.BlogLike;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogImageService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责发布、编辑和删除博客。
 *
 * 代码按下面的规则组织：
 * 
 *     1. 重复发布不重复创建：前端为一次发布生成唯一的 clientRequestId（1-64 位字母、数字、_ 或 -，重试时复用同一值），
 *     双击、超时重试等相同 POST 请求由 {@link BlogIdempotencyService}（幂等控制服务，保证同一份请求只生效一次）
 *     识别后直接返回第一次创建的博客 ID。
 *     2. 一次发布要完整成功：新增博客、绑定图片和保存请求结果处于同一个数据库事务；
 *     中间任何一步失败，前面的数据库修改都会撤销。
 *     3. 缩短编辑锁定时间：先用无锁查询校验标题（不超过 255 字符）、正文（不超过 2048 字符）、
 *     图片数量（1-9 张且不重复）和商户是否存在，全部通过后才用 SELECT ... FOR UPDATE 锁定博客行；
 *     无效请求不会长时间挡住其他人读取或修改这条记录。
 *     4. 只更新允许编辑的列：编辑走专门的 updateEditableFields 语句，只能修改商户、标题、正文和图片，
 *     不能借请求覆盖作者（user_id）、点赞数（liked）、评论数（comments）等服务端维护的数据。
 *     5. 正文只存普通文本：HTML 不写入数据库，页面展示时也不执行正文中的标签，
 *     既减少脚本注入风险，也避免反复编辑后出现 {@code &amp;lt;} 等重复转义。
 * 
 */
@Service
public class BlogCommandService {

    private static final int MAX_BLOG_IMAGES = 9;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 2048;
    private static final int MAX_CLIENT_REQUEST_ID_LENGTH = 64;

    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;
    private final BlogCommentsMapper blogCommentsMapper;
    private final IShopService shopService;
    private final IBlogImageService blogImageService;
    private final BlogIdempotencyService idempotencyService;

    /**
     * 构造函数：注入博客/点赞/评论 Mapper、商户服务、图片服务与幂等服务（由 Spring 装配时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogCommandService(
            BlogMapper blogMapper,
            BlogLikeMapper blogLikeMapper,
            BlogCommentsMapper blogCommentsMapper,
            IShopService shopService,
            IBlogImageService blogImageService,
            BlogIdempotencyService idempotencyService
    ) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
        this.blogCommentsMapper = blogCommentsMapper;
        this.shopService = shopService;
        this.blogImageService = blogImageService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 发布一篇新博客：校验请求、做幂等判定，并在同一事务中创建博客、绑定图片、记录幂等结果。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl}（博客门面）的 saveBlog()，
     * 对应 HTTP 路由 POST /blog（BlogController.saveBlog）。
     * 实现要点：先做确定性的本地校验与规范化——clientRequestId（1-64 位字母、数字、_ 或 -）、
     * 标题（去首尾空白后不超过 255 字符）、正文（去空白、统一换行后不超过 2048 字符）、
     * 图片（1-9 张且 ID 不重复），并计算请求指纹（SHA-256，见 calculatePublishHash）；
     * 随后调用 {@link BlogIdempotencyService}（本包幂等服务）的 begin()，命中幂等（shouldUsePreviousResult 为真）时
     * 直接返回第一次创建的博客 ID，不再校验商户和图片；否则校验商户存在（shopService.getById 查 tb_shop，1 条 SQL）、
     * 经 blogImageService.loadOwnedTemporaryImages 加载本人临时图片，执行 blogMapper.insert 插入 tb_blog（1 条 SQL），
     * 再经 blogImageService.bindToBlog 把图片绑定到新博客，最后 idempotencyService.complete() 把博客 ID 写回幂等记录；
     * @Transactional 保证新增博客、图片绑定和幂等记录要么全部提交要么全部回滚。
     */
    @Transactional
    public Result publish(BlogPublishRequest request) {
        Long userId = requireCurrentUserId();
        if (request == null) {
            throw BusinessException.badRequest("BLOG_PUBLISH_REQUIRED", "发布内容不能为空");
        }

        // 这里只做确定性的本地规范化；幂等命中必须早于会变化的商户和图片数据库校验。
        String requestKey = validateClientRequestId(request.getClientRequestId());
        String title = normalizeTitle(request.getTitle());
        String content = normalizeContent(request.getContent());
        validateImageIds(request.getImageIds());
        String requestHash = calculatePublishHash(
                request.getShopId(), title, content, request.getImageIds());

        IdempotencyDecision decision = idempotencyService.begin(userId, requestKey, requestHash);
        if (decision.shouldUsePreviousResult()) {
            return Result.ok(decision.getResourceId());
        }

        validateShop(request.getShopId());
        List<BlogImage> images = blogImageService.loadOwnedTemporaryImages(request.getImageIds(), userId);
        Blog blog = new Blog()
                .setShopId(request.getShopId())
                .setUserId(userId)
                .setTitle(title)
                .setContent(content)
                .setImages(joinImageUrls(images));
        if (blogMapper.insert(blog) != 1 || blog.getId() == null) {
            throw new IllegalStateException("保存博客失败");
        }

        blogImageService.bindToBlog(request.getImageIds(), userId, blog.getId());
        idempotencyService.complete(decision, blog.getId());
        return Result.ok(blog.getId());
    }

    /**
     * 编辑一篇已有博客：全量替换商户、标题、正文和图片，仅允许作者本人操作。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 updateBlog()，
     * 对应 HTTP 路由 PUT /blog/{id}（BlogController.updateBlog）。
     * 实现要点：无锁校验先行（标题不超过 255 字符、正文不超过 2048 字符、图片 1-9 张不重复、商户存在），
     * 之后由 loadOwnedBlogForWrite 用 SELECT ... FOR UPDATE 锁定 tb_blog 行并校验作者；
     * blogImageService.replaceBlogImages 计算被移除的图片并重排保留图片，loadOwnedBlogImages 查回最终图片列表，
     * blogMapper.updateEditableFields（自定义 UPDATE，仅能修改 shop_id、title、content、images 列，1 条 SQL）更新 1 行；
     * 事务提交后由 blogImageService.schedulePhysicalDeletionAfterCommit 物理删除被移除的图片文件；
     * @Transactional 保证数据库改动原子生效，图片文件删除只在提交成功后进行。
     */
    @Transactional
    public Result update(Long id, BlogUpdateRequest request) {
        Long userId = requireCurrentUserId();
        if (request == null) {
            throw BusinessException.badRequest("BLOG_UPDATE_REQUIRED", "编辑内容不能为空");
        }

        // 无锁校验先失败，避免无效请求占用热门博客的行锁。
        requireBlogId(id);
        String title = normalizeTitle(request.getTitle());
        String content = normalizeContent(request.getContent());
        validateImageIds(request.getImageIds());
        validateShop(request.getShopId());

        Blog existing = loadOwnedBlogForWrite(id, userId);
        List<BlogImage> removed = blogImageService.replaceBlogImages(
                request.getImageIds(), userId, existing.getId());
        List<BlogImage> images = blogImageService.loadOwnedBlogImages(
                request.getImageIds(), userId, existing.getId());
        if (blogMapper.updateEditableFields(
                existing.getId(),
                userId,
                request.getShopId(),
                title,
                content,
                joinImageUrls(images)) != 1) {
            throw new IllegalStateException("更新博客失败");
        }
        blogImageService.schedulePhysicalDeletionAfterCommit(removed);
        return Result.ok(existing.getId());
    }

    /**
     * 删除一篇博客，连同其点赞关系和评论，并把图片标记为事务提交后物理删除。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 deleteBlog()，
     * 对应 HTTP 路由 DELETE /blog/{id}（BlogController.deleteBlog）。
     * 实现要点：loadOwnedBlogForWrite 锁定 tb_blog 行并校验作者；blogImageService.detachAllBoundImages
     * 把全部绑定图片标记待删除；随后 3 条删除 SQL——tb_blog_like（条件 blog_id = id）、
     * tb_blog_comments（条件 blog_id = id）、tb_blog（按主键 deleteById）；幂等记录（tb_idempotency_record）刻意不删，
     * 防止在途的旧发布请求把已删除的博客重新创建出来；提交后 schedulePhysicalDeletionAfterCommit 删除图片文件；
     * @Transactional 保证关系删除原子完成。
     */
    @Transactional
    public Result delete(Long id) {
        Long userId = requireCurrentUserId();
        Blog existing = loadOwnedBlogForWrite(id, userId);
        List<BlogImage> images = blogImageService.detachAllBoundImages(userId, existing.getId());
        blogLikeMapper.delete(new LambdaQueryWrapper<BlogLike>().eq(BlogLike::getBlogId, id));
        blogCommentsMapper.delete(new LambdaQueryWrapper<BlogComments>().eq(BlogComments::getBlogId, id));
        if (blogMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除博客失败");
        }
        // 不删除请求记录：防止已经在路上的旧发布请求，在博客删除后又创建一篇相同博客。
        // 旧请求再次到达时只返回原 blogId，不会恢复或重新创建已删除的博客。
        blogImageService.schedulePhysicalDeletionAfterCommit(images);
        return Result.ok();
    }

    /**
     * 加锁查出博客并确认当前用户是作者，供写操作使用。
     * 使用场景：本类 update() 与 delete() 在修改数据前调用。
     * 实现要点：先 requireBlogId 校验 ID 非空，再执行 blogMapper.selectByIdForUpdate
     * （SELECT 整行 FROM tb_blog WHERE id 等于参数并附带 FOR UPDATE 行锁，1 条 SQL，
     * 事务结束前其他写请求阻塞等待）；博客不存在抛 404（BLOG_NOT_FOUND），
     * 作者（blog.user_id）不是当前用户抛 403（无权修改）。
     */
    private Blog loadOwnedBlogForWrite(Long id, Long userId) {
        requireBlogId(id);
        Blog blog = blogMapper.selectByIdForUpdate(id);
        if (blog == null) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        if (!userId.equals(blog.getUserId())) {
            throw BusinessException.forbidden("无权修改该笔记");
        }
        return blog;
    }

    /**
     * 从登录上下文取当前用户 ID，未登录则抛业务异常。
     * 使用场景：本类 publish()、update()、delete() 的第一步，确保每条写命令必有作者。
     * 实现要点：读取 ThreadLocal 中的 {@link UserDTO}（用户 DTO，由登录拦截器写入 UserHolder）；
     * 用户为 null 或 ID 为 null 时抛 401（未授权）。纯内存判断，无 SQL。
     */
    private Long requireCurrentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user.getId();
    }

    /**
     * 校验博客 ID 参数非空。
     * 使用场景：本类 update() 直接调用，以及 loadOwnedBlogForWrite() 在加锁查询前调用。
     * 实现要点：id 为 null 时抛 400（BLOG_ID_REQUIRED）；只做判空，不查数据库。
     */
    private void requireBlogId(Long id) {
        if (id == null) {
            throw BusinessException.badRequest("BLOG_ID_REQUIRED", "博客ID不能为空");
        }
    }

    /**
     * 校验并规范化客户端幂等键 clientRequestId。
     * 使用场景：仅被本类 publish() 在幂等判定之前调用。
     * 实现要点：空白抛 400（CLIENT_REQUEST_ID_REQUIRED）；去除首尾空白后要求 1-64 位且只含字母、数字、下划线或横线
     * （正则 [A-Za-z0-9_-]+，上限对应常量 MAX_CLIENT_REQUEST_ID_LENGTH = 64），不合法抛 400（INVALID_CLIENT_REQUEST_ID）；
     * 规范化结果作为 tb_idempotency_record 的 request_key 列，参与（user_id, request_key）唯一约束。
     * 纯内存校验，无 SQL。
     */
    private String validateClientRequestId(String value) {
        if (StrUtil.isBlank(value)) {
            throw BusinessException.badRequest("CLIENT_REQUEST_ID_REQUIRED", "clientRequestId 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_CLIENT_REQUEST_ID_LENGTH
                || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw BusinessException.badRequest(
                    "INVALID_CLIENT_REQUEST_ID",
                    "clientRequestId 仅支持 1-64 位字母、数字、_ 或 -");
        }
        return normalized;
    }

    /**
     * 校验并规范化博客标题。
     * 使用场景：本类 publish() 与 update() 在写入前调用。
     * 实现要点：空白抛 400（BLOG_TITLE_REQUIRED）；去除首尾空白后长度不得超过常量 MAX_TITLE_LENGTH = 255，
     * 超长抛 400（BLOG_TITLE_TOO_LONG）；返回规范化后的标题。纯内存校验，无 SQL。
     */
    private String normalizeTitle(String title) {
        if (StrUtil.isBlank(title)) {
            throw BusinessException.badRequest("BLOG_TITLE_REQUIRED", "标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw BusinessException.badRequest(
                    "BLOG_TITLE_TOO_LONG",
                    "标题不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
        return normalized;
    }

    /**
     * 校验并规范化博客正文（只按普通文本入库）。
     * 使用场景：本类 publish() 与 update() 在写入前调用。
     * 实现要点：空白抛 400（BLOG_CONTENT_REQUIRED）；去除首尾空白、把 CRLF 与单独 CR 统一成 LF 换行，
     * 长度不得超过常量 MAX_CONTENT_LENGTH = 2048，超长抛 400（BLOG_CONTENT_TOO_LONG）；
     * 不做 HTML 转义，正文中的标签按普通文本保存。纯内存校验，无 SQL。
     */
    private String normalizeContent(String content) {
        if (StrUtil.isBlank(content)) {
            throw BusinessException.badRequest("BLOG_CONTENT_REQUIRED", "正文不能为空");
        }
        String normalized = content.trim().replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw BusinessException.badRequest("BLOG_CONTENT_TOO_LONG", "正文内容过长");
        }
        return normalized;
    }

    /**
     * 校验发布/编辑请求选择的商户存在。
     * 使用场景：本类 publish() 与 update() 调用；publish 中该校验位于幂等命中判定之后，
     * 保证相同请求的重复提交不会因商户后来被删而被误拦。
     * 实现要点：shopId 为 null 抛 400（SHOP_ID_REQUIRED）；shopService.getById 查 tb_shop（1 条 SQL），
     * 查不到抛 400（SHOP_NOT_FOUND）。
     */
    private void validateShop(Long shopId) {
        if (shopId == null) {
            throw BusinessException.badRequest("SHOP_ID_REQUIRED", "请选择商户");
        }
        if (shopService.getById(shopId) == null) {
            throw BusinessException.badRequest("SHOP_NOT_FOUND", "请选择有效商户");
        }
    }

    /**
     * 校验请求携带的图片 ID 列表的数量与去重性。
     * 使用场景：本类 publish() 与 update() 在向图片服务加载图片之前调用。
     * 实现要点：只做本地校验不查数据库——列表为空抛 400（BLOG_IMAGE_REQUIRED）；
     * 超过常量 MAX_BLOG_IMAGES = 9 张抛 400（TOO_MANY_BLOG_IMAGES）；
     * 含 null 或存在重复 ID（LinkedHashSet 去重后数量与原列表不一致）抛 400（INVALID_BLOG_IMAGE_IDS）。
     */
    private void validateImageIds(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw BusinessException.badRequest("BLOG_IMAGE_REQUIRED", "请至少上传一张图片");
        }
        if (imageIds.size() > MAX_BLOG_IMAGES) {
            throw BusinessException.badRequest(
                    "TOO_MANY_BLOG_IMAGES",
                    "最多上传 " + MAX_BLOG_IMAGES + " 张图片");
        }
        Set<Long> distinct = new LinkedHashSet<>(imageIds);
        if (distinct.contains(null) || distinct.size() != imageIds.size()) {
            throw BusinessException.badRequest("INVALID_BLOG_IMAGE_IDS", "图片ID不能为空或重复");
        }
    }

    /**
     * 把图片实体列表拼成 tb_blog.images 列存储的逗号分隔 URL 字符串。
     * 使用场景：本类 publish()（新博客 images 列的初值）与 update()（更新后的 images 列）调用。
     * 实现要点：按列表顺序取每个 {@link BlogImage}（博客图片实体，对应 tb_blog_image 表）的 publicUrl
     * （对外可访问 URL），用英文逗号连接；纯内存计算，无 SQL。
     */
    private String joinImageUrls(List<BlogImage> images) {
        return images.stream().map(BlogImage::getPublicUrl).collect(Collectors.joining(","));
    }

    /**
     * 计算发布请求的内容指纹（SHA-256 十六进制摘要），用于幂等判断两次请求内容是否完全相同。
     * 使用场景：仅被本类 publish() 调用，结果传给 {@link BlogIdempotencyService} 的 begin()，
     * 与 tb_idempotency_record 记录的 request_hash 列比对。
     * 实现要点：纯内存计算，无 SQL——把 shopId、标题、正文、图片 ID 列表各经 encodeHashPart
     * （长度前缀 + 内容，防止相邻字段内容粘连产生相同摘要）拼接成规范串，按 UTF-8 编码后用 SHA-256 摘要，
     * 转成 64 位小写十六进制字符串；JVM 缺少 SHA-256 实现时抛 IllegalStateException。
     */
    private String calculatePublishHash(
            Long shopId,
            String title,
            String content,
            List<Long> imageIds
    ) {
        String canonical = encodeHashPart(String.valueOf(shopId))
                + encodeHashPart(title)
                + encodeHashPart(content)
                + encodeHashPart(imageIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 支持", e);
        }
    }

    /**
     * 把一个字段编码成“长度:内容”片段，供 calculatePublishHash 拼接规范串。
     * 使用场景：仅被本类 calculatePublishHash() 对参与指纹的 4 个字段（shopId、标题、正文、图片 ID 列表）分别调用。
     * 实现要点：返回“字符长度 + 冒号 + 原文”，确保不同字段切分方式不会拼出相同摘要；纯内存计算，无 SQL。
     */
    private String encodeHashPart(String value) {
        return value.length() + ":" + value;
    }
}
