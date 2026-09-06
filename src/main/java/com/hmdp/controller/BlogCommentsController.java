package com.hmdp.controller;


import com.hmdp.dto.BlogCommentCreateRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 
 *  博客评论前端控制器（根路径 {@code /blog-comments}），提供评论发布、游标分页查询与作者删除；
 *  评论数据落在 tb_blog_comments，评论总数冗余在 tb_blog.comments。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    private final IBlogCommentsService blogCommentsService;

    /**
     * 构造函数：注入评论服务（由 Spring 在装配该 Controller 时调用一次）。
     */
    public BlogCommentsController(IBlogCommentsService blogCommentsService) {
        this.blogCommentsService = blogCommentsService;
    }

    /**
     * 发布一级评论或回复。
     * 使用场景：登录用户在博客详情页评论框提交评论，或点击某条评论的“回复”时，前端发送 POST /blog-comments，
     * 请求体为 {@link BlogCommentCreateRequest}（评论创建请求：blogId、content、parentId、answerId）。
     * 数据库：同一事务内校验 tb_blog 存在、正文去除首尾空白后为 1～255 字，向 tb_blog_comments 插入一行
     * （status=0、liked=0；parentId=0 且 answerId=0 表示一级评论，回复时两个 ID 必须同属本博客同一条评论串），
     * 并把 tb_blog.comments 加一。
     */
    @PostMapping
    public Result createComment(@RequestBody BlogCommentCreateRequest request) {
        return blogCommentsService.createComment(request);
    }

    /**
     * 游标分页查询博客评论。
     * 使用场景：用户打开博客详情页或下拉加载更多评论时，前端发送 GET /blog-comments?blogId=&cursor=&limit=；
     * limit 未传默认 20，有效范围 1～50。
     * 数据库：按 blogId + status=0 + parent_id=0 查一级评论（create_time、id 倒序，游标定位上一页末条），
     * 再一次查出本页全部回复并批量装配作者（tb_user）与被回复用户，主流程共 4 条 SELECT。
     */
    @GetMapping
    public Result queryComments(
            @RequestParam("blogId") Long blogId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        return blogCommentsService.queryComments(blogId, cursor, limit);
    }

    /**
     * 删除自己的评论。
     * 使用场景：评论作者在博客详情页点击自己评论的“删除”时，前端发送 DELETE /blog-comments/{id}。
     * 数据库：同一事务内先校验评论存在（status=0）且 user_id 等于当前用户；删除一级评论时先连带删除其全部回复，
     * 再按实际删除行数扣减 tb_blog.comments；非作者删除返回 403。
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }
}
