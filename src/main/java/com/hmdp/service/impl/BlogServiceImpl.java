package com.hmdp.service.impl;

/*
 * 现实业务背景：博客 Controller 的十一种用例需要统一入口，但发布、点赞、查询和 Feed 各自有不同规则。
 * 实际触发：每次 /blog 请求先到本门面；本类只按方法委托给对应专用服务，不直接写 SQL。
 */

import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.BlogDetailDTO;
import com.hmdp.dto.BlogLikeStateDTO;
import com.hmdp.dto.BlogPublishRequest;
import com.hmdp.dto.BlogUpdateRequest;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IBlogService;
import com.hmdp.service.blog.BlogCommandService;
import com.hmdp.service.blog.BlogLikeService;
import com.hmdp.service.blog.BlogQueryService;
import com.hmdp.service.feed.BlogFeedService;
import org.springframework.stereotype.Service;

/**
 * 博客用例门面：
 * 1. Controller 只依赖一个稳定入口，但门面本身不继承通用 CRUD，外部无法绕过业务规则。
 * 2. Command、Like、Query、Feed 各自拥有单一职责和事务边界，避免 600 行服务继续膨胀。
 * 3. 门面只委托，不在此拼接 SQL、操作图片或实现幂等，保证核心规则只有一个落点。
     * 4. 所有方法成功时都返回 {@link Result}（本项目统一响应包装类，字段包括 success/data/errorCode/errorMsg/traceId/total）；
     * 参数、登录、权限或数据不存在等业务错误由下层抛出
     * {@link BusinessException}（携带 errorCode/errorMsg 的业务异常），经 Controller 对外响应时由全局异常处理器转成
 * {@code success=false、data=null、errorCode、errorMsg、traceId} 的失败 {@code Result}。
 */
@Service
public class BlogServiceImpl implements IBlogService {

    private final BlogCommandService commandService;
    private final BlogLikeService likeService;
    private final BlogQueryService queryService;
    private final BlogFeedService feedService;

    /**
     * 构造函数：注入命令、点赞、查询、Feed 四个专用服务（由 Spring 装配本门面时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogServiceImpl(
            BlogCommandService commandService,
            BlogLikeService likeService,
            BlogQueryService queryService,
            BlogFeedService feedService
    ) {
        this.commandService = commandService;
        this.likeService = likeService;
        this.queryService = queryService;
        this.feedService = feedService;
    }

    /**
     * 使用场景：用户在首页、Feed 或博客详情页点击尚未点赞的“点赞”按钮时，前端发送
     * {@code PUT /blog/{id}/like}，{@code BlogController.likeBlog()} 随即调用本方法。
     * 点赞的完整流程：门面把博客 ID 交给 {@link BlogLikeService}（专门负责点赞、取消点赞和点赞用户查询的独立服务）；
     * 后者读取当前用户、确认博客存在，尝试向 {@code tb_blog_like} 插入唯一的“{@code blog_id + user_id}”点赞关系，
     * 只有首次插入成功才给博客点赞数（tb_blog.liked 列）加一，最后回查并返回最终状态。
     * 具体例子：用户 7 第一次点赞博客 100，关系新增且数量由 12 变 13；网络重试同一请求时唯一约束阻止重复关系，数量仍为 13。
     * 成功响应 JSON：{@code data} 是对象，不是布尔值或单个数字。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": { "liked": true, "likeCount": 13 },
     *   "total": null
     * }
     * }
     *
     * @param id 要点赞的博客 ID，来自 URL 路径；不能为空且必须对应一篇现存博客。
     *           当前用户 ID 不由调用方传入，而是从登录上下文取得，因此调用前必须登录。
     * @return 成功时 {@code success=true}，{@code data} 是 {@link BlogLikeStateDTO}（点赞状态 DTO，只有 liked 和 likeCount 两个字段）：
     *         {@code liked} 表示当前用户最终是否已点赞（本操作后通常为 {@code true}），
     *         {@code likeCount} 是博客最终点赞总数；{@code errorCode/errorMsg/traceId/total} 为空。
     * @throws BusinessException 未登录、ID 为空或博客不存在时抛出；HTTP 调用时转换成统一失败响应。
     */
    @Override
    public Result likeBlog(Long id) {
        return likeService.like(id);
    }

    /**
     * 使用场景：用户在首页、Feed 或博客详情页再次点击已点赞的按钮、想撤销点赞时，前端发送
     * {@code DELETE /blog/{id}/like}，{@code BlogController.unlikeBlog()} 随即调用本方法。
     * 取消点赞的完整流程：委托点赞服务按“{@code blog_id + user_id}”从 {@code tb_blog_like} 删除关系；只有确实删到关系时才把点赞数减一，
     * 随后回查 MySQL，返回 {@code liked=false} 和非负的最终数量。
     * 具体例子：用户 7 已点赞博客 100，取消后数量由 13 变 12；重复取消删不到关系，因此不会继续减成 11。
     * 成功响应 JSON：{@code data} 是包含最终状态的对象。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": { "liked": false, "likeCount": 12 },
     *   "total": null
     * }
     * }
     *
     * @param id 要取消点赞的博客 ID，来自 URL 路径；不能为空且必须对应一篇现存博客。
     *           当前用户 ID 从登录上下文取得，因此调用前必须登录。
     * @return 成功时 {@code success=true}，{@code data} 是 {@link BlogLikeStateDTO}：
     *         {@code liked} 是数据库中的最终点赞状态（本操作后为 {@code false}），
     *         {@code likeCount} 是取消后的博客点赞总数；{@code errorCode/errorMsg/traceId/total} 为空。
     * @throws BusinessException 未登录、ID 为空或博客不存在时抛出；重复取消不是错误，而是返回同一最终状态。
     */
    @Override
    public Result unlikeBlog(Long id) {
        return likeService.unlike(id);
    }

    /**
     * 使用场景：用户点击博客卡片进入详情页、作者进入编辑页，或前端在点赞结果不确定时重新校准状态，
     * 都会发送 {@code GET /blog/{id}}，由 {@code BlogController.queryBlogById()} 调用本方法。
     * 查询详情的完整流程：委托查询服务校验博客存在，读取博客及按顺序绑定的图片 ID，
     * 再由装配器补充作者摘要、图片、当前用户点赞状态等对外字段并返回 DTO。
     * 具体例子：请求博客 100，数据库博客行和排序后的图片 501、502 被装成详情；请求不存在的 999 返回 404。
     * 成功响应 JSON：{@code data} 是博客详情对象；其中 {@code imageIds} 是数组，
     * 其他示例字段都是字符串、数字、布尔值或时间字符串。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "id": 100,
     *     "shopId": 5,
     *     "userId": 7,
     *     "icon": "/imgs/icons/7.jpg",
     *     "name": "小明",
     *     "isLike": true,
     *     "title": "这家店值得去",
     *     "images": "/imgs/blogs/a.jpg,/imgs/blogs/b.jpg",
     *     "imageIds": [501, 502],
     *     "content": "正文内容",
     *     "liked": 13,
     *     "comments": 8,
     *     "createTime": "2026-08-25T10:00:00",
     *     "updateTime": "2026-08-25T10:30:00"
     *   },
     *   "total": null
     * }
     * }
     * {@code comments} 的关键边界：这里的 {@code comments: 8} 是数字，
     * 只表示这篇博客共有 8 条评论（一级评论和回复都计数），不是评论对象或评论数组。
     * 详情响应不内嵌评论正文、评论作者、回复或评论点赞信息，避免一篇热门博客把详情响应无限撑大。
     * 页面会另外请求 {@code GET /blog-comments?blogId=100}。该接口的 {@code data} 才是下面这种复合对象：
     * {@code
     * {
     *   "list": [
     *     {
     *       "id": 30,
     *       "blogId": 100,
     *       "userId": 9,
     *       "parentId": 0,
     *       "answerId": 0,
     *       "content": "环境很好",
     *       "liked": 3,
     *       "createTime": "2026-08-25T11:00:00",
     *       "author": { "id": 9, "nickName": "小红", "icon": "/imgs/icons/9.jpg" },
     *       "answerUser": null,
     *       "replies": []
     *     }
     *   ],
     *   "nextCursor": null,
     *   "hasMore": false
     * }
     * }
     * 评论对象里的 {@code liked: 3} 仍是数字，只表示这条评论累计 3 个赞。
     * 当前评论 DTO 没有 {@code isLike}，项目也没有评论点赞/取消点赞接口，所以页面目前不能判断或切换
     * “当前用户是否赞过这条评论”；若要支持该功能，需要另行设计评论点赞关系和写接口。
     *
     * @param id 要查看的博客 ID，来自 URL 路径；不能为空且必须对应一篇现存博客。
     *           本方法不强制登录；未登录时返回的 {@code isLike=false}。
     * @return 成功时 {@code success=true}，{@code data} 是 {@link BlogDetailDTO}（博客详情 DTO），包含：
     *         博客 {@code id、shopId、title、content、images、imageIds、liked、comments、createTime、updateTime}，
     *         作者 {@code userId、name、icon}，以及当前用户点赞状态 {@code isLike}；
     *         {@code errorCode/errorMsg/traceId/total} 为空。
     * @throws BusinessException ID 为空或博客不存在时抛出；HTTP 调用时分别转换为 400 或 404 失败响应。
     */
    @Override
    public Result queryBlogById(Long id) {
        return queryService.detail(id);
    }

    /**
     * 使用场景：博客详情页首次展示“最近点赞”用户，或用户点击“加载更多”时，前端发送
     * {@code GET /blog/likes/{id}}；{@code BlogController.queryBlogLikes()} 用本方法读取首批或下一批点赞用户。
     * 查询点赞用户的完整流程：委托点赞服务校验博客和分页参数，按点赞时间、关系 ID 倒序做游标分页，
     * 多查一条判断是否还有下一页，再批量查询用户并恢复点赞顺序，返回 DTO 列表与下一页游标。
     * 具体例子：博客 100 最近由用户 9、7、3 点赞，limit=2 时返回 9、7 和游标；下一次用游标继续得到用户 3。
     * 成功响应 JSON：{@code data} 是分页对象，{@code data.list} 才是用户对象数组。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "list": [
     *       { "id": 9, "nickName": "小红", "icon": "/imgs/icons/9.jpg" },
     *       { "id": 7, "nickName": "小明", "icon": "/imgs/icons/7.jpg" }
     *     ],
     *     "nextCursor": "eyJ0eXBlIjoiYmxvZy1saWtlLXYxIn0",
     *     "hasMore": true
     *   },
     *   "total": null
     * }
     * }
     *
     * @param id 要查询点赞用户的博客 ID；不能为空且必须对应一篇现存博客。
     * @param cursor 分页游标；首次查询传 {@code null}，后续查询原样传回上一页的
     *               {@code data.nextCursor}，调用方不能解析、拼接或跨接口复用它。
     * @param limit 本次最多返回多少位用户，有效范围为 1～50；Controller 默认传 10，直接传 {@code null} 时也按 10 处理。
     * @return 成功时 {@code success=true}，{@code data} 是 {@code CursorPageDTO<UserDTO>}：
     *         {@code list} 按最近点赞顺序保存用户，每位用户包含 {@code id、nickName、icon}；
     *         {@code nextCursor} 是下一页游标，无下一页时为 {@code null}；{@code hasMore} 表示是否还有数据。
     *         外层 {@code Result.total} 不用于游标分页，因此为空，{@code errorCode/errorMsg/traceId} 也为空。
     * @throws BusinessException 博客无效、limit 越界或 cursor 无效时抛出；本查询不要求登录。
     */
    @Override
    public Result queryBlogLikes(Long id, String cursor, Integer limit) {
        return likeService.queryUsers(id, cursor, limit);
    }

    /**
     * 使用场景：登录用户进入 Feed 页、切换“关注/为你推荐”、点击刷新或下拉加载更多时，前端发送
     * {@code GET /blog/feed}，{@code BlogController.queryBlogFeed()} 根据 mode、refresh 和 cursor 调用本方法。
     * 查询 Feed 的完整流程：委托 Feed 服务读取当前用户和 following/for_you 模式；刷新时废弃当前快照，
     * 普通翻页优先按游标读取缓存快照，快照失效则按边界重新召回；随后排序、作者去重、记录曝光并返回卡片和新游标。
     * 具体例子：用户 7 首次请求 {@code mode=following} 得到前 50 篇及快照游标；继续下拉按同一快照取后 50 篇，
     * 传 {@code refresh=true} 则重新召回最新关注内容并从第一页开始。
     * 成功响应 JSON：{@code data} 是分页对象，{@code data.list} 是博客卡片对象数组；
     * 卡片里的 {@code comments} 同样只是评论总数，不是评论数组。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "list": [
     *       {
     *         "id": 100,
     *         "shopId": 5,
     *         "userId": 9,
     *         "icon": "/imgs/icons/9.jpg",
     *         "name": "小红",
     *         "isLike": false,
     *         "title": "这家店值得去",
     *         "images": "/imgs/blogs/a.jpg,/imgs/blogs/b.jpg",
     *         "liked": 13,
     *         "comments": 8,
     *         "createTime": "2026-08-25T10:00:00"
     *       }
     *     ],
     *     "nextCursor": "eyJ0eXBlIjoiZmVlZC1mb2xsb3dpbmctdjIifQ",
     *     "hasMore": true
     *   },
     *   "total": null
     * }
     * }
     *
     * @param cursor Feed 分页游标；首次进入或主动刷新时传 {@code null}，加载更多时原样回传上一页的
     *               {@code data.nextCursor}。它绑定用户和 Feed 模式，不能修改或用于另一个模式。
     * @param mode 产品模式：{@code following} 表示关注流，{@code for_you} 表示个性化推荐；
     *             传 {@code null} 时按 {@code following} 处理，其他值会被拒绝。
     * @param refresh 是否强制刷新；{@code true} 会废弃当前快照并从第一页重新召回，
     *                {@code false} 或 {@code null} 表示正常首查/翻页。
     * @return 成功时 {@code success=true}，{@code data} 是 {@code CursorPageDTO<BlogCardDTO>}：
     *         {@code list} 最多含 50 张 {@link BlogCardDTO}（博客卡片 DTO）卡片，每张包含
     *         {@code id、shopId、userId、name、icon、isLike、title、images、liked、comments、createTime}；
     *         {@code nextCursor} 用于加载下一批，{@code hasMore} 表示是否还有下一批。
     *         外层 {@code Result.total} 和 {@code errorCode/errorMsg/traceId} 为空。
     * @throws BusinessException 未登录、mode 不受支持或 cursor 无效时抛出。
     */
    @Override
    public Result queryBlogFeed(String cursor, String mode, Boolean refresh) {
        return feedService.query(cursor, mode, refresh);
    }

    /**
     * 使用场景：用户打开首页“热门笔记”区域，或在该区域点击“加载更多”时，前端发送
     * {@code GET /blog/hot}，{@code BlogController.queryHotBlog()} 调用本方法取得当前热度榜。
     * 查询热榜的完整流程：委托查询服务按“点赞数倒序、博客 ID 倒序”读取 limit+1 条，
     * 装配作者与当前用户状态，用末条博客的点赞数和 ID 生成下一页游标。
     * 具体例子：点赞数为 80、60、40 的博客在 limit=2 时先返回前两篇，下次从“60 分 + 对应 ID”之后继续。
     * 成功响应 JSON：{@code data} 是分页对象，{@code data.list} 是博客卡片对象数组；
     * {@code comments} 是数字评论总数。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "list": [
     *       {
     *         "id": 100,
     *         "shopId": 5,
     *         "userId": 9,
     *         "icon": "/imgs/icons/9.jpg",
     *         "name": "小红",
     *         "isLike": false,
     *         "title": "这家店值得去",
     *         "images": "/imgs/blogs/a.jpg,/imgs/blogs/b.jpg",
     *         "liked": 80,
     *         "comments": 8,
     *         "createTime": "2026-08-25T10:00:00"
     *       }
     *     ],
     *     "nextCursor": "eyJ0eXBlIjoiYmxvZy1ob3QtdjEifQ",
     *     "hasMore": true
     *   },
     *   "total": null
     * }
     * }
     *
     * @param cursor 热榜分页游标；首次查询传 {@code null}，加载更多时原样传回上一页的
     *               {@code data.nextCursor}，不能把其他列表的游标传到这里。
     * @param limit 本次最多返回多少篇博客，有效范围为 1～50；Controller 默认传 10，直接传 {@code null} 时也按 10 处理。
     * @return 成功时 {@code success=true}，{@code data} 是 {@code CursorPageDTO<BlogCardDTO>}：
     *         {@code list} 按点赞数、博客 ID 倒序保存卡片，每张卡片包含
     *         {@code id、shopId、userId、name、icon、isLike、title、images、liked、comments、createTime}；
     *         {@code nextCursor} 是下一页位置，{@code hasMore} 表示是否还有下一页。
     *         外层 {@code Result.total} 和 {@code errorCode/errorMsg/traceId} 为空。
     * @throws BusinessException limit 越界或 cursor 无效时抛出；本查询不强制登录，未登录时卡片的 {@code isLike=false}。
     */
    @Override
    public Result queryHotBlog(String cursor, Integer limit) {
        return queryService.hot(cursor, limit);
    }

    /**
     * 使用场景：需要展示“仅属于当前登录用户”的博客列表或内容管理列表时，客户端发送
     * {@code GET /blog/of/me}，{@code BlogController.queryMyBlog()} 调用本方法，用户 ID 无需也不能由前端传入。
     * 当前 Nuxt 的“我的笔记”页面复用了公开主页接口 {@code /blog/of/user?id=当前用户}，因此现有页面不会调用本方法；
     * 只有改为调用 {@code /blog/of/me} 的客户端或后续内容管理功能才会进入这里。
     * 查询“我的博客”的完整流程：委托查询服务从登录上下文取得作者 ID，按发布时间、博客 ID 倒序游标分页，
     * 批量装配为博客卡片后返回；请求不能伪造另一个用户 ID。
     * 具体例子：当前用户 7 发布了博客 30、20、10，limit=2 时返回 30、20 和可继续查询 10 的游标。
     * 成功响应 JSON：{@code data} 是分页对象，{@code data.list} 是当前用户的博客卡片对象数组。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "list": [
     *       {
     *         "id": 30,
     *         "shopId": 5,
     *         "userId": 7,
     *         "icon": "/imgs/icons/7.jpg",
     *         "name": "小明",
     *         "isLike": false,
     *         "title": "我的探店笔记",
     *         "images": "/imgs/blogs/mine.jpg",
     *         "liked": 6,
     *         "comments": 2,
     *         "createTime": "2026-08-25T10:00:00"
     *       }
     *     ],
     *     "nextCursor": "eyJ0eXBlIjoidXNlci1ibG9nLXYyIn0",
     *     "hasMore": true
     *   },
     *   "total": null
     * }
     * }
     *
     * @param cursor 当前用户博客列表的分页游标；首次查询传 {@code null}，加载更多时原样传回上一页的
     *               {@code data.nextCursor}。作者 ID 隐式取自登录上下文，不需要额外参数。
     * @param limit 本次最多返回多少篇博客，有效范围为 1～50；Controller 默认传 10，直接传 {@code null} 时也按 10 处理。
     * @return 成功时 {@code success=true}，{@code data} 是 {@code CursorPageDTO<BlogCardDTO>}：
     *         {@code list} 只含当前用户的博客卡片，每张包含
     *         {@code id、shopId、userId、name、icon、isLike、title、images、liked、comments、createTime}；
     *         {@code nextCursor} 和 {@code hasMore} 用于继续加载。外层 {@code Result.total} 为空，
     *         {@code errorCode/errorMsg/traceId} 也为空。
     * @throws BusinessException 未登录、limit 越界或 cursor 无效时抛出。
     */
    @Override
    public Result queryMyBlogs(String cursor, Integer limit) {
        return queryService.currentUserBlogs(cursor, limit);
    }

    /**
     * 使用场景：用户进入任意人的公开主页（当前 Nuxt 中也包括从“我的笔记”进入自己的公开主页），或在主页加载更多时，
     * 前端发送 {@code GET /blog/of/user?id={userId}}，{@code BlogController.queryBlogByUserId()} 调用本方法。
     * 查询指定作者博客的完整流程：校验 userId 后，委托查询服务按“发布时间 + 博客 ID”做游标分页，
     * 将数据库行批量装成公开卡片；这是公开主页查询，不使用当前登录用户替换目标作者。
     * 具体例子：访问用户 9 的主页并传 userId=9，只返回用户 9 发布的博客，limit=10 时最多返回 10 篇及下一页游标。
     * 成功响应 JSON：{@code data} 是分页对象，{@code data.list} 是指定作者的博客卡片对象数组。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": {
     *     "list": [
     *       {
     *         "id": 100,
     *         "shopId": 5,
     *         "userId": 9,
     *         "icon": "/imgs/icons/9.jpg",
     *         "name": "小红",
     *         "isLike": true,
     *         "title": "用户 9 的探店笔记",
     *         "images": "/imgs/blogs/user-9.jpg",
     *         "liked": 13,
     *         "comments": 8,
     *         "createTime": "2026-08-25T10:00:00"
     *       }
     *     ],
     *     "nextCursor": null,
     *     "hasMore": false
     *   },
     *   "total": null
     * }
     * }
     *
     * @param userId 要查看的作者 ID，来自查询参数 {@code id}，不能为空；它可以是当前用户，也可以是其他用户。
     *               当前实现不额外校验用户表是否存在，因此不存在的 userId 会得到空列表，而不是 404。
     * @param cursor 该作者博客列表的分页游标；首次查询传 {@code null}，加载更多时原样传回上一页的
     *               {@code data.nextCursor}。
     * @param limit 本次最多返回多少篇博客，有效范围为 1～50；Controller 默认传 10，直接传 {@code null} 时也按 10 处理。
     * @return 成功时 {@code success=true}，{@code data} 是 {@code CursorPageDTO<BlogCardDTO>}：
     *         {@code list} 只含 userId 对应作者的博客卡片，每张包含
     *         {@code id、shopId、userId、name、icon、isLike、title、images、liked、comments、createTime}；
     *         {@code nextCursor} 和 {@code hasMore} 用于继续加载。没有博客时返回空 {@code list}、
     *         {@code nextCursor=null、hasMore=false}；外层 {@code Result.total} 和错误字段为空。
     * @throws BusinessException userId 为空、limit 越界或 cursor 无效时抛出；本查询不强制登录。
     */
    @Override
    public Result queryBlogsByUserId(Long userId, String cursor, Integer limit) {
        return queryService.userBlogs(userId, cursor, limit);
    }

    /**
     * 使用场景：登录用户进入“发布笔记”页，选好店铺、填写标题和正文、上传图片后点击“发布笔记”，
     * 前端发送 {@code POST /blog}，{@code BlogController.saveBlog()} 把请求交给本方法。
     * 发布博客的完整流程：委托命令服务取得当前用户，校验标题、正文、商户、1～9 张临时图片和 clientRequestId；
     * 先用请求指纹做幂等判断，再在同一事务中新增博客、把本人临时图片绑定到新博客并记录处理结果。
     * 具体例子：用户 7 用请求号 req-01 发布博客并绑定图片 501、502，返回博客 100；双击造成相同请求再次到达时仍返回 100，不会生成第二篇。
     * 请求 JSON：{@code request} 是对象，{@code imageIds} 是图片 ID 数组。
     * {@code
     * {
     *   "clientRequestId": "req-01",
     *   "shopId": 5,
     *   "title": "这家店值得去",
     *   "content": "正文内容",
     *   "imageIds": [501, 502]
     * }
     * }
     * 成功响应 JSON：{@code data} 是数字类型的博客 ID，不是博客对象。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": 100,
     *   "total": null
     * }
     * }
     *
     * @param request 完整发布请求：{@code clientRequestId} 必填，只能是 1～64 位字母、数字、下划线或横线；
     *                {@code shopId} 必填且商户必须存在；{@code title} 去除首尾空白后为 1～255 字符；
     *                {@code content} 去除首尾空白、统一换行后为 1～2048 字符；
     *                {@code imageIds} 必须含 1～9 个不重复 ID，且图片均为当前用户已上传、尚未绑定的临时图片。
     *                作者 ID 隐式取自登录上下文，不能通过 request 指定。
     * @return 成功时 {@code success=true}，{@code data} 是新建博客的 {@link Long} 类型 ID；
     *         相同用户以同一 clientRequestId 和相同内容重试时，返回第一次创建的同一个 ID。
     *         {@code errorCode/errorMsg/traceId/total} 为空。
     * @throws BusinessException 未登录、request 为空、任一字段不合法、商户/图片无效，或同一 clientRequestId
     *                           被用于不同内容时抛出。
     */
    @Override
    public Result saveBlog(BlogPublishRequest request) {
        return commandService.publish(request);
    }

    /**
     * 使用场景：博客作者从详情页进入编辑页，修改店铺、标题、正文或图片后点击“保存修改”，
     * 前端发送 {@code PUT /blog/{id}}，{@code BlogController.updateBlog()} 调用本方法；浏览详情本身不会触发更新。
     * 编辑博客的完整流程：委托命令服务先校验新标题、正文、商户和图片列表，再锁定博客并确认当前用户是作者；
     * 保留仍使用的图片、绑定新增临时图片、把移除图片标记为待删除，更新允许编辑的字段，事务提交后才物理删文件。
     * 具体例子：博客 100 原有图片 501、502，提交列表 502、503 后保留并重排 502、绑定 503、提交后删除 501；非作者无法编辑。
     * 请求 JSON：{@code request} 是编辑后的完整对象，{@code imageIds} 不是增量，而是最终图片数组。
     * {@code
     * {
     *   "shopId": 5,
     *   "title": "修改后的标题",
     *   "content": "修改后的正文",
     *   "imageIds": [502, 503]
     * }
     * }
     * 成功响应 JSON：{@code data} 是数字类型的博客 ID，不返回修改后的完整博客对象。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": 100,
     *   "total": null
     * }
     * }
     *
     * @param id 要编辑的博客 ID，来自 URL 路径；不能为空、必须存在且作者必须是当前登录用户。
     * @param request 编辑后的完整状态：{@code shopId} 必填且商户必须存在；{@code title} 为 1～255 字符；
     *                {@code content} 为 1～2048 字符；{@code imageIds} 是编辑完成后要保留的完整图片顺序，
     *                必须含 1～9 个不重复 ID，只能引用该博客已有图片或当前用户新上传的临时图片。
     *                未出现在 imageIds 中的旧图片会被移除，不是“保持不变”。
     * @return 成功时 {@code success=true}，{@code data} 是编辑完成的博客 {@link Long} 类型 ID，值与参数 id 相同；
     *         {@code errorCode/errorMsg/traceId/total} 为空。
     * @throws BusinessException 未登录、ID/request/字段无效、博客不存在、当前用户不是作者，或图片归属/状态不合法时抛出。
     */
    @Override
    public Result updateBlog(Long id, BlogUpdateRequest request) {
        return commandService.update(id, request);
    }

    /**
     * 使用场景：博客作者在详情页点击“删除”并确认后，前端发送 {@code DELETE /blog/{id}}，
     * {@code BlogController.deleteBlog()} 调用本方法；普通访客看博客或退出页面都不会触发删除。
     * 删除博客的完整流程：委托命令服务锁定博客并校验作者，把全部图片标成待删除，删除点赞关系、评论和博客行；
     * MySQL 事务成功提交后再删物理文件，失败则保留数据库和文件，幂等请求记录仍保留以阻止旧发布请求复活博客。
     * 具体例子：用户 7 删除自己的博客 100 后，其评论和点赞关系一起清理；如果文件删除暂时失败，DELETING 记录由后台任务重试。
     * 成功响应 JSON：删除操作没有需要返回的复合对象，所以 {@code data} 明确为 {@code null}。
     * {@code
     * {
     *   "success": true,
     *   "errorMsg": null,
     *   "errorCode": null,
     *   "traceId": null,
     *   "data": null,
     *   "total": null
     * }
     * }
     *
     * @param id 要删除的博客 ID，来自 URL 路径；不能为空、必须存在且作者必须是当前登录用户。
     * @return 成功时返回一个不携带业务数据的 {@link Result}：{@code success=true、data=null、total=null}，
     *         {@code errorCode/errorMsg/traceId} 为空。没有博客 ID 返回值，因为被删除资源不再可查询。
     * @throws BusinessException 未登录、ID 为空、博客不存在或当前用户不是作者时抛出；HTTP 调用时转换为统一失败响应。
     */
    @Override
    public Result deleteBlog(Long id) {
        return commandService.delete(id);
    }
}
