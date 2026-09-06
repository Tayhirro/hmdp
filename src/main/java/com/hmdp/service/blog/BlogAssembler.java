package com.hmdp.service.blog;

/*
 * 现实业务背景：博客详情、热榜、作者主页或 Feed 流查出数据库 Blog（tb_blog 表对应的实体）后，
 * 前端还需要作者昵称、头像和当前用户是否点赞，这些信息不在博客表里。
 * 实际触发：BlogQueryService（博客只读查询服务，本包）或 BlogFeedService（个性化 Feed 流服务，service/feed 包）
 * 准备返回页面数据时调用；本类批量补齐关联信息并转换成 Detail/Card DTO（返回给前端的字段集合）。
 */

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.BlogDetailDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把数据库查询出的博客（Blog 实体）转换成真正返回给前端的数据（DTO，只含接口允许公开的字段）。
 *     1. 一次处理整页数据，避免 N+1 查询：以一页 20 篇博客为例，调用方先用第 1 条 SQL 查出这 20 篇 Blog；
 *     本类的 loadEnrichment 再执行第 2 条 SQL，把 20 篇的作者 ID（blog.user_id）去重后一次性查回作者（tb_user 表），
 *     第 3 条 SQL 一次性查回当前用户对这 20 篇的点赞记录（tb_blog_like 表中 user_id = 当前用户且 blog_id IN 这 20 个博客 ID）。
 *     整页共 3 条 SQL；若不批量、每篇博客单独查一次作者和点赞，则要 1 + 20×2 = 41 条。
 *     2. 查询后在内存中配对：通过 ID 把作者和点赞状态放回对应博客，
 *     保持 Mapper（MyBatis-Plus 数据访问接口）返回的博客顺序，不再产生额外 SQL。
 *     3. 不把数据库对象直接返回：Entity（如 Blog，与数据库表一行记录对应）是与表结构绑定的对象；
 *     DTO 是接口允许公开的字段集合。显式转换后，即使数据库以后新增内部列，也不会自动出现在前端响应中。
 *     4. 列表和详情分开：列表使用 {@link BlogCardDTO}（博客列表卡片 DTO，只有标题、封面图、点赞数等，不含完整正文）；
 *     用户进入详情页后才通过 {@link BlogDetailDTO}（博客详情 DTO，额外包含完整正文 content）获取正文，从而减少列表响应大小。
 * 
 */
@Component
public class BlogAssembler {

    private final IUserService userService;
    private final BlogLikeMapper blogLikeMapper;

    /**
     * 构造函数：注入用户查询服务与点赞关系 Mapper（由 Spring 装配本组件时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogAssembler(IUserService userService, BlogLikeMapper blogLikeMapper) {
        this.userService = userService;
        this.blogLikeMapper = blogLikeMapper;
    }

    /**
     * 把单篇 Blog 转换成博客详情 DTO（含完整正文），供详情接口直接返回前端。
     * 使用场景：仅被 {@link BlogQueryService}（本包博客只读查询服务）的 detail() 调用，
     * 对应 HTTP 路由 GET /blog/{id}（BlogController.queryBlogById 经博客门面 BlogServiceImpl 转入）。
     * 实现要点：先经 loadEnrichment 批量补齐作者与当前用户点赞状态（最多 2 条 SQL，见该方法），
     * 再在内存中逐字段拷贝出 {@link BlogDetailDTO}（博客详情 DTO，含正文 content）；
     * 点赞数、评论数经 normalizeCount 规范（null 或负数按 0 返回）；入参 blog 为 null 时直接返回 null。
     */
    public BlogDetailDTO toDetail(Blog blog) {
        if (blog == null) {
            return null;
        }
        Enrichment enrichment = loadEnrichment(Collections.singletonList(blog));
        User author = enrichment.authorById.get(blog.getUserId());
        return new BlogDetailDTO()
                .setId(blog.getId())
                .setShopId(blog.getShopId())
                .setUserId(blog.getUserId())
                .setIcon(author == null ? null : author.getIcon())
                .setName(author == null ? null : author.getNickName())
                .setIsLike(enrichment.likedBlogIds.contains(blog.getId()))
                .setTitle(blog.getTitle())
                .setImages(blog.getImages())
                .setContent(blog.getContent())
                .setLiked(normalizeCount(blog.getLiked()))
                .setComments(normalizeCount(blog.getComments()))
                .setCreateTime(blog.getCreateTime())
                .setUpdateTime(blog.getUpdateTime());
    }

    /**
     * 把一页 Blog 批量转换成博客卡片 DTO（不含完整正文）列表，保持入参顺序。
     * 使用场景：三处调用——{@link BlogQueryService} 的热榜与作者博客游标分页（GET /blog/hot、/blog/of/me、/blog/of/user）、
     * {@link com.hmdp.service.search.impl.MySqlBlogSearchService}（MySQL 关键词搜索服务）的 search()、
     * {@link com.hmdp.service.feed.BlogFeedService}（个性化 Feed 流服务）的 query()（GET /blog/feed）。
     * 实现要点：整页共用一次 loadEnrichment（最多 2 条 SQL：按 blog.user_id 去重批量查 tb_user 作者、
     * 按 user_id = 当前用户且 blog_id IN 本页 ID 查 tb_blog_like 点赞记录），再在内存中逐张映射成
     * {@link BlogCardDTO}（博客卡片 DTO，不含正文 content）；blogs 为 null 或空时返回空列表。
     */
    public List<BlogCardDTO> toCards(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return Collections.emptyList();
        }
        Enrichment enrichment = loadEnrichment(blogs);
        return blogs.stream().map(blog -> {
            User author = enrichment.authorById.get(blog.getUserId());
            return new BlogCardDTO()
                    .setId(blog.getId())
                    .setShopId(blog.getShopId())
                    .setUserId(blog.getUserId())
                    .setIcon(author == null ? null : author.getIcon())
                    .setName(author == null ? null : author.getNickName())
                    .setIsLike(enrichment.likedBlogIds.contains(blog.getId()))
                    .setTitle(blog.getTitle())
                    .setImages(blog.getImages())
                    .setLiked(normalizeCount(blog.getLiked()))
                    .setComments(normalizeCount(blog.getComments()))
                    .setCreateTime(blog.getCreateTime());
        }).collect(Collectors.toList());
    }

    /**
     * 一次性查回一页博客所需的作者信息和当前用户的点赞记录。
     * 使用场景：本类 toDetail() 与 toCards() 在转换前调用，是避免 N+1 查询的关键批量步骤。
     * 实现要点：最多 2 条 SQL——第 1 条把各博客的 author ID（blog.user_id）去重后经 userService.listByIds
     * 批量查 tb_user 作者，建立 ID 到实体的映射；第 2 条仅当已登录（UserHolder.getUser() 非 null）时执行，
     * 查 tb_blog_like 中 user_id = 当前用户且 blog_id IN 本页博客 ID 的点赞记录，收集成博客 ID 集合；
     * 未登录时点赞集合为空（对应 isLike 一律 false）。
     */
    private Enrichment loadEnrichment(List<Blog> blogs) {
        Set<Long> authorIds = blogs.stream()
                .map(Blog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> authorById = new HashMap<>(authorIds.size());
        if (!authorIds.isEmpty()) {
            for (User author : userService.listByIds(authorIds)) {
                authorById.put(author.getId(), author);
            }
        }

        UserDTO currentUser = UserHolder.getUser();
        Set<Long> likedBlogIds = new HashSet<>();
        if (currentUser != null && currentUser.getId() != null) {
            List<Long> blogIds = blogs.stream()
                    .map(Blog::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!blogIds.isEmpty()) {
                List<BlogLike> likes = blogLikeMapper.selectList(new LambdaQueryWrapper<BlogLike>()
                        .select(BlogLike::getBlogId)
                        .eq(BlogLike::getUserId, currentUser.getId())
                        .in(BlogLike::getBlogId, blogIds));
                likedBlogIds.addAll(likes.stream()
                        .map(BlogLike::getBlogId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
            }
        }

        return new Enrichment(authorById, likedBlogIds);
    }

    /**
     * 把数据库中的计数字段规范成非负整数。
     * 使用场景：本类 toDetail() 与 toCards() 转换 liked（点赞数）、comments（评论数）时调用。
     * 实现要点：null 按 0 处理，负数取 0，其余原样返回；纯内存计算，无 SQL。
     */
    private int normalizeCount(Integer count) {
        return count == null ? 0 : Math.max(count, 0);
    }

    /**
     * 一次批量补充得到的关联数据快照，供 toDetail/toCards 在内存中配对使用，不对外暴露。
     */
    private static class Enrichment {

        /** 作者 ID（blog.user_id）到作者实体（tb_user 行）的映射；查不到的作者不在映射中，取值时按 null 处理。 */
        private final Map<Long, User> authorById;

        /** 当前用户已点赞的博客 ID 集合（tb_blog_like 中 user_id = 当前用户的 blog_id）；未登录时为空集合。 */
        private final Set<Long> likedBlogIds;

        /**
         * 构造函数：仅把 loadEnrichment 查得的两个结果原样保存（由 loadEnrichment 在查询完成后调用一次）。
         */
        private Enrichment(Map<Long, User> authorById, Set<Long> likedBlogIds) {
            this.authorById = authorById;
            this.likedBlogIds = likedBlogIds;
        }
    }
}
