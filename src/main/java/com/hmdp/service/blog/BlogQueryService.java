package com.hmdp.service.blog;

/*
 * 现实业务背景：用户打开博客详情、热门页、我的笔记或其他作者主页时，需要读取对应博客并稳定翻页。
 * 实际触发：GET /blog/{id}、/blog/hot、/blog/of/me、/blog/of/user 经博客门面（BlogServiceImpl）进入本类。
 */

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.CursorPayload;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogImage;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.BlogImageMapper;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 负责博客详情、热门博客和指定作者博客的只读查询。
 *     1. 数据库对象不直接给前端：Mapper 只查 Entity（与 tb_blog 表对应的 Blog 实体），
 *     {@link BlogAssembler}（把查出的 Blog 批量拼装成返回给前端的卡片/详情 DTO 的组件）再挑选接口允许的字段，
 *     数据库以后加列不会偷偷改变接口返回内容。
 *     2. 整页批量补充作者和点赞状态：以一页 20 篇博客（limit 最大 50，不传默认 10）为例，
 *     第 1 条 SQL 查博客本身（tb_blog），第 2 条把 20 篇的作者 ID（blog.user_id）去重后一次性查回作者（tb_user），
 *     第 3 条一次性查回当前用户对这 20 篇的点赞记录（tb_blog_like），共 3 条 SQL；
 *     不批量则为每篇博客各查一次作者和点赞，需要 1 + 20×2 = 41 条。
 *     3. 游标记录上一页最后位置（作者博客接口）：游标 =（score：上一页最后一条博客的 createTime 发布时间
 *     转成的 UTC epoch 毫秒值，id：博客主键）；排序规则 ORDER BY create_time DESC, id DESC，
 *     id 用来在两条博客发布时间相同时分先后，续查条件即“发布时间更早，或时间相同但 id 更小”。
 *     时间统一按 UTC 转换，服务器部署在不同时区也会得到同一个位置。
 *     4. 当前热榜会实时变化：热榜游标 =（score：liked 点赞数，id：博客主键）；
 *     排序规则 ORDER BY liked DESC, id DESC，id 用来在两条博客点赞数相同时分先后。
 *     翻页期间如果点赞数变化，极少数博客仍可能重复或漏掉；只有固定一份排名快照才能完全避免。
 *     （两类列表都通过多查 1 条，LIMIT pageSize + 1，来探测是否还有下一页。）
 * 
 */
@Service
public class BlogQueryService {

    private static final String HOT_CURSOR = "blog-hot-v1";
    private static final String USER_BLOG_CURSOR = "user-blog-v2";

    private final BlogMapper blogMapper;
    private final BlogImageMapper blogImageMapper;
    private final BlogAssembler blogAssembler;
    private final CursorCodec cursorCodec;

    /**
     * 构造函数：注入博客/图片 Mapper、装配器与游标编解码器（由 Spring 装配时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogQueryService(
            BlogMapper blogMapper,
            BlogImageMapper blogImageMapper,
            BlogAssembler blogAssembler,
            CursorCodec cursorCodec
    ) {
        this.blogMapper = blogMapper;
        this.blogImageMapper = blogImageMapper;
        this.blogAssembler = blogAssembler;
        this.cursorCodec = cursorCodec;
    }

    /**
     * 查询单篇博客详情：博客本体、按顺序排列的已绑定图片 ID，以及作者和当前用户点赞状态。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl}（博客门面）的 queryBlogById()，
     * 对应 HTTP 路由 GET /blog/{id}（BlogController.queryBlogById）；不强制登录。
     * 实现要点：requireBlog 校验并查回博客（1 条 SELECT tb_blog）；再查 tb_blog_image 取本篇图片
     * （1 条 SQL，条件 blog_id = id 且 status = STATUS_BOUND（已绑定），按 sort_order、id 升序，只取 ID 列）；
     * 最后交给 {@link BlogAssembler}（本包装配器）的 toDetail 补齐作者摘要与当前用户点赞状态（SQL 细节见该方法）。
     */
    public Result detail(Long id) {
        Blog blog = requireBlog(id);
        return Result.ok(blogAssembler.toDetail(blog).setImageIds(blogImageMapper.selectList(
                        new LambdaQueryWrapper<BlogImage>()
                                .select(BlogImage::getId)
                                .eq(BlogImage::getBlogId, id)
                                .eq(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                                .orderByAsc(BlogImage::getSortOrder, BlogImage::getId))
                .stream()
                .map(BlogImage::getId)
                .collect(Collectors.toList())));
    }

    /**
     * 游标分页查询热门博客榜（按点赞数倒序）。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 queryHotBlog()，
     * 对应 HTTP 路由 GET /blog/hot（BlogController.queryHotBlog）；不强制登录。
     * 实现要点：1 条 SQL 查 tb_blog，排序 ORDER BY liked DESC, id DESC，LIMIT pageSize + 1 多查 1 条探测 hasMore；
     * 游标 =（score：上一页最后一条博客的 liked 点赞数，id：博客主键），类型标记 HOT_CURSOR = "blog-hot-v1"，
     * 经 cursorCodec.decode 解码校验，续查条件“liked 小于游标点赞数，或点赞数相同但 id 小于游标 id”
     * （点赞数经 toLikedScore 校验并还原为 int）；热榜实时变化，翻页期间点赞数变化时个别博客可能重复或遗漏；
     * 结果经 toCursorPage 装配成卡片并生成 nextCursor。
     */
    public Result hot(String cursor, Integer limit) {
        int pageSize = normalizePageSize(limit);
        CursorPayload position = cursorCodec.decode(cursor, HOT_CURSOR);
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .orderByDesc(Blog::getLiked, Blog::getId)
                .last("LIMIT " + (pageSize + 1));
        if (position != null) {
            requirePosition(position);
            int liked = toLikedScore(position.getScore());
            wrapper.and(query -> query.lt(Blog::getLiked, liked)
                    .or(nested -> nested.eq(Blog::getLiked, liked)
                            .lt(Blog::getId, position.getId())));
        }
        return Result.ok(toCursorPage(blogMapper.selectList(wrapper), pageSize, HOT_CURSOR, true));
    }

    /**
     * 游标分页查询当前登录用户自己发布的博客。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 queryMyBlogs()，
     * 对应 HTTP 路由 GET /blog/of/me（BlogController.queryMyBlog）；作者 ID 取自登录上下文，不接受前端传入。
     * 实现要点：requireCurrentUserId 取当前用户后完全委托 authorBlogs() 执行查询（实现要点见该方法）。
     */
    public Result currentUserBlogs(String cursor, Integer limit) {
        return authorBlogs(requireCurrentUserId(), cursor, limit);
    }

    /**
     * 游标分页查询指定用户发布的博客（公开主页）。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 queryBlogsByUserId()，
     * 对应 HTTP 路由 GET /blog/of/user?id={userId}（BlogController.queryBlogByUserId）；不强制登录。
     * 实现要点：userId 为 null 抛 400（USER_ID_REQUIRED），随后委托 authorBlogs() 查询；
     * 不额外校验用户是否存在，目标用户没有博客时返回空列表。
     */
    public Result userBlogs(Long userId, String cursor, Integer limit) {
        if (userId == null) {
            throw BusinessException.badRequest("USER_ID_REQUIRED", "用户ID不能为空");
        }
        return authorBlogs(userId, cursor, limit);
    }

    /**
     * 按发布时间倒序游标分页查询指定作者的博客，是 currentUserBlogs 与 userBlogs 的公共实现。
     * 使用场景：本类 currentUserBlogs()（GET /blog/of/me）与 userBlogs()（GET /blog/of/user）调用。
     * 实现要点：1 条 SQL 查 tb_blog，条件 user_id = 作者 ID，排序 ORDER BY create_time DESC, id DESC，
     * LIMIT pageSize + 1 多查 1 条探测 hasMore；游标 =（score：上一页最后一条博客 create_time 转成的
     * UTC epoch 毫秒，id：博客主键），类型标记 USER_BLOG_CURSOR = "user-blog-v2"，经 cursorCodec.decode
     * 解码校验，续查条件“create_time 早于游标时间，或时间相同但 id 小于游标 id”（时间经 toUtcLocalDateTime 还原）；
     * 结果经 toCursorPage 装配成卡片并生成 nextCursor。
     */
    private Result authorBlogs(Long userId, String cursor, Integer limit) {
        int pageSize = normalizePageSize(limit);
        CursorPayload position = cursorCodec.decode(cursor, USER_BLOG_CURSOR);
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getCreateTime, Blog::getId)
                .last("LIMIT " + (pageSize + 1));
        if (position != null) {
            requirePosition(position);
            LocalDateTime time = toUtcLocalDateTime(position.getScore());
            wrapper.and(query -> query.lt(Blog::getCreateTime, time)
                    .or(nested -> nested.eq(Blog::getCreateTime, time)
                            .lt(Blog::getId, position.getId())));
        }
        return Result.ok(toCursorPage(
                blogMapper.selectList(wrapper), pageSize, USER_BLOG_CURSOR, false));
    }

    /**
     * 把一页查询结果装配成卡片分页对象（转卡片、探测下一页、生成 nextCursor）。
     *
     * 使用场景：本类 hot()（热榜，游标分页键 = 点赞数）与 authorBlogs()（作者列表，游标分页键 = 发布时间）调用。
     * 两条列表的排序轴不同，所以游标里记录"上一页最后一条位置"的 score 取值不同，
     * 由 useLikedScore 决定：true 时 score = 最后一条博客的 liked 点赞数（热榜按 liked DESC 排序）；
     * false 时 score = 最后一条博客的 createTime 转 UTC 毫秒（作者列表按 create_time DESC 排序）。
     * 调用方按位置传参，hot() 传 true、authorBlogs() 传 false，改调用时注意别传反。
     *
     * 实现要点：rows 是 LIMIT pageSize + 1 的探测查询结果，倒数第 1 条就是"下一页有没有"的信号；
     * 有下一页时取页内最后一条（第 pageSize 条）的对应字段做 score，连同 type、id 编码进
     * {@link CursorPayload}（游标载荷 DTO）并经 cursorCodec.encode 得到 nextCursor；type 为调用方传入的
     * 游标类型（blog-hot-v1 或 user-blog-v2），卡片列表由 {@link BlogAssembler} 的 toCards 生成。
     */
    private CursorPageDTO<BlogCardDTO> toCursorPage(
            List<Blog> rows,
            int pageSize,
            String cursorType,
            boolean useLikedScore
    ) {
        boolean hasMore = rows.size() > pageSize;
        List<Blog> pageRows = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : rows;
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Blog last = pageRows.get(pageRows.size() - 1);
            long score = useLikedScore
                    ? (last.getLiked() == null ? 0L : last.getLiked().longValue())
                    : toEpochMilli(last.getCreateTime());
            CursorPayload payload = new CursorPayload();
            payload.setType(cursorType);
            payload.setScore(score);
            payload.setId(last.getId());
            nextCursor = cursorCodec.encode(payload);
        }
        return new CursorPageDTO<>(blogAssembler.toCards(pageRows), nextCursor, hasMore);
    }

    /**
     * 校验博客 ID 非空且博客存在，返回查到的博客实体。
     * 使用场景：仅被本类 detail() 调用（列表接口按作者或全表条件查询，不做单篇存在性校验）。
     * 实现要点：id 为 null 抛 400（BLOG_ID_REQUIRED）；blogMapper.selectById 查 tb_blog（1 条 SQL），
     * 不存在抛 404（BLOG_NOT_FOUND）。
     */
    private Blog requireBlog(Long id) {
        if (id == null) {
            throw BusinessException.badRequest("BLOG_ID_REQUIRED", "博客ID不能为空");
        }
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return blog;
    }

    /**
     * 从登录上下文取当前用户 ID，未登录则抛业务异常。
     * 使用场景：仅被本类 currentUserBlogs() 调用，用于确定“我的博客”的作者 ID。
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
     * 规范化分页大小参数 limit。
     * 使用场景：本类 hot() 与 authorBlogs()（currentUserBlogs、userBlogs 经后者间接生效）调用。
     * 实现要点：null 时取常量 SystemConstants.MAX_PAGE_SIZE = 10（与 Controller 默认值一致）；
     * 小于 1 或大于 50 抛 400（INVALID_PAGE_SIZE）。纯内存校验，无 SQL。
     */
    private int normalizePageSize(Integer limit) {
        if (limit == null) {
            return SystemConstants.MAX_PAGE_SIZE;
        }
        if (limit < 1 || limit > 50) {
            throw BusinessException.badRequest("INVALID_PAGE_SIZE", "limit 必须在 1 到 50 之间");
        }
        return limit;
    }

    /**
     * 校验解码后的游标位置字段完整合法。
     * 使用场景：本类 hot() 与 authorBlogs() 在带游标续查时调用。
     * 实现要点：纯内存校验，无 SQL——score（点赞数或 UTC epoch 毫秒）不得为 null 或负数，
     * id（博客主键）不得为 null 或非正数，否则抛 400（INVALID_CURSOR）。
     */
    private void requirePosition(CursorPayload payload) {
        if (payload.getScore() == null || payload.getScore() < 0
                || payload.getId() == null || payload.getId() <= 0) {
            throw BusinessException.badRequest("INVALID_CURSOR", "分页游标缺少必要位置");
        }
    }

    /**
     * 把游标中的点赞数 score 还原为 int 值，用于热榜续查条件。
     * 使用场景：仅被本类 hot() 在带游标续查时调用。
     * 实现要点：纯内存校验，无 SQL——score 为 null、负数或超出 int 正数上限
     * （tb_blog.liked 为整型列）时抛 400（INVALID_CURSOR）。
     */
    private int toLikedScore(Long score) {
        if (score == null || score < 0 || score > Integer.MAX_VALUE) {
            throw BusinessException.badRequest("INVALID_CURSOR", "热榜分页游标超出有效范围");
        }
        return score.intValue();
    }

    /**
     * 把博客发布时间转成 UTC 时区的 epoch 毫秒值，作为作者博客列表游标的 score。
     * 使用场景：仅被本类 toCursorPage() 在 authorBlogs 路径（useLikedScore 为 false）生成游标时调用。
     * 实现要点：按 ZoneOffset.UTC 换算，保证服务器部署在不同时区也得到相同游标；
     * 时间为 null 抛 IllegalStateException。纯内存计算，无 SQL。
     */
    private long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            throw new IllegalStateException("分页字段 createTime 不能为空");
        }
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 把游标中的 epoch 毫秒值还原成 UTC 时区的 LocalDateTime，用于续查条件比较，与 toEpochMilli 互为逆操作。
     * 使用场景：仅被本类 authorBlogs() 在带游标续查时调用（热榜的 score 是点赞数，不走本方法）。
     * 实现要点：按 ZoneOffset.UTC 换算；数值超出 LocalDateTime 有效范围抛 400（INVALID_CURSOR）。
     */
    private LocalDateTime toUtcLocalDateTime(long epochMilli) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
        } catch (DateTimeException e) {
            throw BusinessException.badRequest("INVALID_CURSOR", "分页游标时间超出有效范围");
        }
    }
}
