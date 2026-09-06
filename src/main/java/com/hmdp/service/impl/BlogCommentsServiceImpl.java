package com.hmdp.service.impl;

/*
 * 现实业务背景：博客详情页需要完成评论发布、回复、游标翻页和作者删除的基础闭环。
 * 设计边界：MySQL 保存评论真相；本类负责评论树归属、对象权限、事务计数和批量装配，不把 Entity 直接暴露给前端。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.BlogCommentCreateRequest;
import com.hmdp.dto.BlogCommentDTO;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.CursorPayload;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IUserService;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 
 *  服务实现类
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    private static final String COMMENT_CURSOR = "blog-comment-v1";
    private static final int MAX_CONTENT_LENGTH = 255;

    private final BlogCommentsMapper commentsMapper;
    private final BlogMapper blogMapper;
    private final IUserService userService;
    private final CursorCodec cursorCodec;

    /**
     * 构造函数：注入评论 Mapper、博客 Mapper、用户服务与游标编解码器（由 Spring 装配本 Service 时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogCommentsServiceImpl(
            BlogCommentsMapper commentsMapper,
            BlogMapper blogMapper,
            IUserService userService,
            CursorCodec cursorCodec
    ) {
        this.commentsMapper = commentsMapper;
        this.blogMapper = blogMapper;
        this.userService = userService;
        this.cursorCodec = cursorCodec;
    }

    /**
     * 创建一级评论或回复的完整流程：
     * 使用场景：登录用户在博客详情页发布一级评论或回复时，前端发送 POST /blog-comments，
     * 由 BlogCommentsController.createComment() 调用；项目内没有其他调用方。
     * 1. 从 {@link UserHolder} 取得当前登录用户 ID；请求只能提供博客、正文和回复关系，不能指定作者。
     * 2. 校验博客存在，正文去掉首尾空白并限制为 1～255 个字符。
     * 3. {@code parentId=0、answerId=0} 表示一级评论；回复时两个 ID 必须同时提供（{@code answerId=parentId}
     *    表示直接回复这条一级评论本身），并校验一级评论、被回复评论都存在、可见、属于本博客且位于同一条评论串
     *    （即被回复评论的 {@code parentId} 等于该一级评论 ID）。
     * 4. 组装评论记录，作者强制写成当前用户，然后插入 {@code tb_blog_comments}。
     * 5. 将 {@code tb_blog.comments} 加一并返回新评论 ID；两次写库处于同一事务，任一步失败都会一起回滚。
     *
     * 具体例子：登录用户 7 在博客 100 下发布“环境很好”，请求为
     * {@code {blogId:100, content:"环境很好"}}，最终保存 {@code userId=7,parentId=0,answerId=0}。
     * 如果回复博客 100 的一级评论 20 下的回复 25，则传 {@code parentId=20,answerId=25}；
     * 即使评论 25 真实存在，只要它属于博客 101 或另一条评论串，也会被拒绝，不能把两棵评论树串起来。
     */
    @Override
    @Transactional
    public Result createComment(BlogCommentCreateRequest request) {
        Long userId = requireCurrentUserId();
        if (request == null || request.getBlogId() == null) {
            throw BusinessException.badRequest("BLOG_ID_REQUIRED", "博客ID不能为空");
        }
        requireBlog(request.getBlogId());
        String content = normalizeContent(request.getContent());
        long parentId = normalizeRelationId(request.getParentId());
        long answerId = normalizeRelationId(request.getAnswerId());
        validateReplyTargets(request.getBlogId(), parentId, answerId);

        BlogComments comment = new BlogComments()
                .setUserId(userId)
                .setBlogId(request.getBlogId())
                .setParentId(parentId)
                .setAnswerId(answerId)
                .setContent(content)
                .setLiked(0)
                .setStatus(0);
        if (commentsMapper.insert(comment) != 1 || comment.getId() == null) {
            throw new IllegalStateException("保存评论失败");
        }
        if (blogMapper.incrementComments(request.getBlogId()) != 1) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return Result.ok(comment.getId());
    }

    /**
     * 查询一页评论树的完整流程：
     * 使用场景：用户打开博客详情页查看评论或下拉加载更多时，前端发送 GET /blog-comments?blogId=&cursor=&limit=，
     * 由 BlogCommentsController.queryComments() 调用；项目内没有其他调用方。
     * 1. 校验博客存在、limit 在 1～50 之间（未传默认 20），并把不透明游标（由 {@code CursorCodec} 按类型
     *    {@code blog-comment-v1} 编码的字符串）还原为“上一页末条一级评论的创建时间 + ID”。
     * 2. 只按创建时间和 ID 倒序查询一级评论（{@code parent_id=0}），多查一条用来判断 {@code hasMore}；
     *    有游标时只取“时间更早，或时间相同但 ID 更小”的数据。
     * 3. 一次查询本页所有一级评论的回复（{@code parent_id IN (本页一级评论 ID)}），再批量查询评论作者和被回复用户；
     *    加上第 2 步，主流程共 4 条 SELECT，避免每条评论各发一条 SQL。
     * 4. 将回复按 {@code parentId} 分组挂回一级评论，转换为 DTO，并用本页最后一条一级评论生成下一页游标。
     *
     * 具体例子：博客 100 有一级评论 30、20、10，{@code limit=2} 时先返回 30、20 及它们的全部回复，
     * 同时返回以评论 20 为位置的 {@code nextCursor}；下次携带该游标只查询 20 之后的一级评论 10，
     * 不会因为期间新插入了评论 40 而把第一页数据整体向后推。
     */
    @Override
    public Result queryComments(Long blogId, String cursor, Integer limit) {
        requireBlog(blogId);
        int pageSize = normalizePageSize(limit);
        CursorPayload position = cursorCodec.decode(cursor, COMMENT_CURSOR);
        LambdaQueryWrapper<BlogComments> wrapper = new LambdaQueryWrapper<BlogComments>()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getStatus, 0)
                .eq(BlogComments::getParentId, 0L)
                .orderByDesc(BlogComments::getCreateTime, BlogComments::getId)
                .last("LIMIT " + (pageSize + 1));
        if (position != null) {
            requirePosition(position);
            LocalDateTime time = toUtcLocalDateTime(position.getScore());
            wrapper.and(query -> query.lt(BlogComments::getCreateTime, time)
                    .or(nested -> nested.eq(BlogComments::getCreateTime, time)
                            .lt(BlogComments::getId, position.getId())));
        }

        List<BlogComments> rows = commentsMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<BlogComments> pageRows = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : rows;
        if (pageRows.isEmpty()) {
            return Result.ok(CursorPageDTO.empty());
        }

        List<Long> parentIds = pageRows.stream().map(BlogComments::getId).collect(Collectors.toList());
        List<BlogComments> replies = commentsMapper.selectList(new LambdaQueryWrapper<BlogComments>()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getStatus, 0)
                .in(BlogComments::getParentId, parentIds)
                .orderByAsc(BlogComments::getCreateTime, BlogComments::getId));
        List<BlogComments> all = new ArrayList<>(pageRows.size() + replies.size());
        all.addAll(pageRows);
        all.addAll(replies);
        Map<Long, UserDTO> users = loadUsers(all);
        Map<Long, Long> answerUserIds = loadAnswerUserIds(all);

        Map<Long, List<BlogCommentDTO>> repliesByParent = new LinkedHashMap<>();
        for (BlogComments reply : replies) {
            repliesByParent.computeIfAbsent(reply.getParentId(), ignored -> new ArrayList<>())
                    .add(toDTO(reply, users, answerUserIds));
        }
        List<BlogCommentDTO> result = pageRows.stream()
                .map(comment -> toDTO(comment, users, answerUserIds)
                        .setReplies(repliesByParent.getOrDefault(comment.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        String nextCursor = hasMore ? encodePosition(pageRows.get(pageRows.size() - 1)) : null;
        return Result.ok(new CursorPageDTO<>(result, nextCursor, hasMore));
    }

    /**
     * 删除评论的完整流程：
     * 使用场景：评论作者在博客详情页删除自己的评论时，前端发送 DELETE /blog-comments/{id}，
     * 由 BlogCommentsController.deleteComment() 调用；项目内没有其他调用方。
     * 1. 从登录上下文取得当前用户，按 ID 查询可见评论；不存在直接返回“评论不存在”。
     * 2. 比较评论的 {@code userId} 与当前用户 ID，只有作者本人可以删除。
     * 3. 删除回复时只删一行（条件 {@code id=回复ID AND user_id=当前用户}）；删除一级评论时先按
     *    {@code parent_id=一级评论ID} 删它下面的全部回复，再按 {@code id=一级评论ID AND user_id=当前用户} 删一级评论本身。
     * 4. 按数据库实际删除行数扣减博客评论总数；删除与计数更新处于同一事务，失败时一起回滚。
     *
     * 具体例子：一级评论 20 有回复 21、22，作者删除 20 时实际删除 3 行，所以博客评论数减 3；
     * 若回复 21 的作者只删除 21，则评论数只减 1。用户 9 尝试删除用户 7 的评论会收到 403。
     */
    @Override
    @Transactional
    public Result deleteComment(Long commentId) {
        Long userId = requireCurrentUserId();
        if (commentId == null) {
            throw BusinessException.badRequest("COMMENT_ID_REQUIRED", "评论ID不能为空");
        }
        BlogComments existing = commentsMapper.selectById(commentId);
        if (existing == null || !Integer.valueOf(0).equals(existing.getStatus())) {
            throw BusinessException.notFound("COMMENT_NOT_FOUND", "评论不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw BusinessException.forbidden("只能删除自己的评论");
        }

        int deleted = 0;
        if (Long.valueOf(0L).equals(existing.getParentId())) {
            deleted += commentsMapper.delete(new LambdaQueryWrapper<BlogComments>()
                    .eq(BlogComments::getParentId, existing.getId()));
        }
        deleted += commentsMapper.delete(new LambdaQueryWrapper<BlogComments>()
                .eq(BlogComments::getId, existing.getId())
                .eq(BlogComments::getUserId, userId));
        if (deleted < 1) {
            throw BusinessException.notFound("COMMENT_NOT_FOUND", "评论不存在");
        }
        if (blogMapper.decrementComments(existing.getBlogId(), deleted) != 1) {
            throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        }
        return Result.ok();
    }

    /**
     * 校验回复关系合法：parentId 与 answerId 必须同时为 0（一级评论）或同时非 0（回复）；
     * 一级评论必须存在、可见（属于本博客且 status=0）且 parent_id=0；被回复评论必须存在、可见，
     * 且与该一级评论同属一条评论串（answerId 等于 parentId，或被回复评论的 parentId 等于该一级评论 ID）。
     * 使用场景：仅被本类 createComment 在插入评论前调用。
     * 实现要点：最多 2 条 SELECT（selectById 查一级评论；answerId 不等于 parentId 时再查被回复评论），无写操作。
     */
    private void validateReplyTargets(Long blogId, long parentId, long answerId) {
        if (parentId == 0L && answerId == 0L) {
            return;
        }
        if (parentId == 0L || answerId == 0L) {
            throw BusinessException.badRequest("INVALID_COMMENT_REPLY", "回复必须同时指定一级评论和被回复评论");
        }
        BlogComments parent = commentsMapper.selectById(parentId);
        BlogComments answer = parentId == answerId ? parent : commentsMapper.selectById(answerId);
        if (!isVisibleComment(parent, blogId) || !Long.valueOf(0L).equals(parent.getParentId())) {
            throw BusinessException.badRequest("INVALID_COMMENT_PARENT", "一级评论不存在");
        }
        if (!isVisibleComment(answer, blogId)
                || !(answerId == parentId || Long.valueOf(parentId).equals(answer.getParentId()))) {
            throw BusinessException.badRequest("INVALID_COMMENT_ANSWER", "被回复评论不属于当前评论串");
        }
    }

    /**
     * 判断评论是否可见：非空、属于指定博客且 status=0。
     * 使用场景：仅被本类 validateReplyTargets 调用，用于回复目标的可见性检查。
     * 实现要点：纯内存判断，不发 SQL。
     */
    private boolean isVisibleComment(BlogComments comment, Long blogId) {
        return comment != null
                && blogId.equals(comment.getBlogId())
                && Integer.valueOf(0).equals(comment.getStatus());
    }

    /**
     * 批量加载评论作者的用户摘要（id、昵称、头像）。
     * 使用场景：仅被本类 queryComments 调用，为本页一级评论和回复装配 author 字段。
     * 实现要点：收集评论的 userId 去重后用 userService.listByIds（IService 通用方法）一次查 tb_user，
     * 转换为 UserDTO 后按用户 ID 建映射；无评论时不发 SQL。
     */
    private Map<Long, UserDTO> loadUsers(List<BlogComments> comments) {
        Set<Long> ids = comments.stream()
                .map(BlogComments::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserDTO> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (User user : userService.listByIds(ids)) {
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setNickName(user.getNickName());
            dto.setIcon(user.getIcon());
            result.put(user.getId(), dto);
        }
        return result;
    }

    /**
     * 批量查询被回复评论的作者 ID，供回复卡片展示“回复 @某人”。
     * 使用场景：仅被本类 queryComments 调用。
     * 实现要点：收集 answerId 大于 0 的去重集合，用 selectBatchIds 一次查 tb_blog_comments，
     * 返回评论 ID 到 userId 的映射；没有回复时返回空 Map 且不发 SQL。
     */
    private Map<Long, Long> loadAnswerUserIds(List<BlogComments> comments) {
        Set<Long> answerIds = comments.stream()
                .map(BlogComments::getAnswerId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (answerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return commentsMapper.selectBatchIds(answerIds).stream()
                .collect(Collectors.toMap(BlogComments::getId, BlogComments::getUserId, (left, right) -> left));
    }

    /**
     * 把一条评论记录转换为 {@link BlogCommentDTO}（评论对外 DTO）。
     * 使用场景：仅被本类 queryComments 调用，分别转换一级评论和回复。
     * 实现要点：liked 为空或负数时兜底为 0；作者与被回复用户摘要从预加载的 Map 中取，查不到则为 null；纯内存转换，不发 SQL。
     */
    private BlogCommentDTO toDTO(
            BlogComments comment,
            Map<Long, UserDTO> users,
            Map<Long, Long> answerUserIds
    ) {
        Long answerUserId = answerUserIds.get(comment.getAnswerId());
        return new BlogCommentDTO()
                .setId(comment.getId())
                .setBlogId(comment.getBlogId())
                .setUserId(comment.getUserId())
                .setParentId(comment.getParentId())
                .setAnswerId(comment.getAnswerId())
                .setContent(comment.getContent())
                .setLiked(comment.getLiked() == null ? 0 : Math.max(comment.getLiked(), 0))
                .setCreateTime(comment.getCreateTime())
                .setAuthor(users.get(comment.getUserId()))
                .setAnswerUser(users.get(answerUserId));
    }

    /**
     * 规范化评论正文：去除首尾空白，要求非空且不超过 255 个字符（常量 MAX_CONTENT_LENGTH）。
     * 使用场景：仅被本类 createComment 在保存前调用。
     * 实现要点：纯内存校验，违规抛业务异常（正文为空或超长），不写库。
     */
    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw BusinessException.badRequest("COMMENT_CONTENT_REQUIRED", "评论内容不能为空");
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw BusinessException.badRequest("COMMENT_CONTENT_TOO_LONG", "评论不能超过 255 个字符");
        }
        return normalized;
    }

    /**
     * 把可空的评论关系 ID 归一化为 long：null 视为 0（0 表示“未指定”）。
     * 使用场景：仅被本类 createComment 调用，处理 parentId 与 answerId。
     * 实现要点：纯内存转换。
     */
    private long normalizeRelationId(Long id) {
        return id == null ? 0L : id;
    }

    /**
     * 规范化评论分页大小：未传默认 20，小于 1 或大于 50 抛业务异常。
     * 使用场景：仅被本类 queryComments 调用。
     * 实现要点：纯内存校验。
     */
    private int normalizePageSize(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 1 || limit > 50) {
            throw BusinessException.badRequest("INVALID_PAGE_SIZE", "limit 必须在 1 到 50 之间");
        }
        return limit;
    }

    /**
     * 校验解码后的评论游标：score（上一页末条的 UTC 毫秒时间戳）非空且不小于 0，id 非空且大于 0。
     * 使用场景：仅被本类 queryComments 携带游标查询时调用。
     * 实现要点：纯内存校验，不合法抛 INVALID_CURSOR 业务异常。
     */
    private void requirePosition(CursorPayload payload) {
        if (payload.getScore() == null || payload.getScore() < 0
                || payload.getId() == null || payload.getId() <= 0) {
            throw BusinessException.badRequest("INVALID_CURSOR", "评论分页游标缺少必要位置");
        }
    }

    /**
     * 把本页最后一条一级评论编码为下一页游标：取其创建时间（转 UTC epoch 毫秒）和 ID，
     * 组装 type 为 blog-comment-v1 的 {@link CursorPayload}（游标负载对象）后交给 {@link CursorCodec} 编码成不透明字符串。
     * 使用场景：仅被本类 queryComments 在还有下一页（hasMore）时调用。
     * 实现要点：创建时间为空视为数据异常抛 IllegalStateException；纯内存操作。
     */
    private String encodePosition(BlogComments comment) {
        if (comment.getCreateTime() == null) {
            throw new IllegalStateException("评论时间不能为空");
        }
        CursorPayload payload = new CursorPayload();
        payload.setType(COMMENT_CURSOR);
        payload.setScore(comment.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        payload.setId(comment.getId());
        return cursorCodec.encode(payload);
    }

    /**
     * 把游标中的 UTC epoch 毫秒时间戳转换为 LocalDateTime（UTC 时区），用于与 tb_blog_comments.create_time 做 SQL 比较。
     * 使用场景：仅被本类 queryComments 解析游标位置时调用。
     * 实现要点：时间超出有效范围抛 INVALID_CURSOR 业务异常。
     */
    private LocalDateTime toUtcLocalDateTime(long epochMilli) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
        } catch (DateTimeException e) {
            throw BusinessException.badRequest("INVALID_CURSOR", "评论分页游标时间超出有效范围");
        }
    }

    /**
     * 校验博客存在并返回博客记录：ID 为空抛 400，博客不存在抛 404（“笔记不存在”）。
     * 使用场景：被本类 createComment 和 queryComments 调用，作为评论写入与评论分页前的博客存在性检查。
     * 实现要点：1 条 SELECT（blogMapper.selectById 查 tb_blog），无写操作。
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
     * 从登录上下文取当前用户 ID：未登录或缺少用户 ID 抛 401 业务异常。
     * 使用场景：被本类 createComment 和 deleteComment 调用；queryComments 不要求登录，不使用本方法。
     * 实现要点：读 {@link UserHolder}（基于 ThreadLocal 的当前登录用户上下文工具），不发 SQL。
     */
    private Long requireCurrentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user.getId();
    }
}
