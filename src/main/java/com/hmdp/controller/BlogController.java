package com.hmdp.controller;


import com.hmdp.dto.BlogPublishRequest;
import com.hmdp.dto.BlogUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 
 * 博客（探店笔记）前端控制器（根路径 {@code /blog}）。
 * 所有方法委托 {@link IBlogService}（博客用例门面服务）实现，完整流程、参数约束与响应示例见 BlogServiceImpl 各方法说明。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布博客（探店笔记）。
     * 使用场景：登录用户在“发布笔记”页选好店铺、填写标题和正文、上传图片后点击“发布”，前端发送 POST /blog，
     * 请求体为 {@link BlogPublishRequest}（发布请求：clientRequestId、shopId、title、content、imageIds）。
     * 数据库：校验标题 1～255 字、正文 1～2048 字、1～9 张本人未绑定的 TEMP 图片后，先按 clientRequestId 做请求指纹幂等判断，
     * 再在同一事务向 tb_blog 插入新博客，并把临时图片（tb_blog_image）绑定到该博客。
     */
    @PostMapping
    public Result saveBlog(@RequestBody BlogPublishRequest request) {
        return blogService.saveBlog(request);
    }

    /**
     * 编辑当前用户自己的博客。
     * 使用场景：博客作者从详情页进入编辑页，修改店铺、标题、正文或图片后点击“保存修改”，前端发送 PUT /blog/{id}，
     * 请求体是编辑后的完整状态（imageIds 为最终图片顺序，不是增量）。
     * 数据库：锁定 tb_blog 行并校验当前用户是作者，全量替换图片绑定（移除的图片标记 DELETING），事务提交后才删除对应物理文件。
     */
    @PutMapping("/{id}")
    public Result updateBlog(
            @PathVariable("id") Long id,
            @RequestBody BlogUpdateRequest request
    ) {

        return blogService.updateBlog(id, request);
    }

    /**
     * 删除当前用户自己的博客。
     * 使用场景：博客作者在详情页点击“删除”并确认后，前端发送 DELETE /blog/{id}。
     * 数据库：同一事务删除 tb_blog 行及其 tb_blog_like 点赞关系、tb_blog_comments 评论，并解绑全部图片；
     * 事务提交后才删除图片物理文件；非作者删除返回 403。
     */
    @DeleteMapping("/{id}")
    public Result deleteBlog(@PathVariable("id") Long id) {
        return blogService.deleteBlog(id);
    }

    /**
     * 点赞博客。
     * 使用场景：登录用户在首页、Feed 或详情页点击尚未点赞的“点赞”按钮时，前端发送 PUT /blog/{id}/like。
     * 数据库：向 tb_blog_like 插入 blog_id + user_id 唯一点赞关系，仅首次插入成功时把 tb_blog.liked 加一；
     * 网络重试被唯一约束挡住，点赞数不变，天然幂等。
     */
    @PutMapping("/{id}/like")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 取消点赞博客。
     * 使用场景：登录用户再次点击已点赞的按钮想撤销点赞时，前端发送 DELETE /blog/{id}/like。
     * 数据库：按 blog_id + user_id 从 tb_blog_like 删除点赞关系，仅确实删到时把 tb_blog.liked 减一；重复取消幂等。
     */
    @DeleteMapping("/{id}/like")
    public Result unlikeBlog(@PathVariable("id") Long id) {
        return blogService.unlikeBlog(id);
    }

    /**
     * 根据 id 查询博客详情。
     * 使用场景：用户点击博客卡片进入详情页、作者进入编辑页，或前端在点赞结果不确定时校准状态，
     * 前端发送 GET /blog/{id}；本接口不强制登录，未登录时 isLike=false。
     * 数据库：读取 tb_blog 行和排序后的图片，并装配作者摘要（tb_user）与当前用户点赞状态（tb_blog_like）；
     * 请求不存在的 id 返回 404。
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 游标分页查询博客的点赞用户榜，以 MySQL 点赞关系为权威数据源。
     * 使用场景：博客详情页首次展示“最近点赞”用户或点击“加载更多”时，前端发送 GET /blog/likes/{id}?cursor=&limit=；
     * limit 未传默认 10，有效范围 1～50；不强制登录。
     * 数据库：按 blog_id 从 tb_blog_like 以点赞时间、关系 ID 倒序取 limit+1 条判断 hasMore，
     * 再按用户 ID 批量查 tb_user 装配并恢复点赞顺序。
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        return blogService.queryBlogLikes(id, cursor, limit);
    }

    /**
     * 游标分页查询博客 Feed；following / for_you 是稳定产品模式，排序算法不作为 API 参数暴露。
     * 使用场景：登录用户进入 Feed 页、切换“关注/为你推荐”、点击刷新或下拉加载更多时，前端发送
     * GET /blog/feed?cursor=&mode=&refresh=；mode 未传默认 following，refresh 未传默认 false。
     * 数据库/Redis：优先按游标读 Redis 快照缓存（feed:cache:{用户ID}:{模式}:{算法版本}:*），
     * 快照失效则按边界基于 MySQL 关注关系与博客时间线重新召回，并维护 feed:exposure:{用户ID} 的最近曝光 ZSet。
     */
    @GetMapping("/feed")
    public Result queryBlogFeed(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "mode", defaultValue = "following") String mode,
            @RequestParam(value = "refresh", defaultValue = "false") Boolean refresh
    ) {
        return blogService.queryBlogFeed(cursor, mode, refresh);
    }

    /**
     * 游标分页查询当前登录用户自己的博客。
     * 使用场景：需要展示“仅属于当前登录用户”的博客列表时发送 GET /blog/of/me?cursor=&limit=
     * （用户 ID 从登录上下文取得，不接受前端传入）；limit 未传默认 10。
     * 注意：当前前端“我的笔记”页面实际调用的是 GET /blog/of/user?id=当前用户，本接口供后续内容管理等功能使用。
     * 数据库：按 tb_blog.user_id = 当前用户，create_time、id 倒序游标分页，批量装配博客卡片。
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        return blogService.queryMyBlogs(cursor, limit);
    }

    /**
     * 游标分页查询指定用户发布的博客。
     * 使用场景：访问任意用户的公开主页或主页加载更多时，前端发送 GET /blog/of/user?id={userId}&cursor=&limit=；
     * limit 未传默认 10，有效范围 1～50；不强制登录。
     * 数据库：按 tb_blog.user_id = 指定用户（不存在的 userId 返回空列表），create_time、id 倒序游标分页并装配作者卡片。
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam("id") Long id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        return blogService.queryBlogsByUserId(id, cursor, limit);
    }

    /**
     * 游标分页查询热门博客榜。
     * 使用场景：用户打开首页“热门笔记”区域或点击“加载更多”时，前端发送 GET /blog/hot?cursor=&limit=；
     * limit 未传默认 10，有效范围 1～50；不强制登录。
     * 数据库：按 tb_blog.liked 点赞数倒序、博客 ID 倒序取 limit+1 条判断 hasMore，并装配作者与当前用户点赞状态。
     */
    @GetMapping("/hot")
    public Result queryHotBlog(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        return blogService.queryHotBlog(cursor, limit);
    }
}
