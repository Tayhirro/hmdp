package com.hmdp.service.blog;

/*
 * 现实业务背景：用户点击点赞、取消点赞，或博客详情页加载最近点赞用户时，需要维护点赞关系和总数。
 * 实际触发：PUT/DELETE /blog/{id}/like 与 GET /blog/likes/{id} 经博客门面进入本类。
 */

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.BlogLikeStateDTO;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.CursorPayload;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.entity.User;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 负责点赞、取消点赞、查询点赞状态和读取点赞用户列表。
 *     1. 接口直接表达最终目的：PUT 表示“操作完成后必须是已点赞”，DELETE 表示“必须是未点赞”。
 *     所以同一个 PUT 因网络问题执行两次，第二次插入会命中唯一键、被捕获后忽略，
 *     不会把点赞反转成取消；DELETE 重复执行时删不到行，也不会再扣减总数。
 *     2. 数据库决定谁真正点赞成功：tb_blog_like 表上 (blog_id 被点赞的博客 ID, user_id 点赞用户 ID)
 *     两列的唯一约束保证一个用户对一篇博客只有一条关系。
 *     两个并发 PUT 中只有一个能插入成功，只有它会把博客的点赞总数（tb_blog.liked 列）加一。
 *     3. 点赞关系和总数一起成功或一起撤销：二者放在同一个事务中。
 *     返回前重新读取 MySQL 最终状态，前端直接用返回的 liked（是否已点赞）和 likeCount（点赞总数）覆盖本地值。
 *     4. 点赞用户列表用游标分页：排序规则 ORDER BY create_time（点赞时间）DESC, id（tb_blog_like 主键）DESC，
 *     id 用来在两条点赞时间相同时分先后；游标 =（score：上一页最后一条点赞记录的 createTime 转成的 UTC epoch 毫秒值，
 *     id：该条点赞记录的主键），类型标记为 "blog-like-v2"；每次多查 1 条（LIMIT pageSize + 1）来探测是否还有下一页。
 *     5. 点赞用户批量查询：以一页 50 个用户（limit 上限 50，不传默认 10）为例，第 1 条 SQL 查本页的点赞关系
 *     （tb_blog_like），第 2 条把这页的用户 ID 一次性查回用户资料（tb_user），共 2 条 SQL；
 *     用户显示顺序在内存中按点赞时间恢复，既避免为每个用户单独查询（否则要 1 + 50 = 51 条），
 *     也避免拼接难维护的动态排序 SQL。
 * 
 */
@Service
public class BlogLikeService {

    private static final String LIKE_CURSOR = "blog-like-v2";

    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;
    private final IUserService userService;
    private final CursorCodec cursorCodec;

    /**
     * 构造函数：注入博客/点赞 Mapper、用户服务与游标编解码器（由 Spring 装配时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogLikeService(
            BlogMapper blogMapper,
            BlogLikeMapper blogLikeMapper,
            IUserService userService,
            CursorCodec cursorCodec
    ) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
        this.userService = userService;
        this.cursorCodec = cursorCodec;
    }

    /**
     * 点赞一篇博客：插入点赞关系，仅首次插入成功时给博客点赞总数加一，返回最终点赞状态。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl}（博客门面）的 likeBlog()，
     * 对应 HTTP 路由 PUT /blog/{id}/like（BlogController.likeBlog）。
     * 实现要点：@Transactional 事务内执行——requireBlog 先校验博客存在（1 条 SELECT tb_blog）；
     * blogLikeMapper.insertRelation 向 tb_blog_like 插入（blog_id, user_id, create_time = 当前时间），
     * 相同（blog_id, user_id）命中唯一键时捕获 DuplicateKeyException 并忽略（重复点赞不会反转状态，也不会重复加数）；
     * 仅当插入成功才执行 blogMapper.incrementLiked 给 tb_blog.liked 列加一（1 条 UPDATE），更新 0 行说明博客并发被删，
     * 抛 404（BLOG_NOT_FOUND）；最后 queryLikeState 回查数据库最终状态返回。
     */
    @Transactional
    public Result like(Long blogId) {
        Long userId = requireCurrentUserId();
        requireBlog(blogId);
        boolean inserted = false;
        try {
            inserted = blogLikeMapper.insertRelation(blogId, userId, LocalDateTime.now()) == 1;
        } catch (DuplicateKeyException ignored) {
            // 唯一键只裁决相同 (blog_id 被点赞的博客 ID, user_id 点赞用户 ID) 组合；其他数据库错误不会像 INSERT IGNORE 一样被吞掉。
        }
        if (inserted && blogMapper.incrementLiked(blogId) != 1) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return Result.ok(queryLikeState(blogId, userId));
    }

    /**
     * 取消点赞一篇博客：删除点赞关系，确实删到行时给博客点赞总数减一，返回最终点赞状态。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 unlikeBlog()，
     * 对应 HTTP 路由 DELETE /blog/{id}/like（BlogController.unlikeBlog）。
     * 实现要点：@Transactional 事务内执行——blogLikeMapper.deleteRelation 按（blog_id, user_id）删除
     * tb_blog_like 关系（1 条 DELETE；本方法不先校验博客存在，博客不存在由返回前的 queryLikeState 抛 404）；
     * 仅当删除行数大于 0 才执行 blogMapper.decrementLiked 给 tb_blog.liked 列减一，更新 0 行抛 404；
     * 重复取消删不到行，不会重复扣减，直接回查返回当前状态。
     */
    @Transactional
    public Result unlike(Long blogId) {
        Long userId = requireCurrentUserId();
        int deleted = blogLikeMapper.deleteRelation(blogId, userId);
        if (deleted > 0 && blogMapper.decrementLiked(blogId) != 1) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return Result.ok(queryLikeState(blogId, userId));
    }

    /**
     * 游标分页查询一篇博客的最近点赞用户。
     * 使用场景：唯一调用方是 {@link com.hmdp.service.impl.BlogServiceImpl} 的 queryBlogLikes()，
     * 对应 HTTP 路由 GET /blog/likes/{id}（BlogController.queryBlogLikes）；不要求登录。
     * 实现要点：游标 =（score：上一页最后一条点赞记录 create_time 转成的 UTC epoch 毫秒，
     * id：该条点赞记录的 tb_blog_like 主键），类型标记 LIKE_CURSOR = "blog-like-v2"，
     * 经 cursorCodec.decode 解码并校验类型；1 条 SQL 查 tb_blog_like（只取 id、user_id、create_time 列，
     * 条件 blog_id = 入参），排序 ORDER BY create_time DESC, id DESC，续查条件“create_time 早于游标时间，
     * 或时间相同但 id 小于游标 id”，LIMIT pageSize + 1 多查 1 条探测 hasMore；
     * 随后 hydrateUsersInOrder 按点赞顺序批量查回用户（第 2 条 SQL 查 tb_user）；
     * hasMore 时用本页最后一条记录经 encodePosition 生成 nextCursor。
     */
    public Result queryUsers(Long blogId, String cursor, Integer limit) {
        requireBlog(blogId);
        int pageSize = normalizePageSize(limit);
        CursorPayload position = cursorCodec.decode(cursor, LIKE_CURSOR);
        LambdaQueryWrapper<BlogLike> wrapper = new LambdaQueryWrapper<BlogLike>()
                .select(BlogLike::getId, BlogLike::getUserId, BlogLike::getCreateTime)
                .eq(BlogLike::getBlogId, blogId)
                .orderByDesc(BlogLike::getCreateTime, BlogLike::getId)
                .last("LIMIT " + (pageSize + 1));
        if (position != null) {
            requirePosition(position);
            LocalDateTime time = toUtcLocalDateTime(position.getScore());
            wrapper.and(query -> query.lt(BlogLike::getCreateTime, time)
                    .or(nested -> nested.eq(BlogLike::getCreateTime, time)
                            .lt(BlogLike::getId, position.getId())));
        }

        List<BlogLike> rows = blogLikeMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<BlogLike> pageRows = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : rows;
        List<Long> userIds = pageRows.stream()
                .map(BlogLike::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<UserDTO> users = hydrateUsersInOrder(userIds);
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? encodePosition(pageRows.get(pageRows.size() - 1))
                : null;
        return Result.ok(new CursorPageDTO<>(users, nextCursor, hasMore));
    }

    /**
     * 回查一篇博客对当前用户的最终点赞状态和点赞总数。
     * 使用场景：本类 like() 与 unlike() 返回前调用，把数据库最终状态直接交给前端覆盖本地值。
     * 实现要点：2 条 SQL——blogMapper.selectById 查 tb_blog（不存在抛 404 BLOG_NOT_FOUND）；
     * 再查 tb_blog_like 是否存在（blog_id = 入参且 user_id = 当前用户，LIMIT 1，存在即 liked = true）；
     * 点赞总数取 tb_blog.liked 列，null 或负数按 0 返回，包装成 {@link BlogLikeStateDTO}（点赞状态 DTO）返回。
     */
    private BlogLikeStateDTO queryLikeState(Long blogId, Long userId) {
        Blog blog = blogMapper.selectById(blogId);
        if (blog == null) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        boolean liked = blogLikeMapper.selectOne(new LambdaQueryWrapper<BlogLike>()
                .select(BlogLike::getId)
                .eq(BlogLike::getBlogId, blogId)
                .eq(BlogLike::getUserId, userId)
                .last("LIMIT 1")) != null;
        int count = blog.getLiked() == null ? 0 : Math.max(blog.getLiked(), 0);
        return new BlogLikeStateDTO(liked, count);
    }

    /**
     * 按给定顺序批量把用户 ID 装配成用户 DTO 列表。
     * 使用场景：仅被本类 queryUsers() 调用，用于在内存中恢复“按点赞时间排序”的用户展示顺序。
     * 实现要点：1 条 SQL——userService.listByIds 按本页全部 user_id 批量查 tb_user，建 ID 到实体的映射后
     * 按入参顺序输出 {@link UserDTO}（用户 DTO，只含 id、nickName、icon），映射中查不到的 ID 被跳过；
     * 入参为空直接返回空列表。
     */
    private List<UserDTO> hydrateUsersInOrder(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> byId = new HashMap<>();
        for (User user : userService.listByIds(userIds)) {
            byId.put(user.getId(), user);
        }
        return userIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setNickName(user.getNickName());
                    dto.setIcon(user.getIcon());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 校验博客 ID 非空且博客存在，返回查到的博客实体。
     * 使用场景：本类 like() 与 queryUsers() 的入口校验；unlike() 不调用本方法，
     * 博客不存在由其返回前的 queryLikeState 抛 404。
     * 实现要点：blogId 为 null 抛 400（BLOG_ID_REQUIRED）；blogMapper.selectById 查 tb_blog（1 条 SQL），
     * 不存在抛 404（BLOG_NOT_FOUND）。
     */
    private Blog requireBlog(Long blogId) {
        if (blogId == null) {
            throw BusinessException.badRequest("BLOG_ID_REQUIRED", "博客ID不能为空");
        }
        Blog blog = blogMapper.selectById(blogId);
        if (blog == null) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return blog;
    }

    /**
     * 从登录上下文取当前用户 ID，未登录则抛业务异常。
     * 使用场景：本类 like() 与 unlike() 的第一步，确保点赞关系必有归属用户；queryUsers() 不要求登录故不调用。
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
     * 使用场景：仅被本类 queryUsers() 调用。
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
     * 使用场景：仅被本类 queryUsers() 在带游标续查时调用。
     * 实现要点：纯内存校验，无 SQL——score（create_time 的 UTC epoch 毫秒）不得为 null 或负数，
     * id（tb_blog_like 主键）不得为 null 或非正数，否则抛 400（INVALID_CURSOR）。
     */
    private void requirePosition(CursorPayload payload) {
        if (payload.getScore() == null || payload.getScore() < 0
                || payload.getId() == null || payload.getId() <= 0) {
            throw BusinessException.badRequest("INVALID_CURSOR", "分页游标缺少必要位置");
        }
    }

    /**
     * 把本页最后一条点赞记录编码成下一页游标字符串。
     * 使用场景：仅被本类 queryUsers() 在 hasMore 为真时调用，结果作为响应的 nextCursor 由前端原样回传。
     * 实现要点：构造 {@link CursorPayload}（游标载荷 DTO）——type = LIKE_CURSOR（"blog-like-v2"）、
     * score = 最后一条 create_time 经 toEpochMilli 转成的 UTC epoch 毫秒、id = 该条记录的 tb_blog_like 主键，
     * 再交给 cursorCodec.encode 序列化；纯内存计算，无 SQL。
     */
    private String encodePosition(BlogLike last) {
        CursorPayload payload = new CursorPayload();
        payload.setType(LIKE_CURSOR);
        payload.setScore(toEpochMilli(last.getCreateTime()));
        payload.setId(last.getId());
        return cursorCodec.encode(payload);
    }

    /**
     * 把点赞时间转成 UTC 时区的 epoch 毫秒值，作为游标的 score。
     * 使用场景：仅被本类 encodePosition() 调用。
     * 实现要点：按 ZoneOffset.UTC 换算，保证服务器部署在不同时区也得到相同游标；
     * 时间为 null 抛 IllegalStateException。纯内存计算，无 SQL。
     */
    private long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            throw new IllegalStateException("点赞时间不能为空");
        }
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 把游标中的 epoch 毫秒值还原成 UTC 时区的 LocalDateTime，用于续查条件比较，与 toEpochMilli 互为逆操作。
     * 使用场景：仅被本类 queryUsers() 在带游标续查时调用。
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
