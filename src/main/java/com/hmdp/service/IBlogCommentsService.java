package com.hmdp.service;

/*
 * 现实业务背景：用户在博客详情页发表一级评论、回复别人、继续加载评论，或删除自己的评论。
 * 设计边界：Controller 只接收 HTTP 参数；评论树校验、权限、分页和评论数一致性统一由本服务负责。
 * 数据落点：评论真相保存在 tb_blog_comments（每条评论一行，一级评论 parentId=0、回复的 parentId 指向一级评论）；
 * 博客表 tb_blog 上冗余一个评论总数 comments 字段，随评论增删在同一事务里加减。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.BlogCommentCreateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;

/**
 * 博客评论服务。
 * 所有方法成功时返回 {@link Result}（本项目统一的 HTTP 响应包装：
 * {@code success/data/errorCode/errorMsg/traceId}，业务错误由实现抛出后经全局异常处理器转成失败 Result）。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /**
     * 发布一级评论或回复。
     * 使用场景：POST /blog-comments——登录用户在博客详情页评论框提交评论，或点击某条一级评论下的“回复”时由前端触发；
     * 内部调用方：无其他 Service，仅 BlogCommentsController 调用。
     * 1. 作者 ID 强制取自登录上下文，前端只能传博客 ID、正文和回复关系。
     * 2. parentId=0 且 answerId=0 表示一级评论；回复时两个 ID 必须同时提供，
     *    且一级评论、被回复评论都必须存在、可见、属于同一篇博客的同一条评论串。
     * 3. 插入 tb_blog_comments 后把 tb_blog.comments 加一，两步同一事务。
     */
    Result createComment(BlogCommentCreateRequest request);

    /**
     * 按时间倒序分页读取一级评论，并批量装配回复。
     * 使用场景：GET /blog-comments?blogId=&cursor=&limit=——用户打开博客详情页或下拉“加载更多评论”时由前端触发；
     * 内部调用方：无其他 Service，仅 BlogCommentsController 调用。
     * 游标 =（score：上一页最后一条一级评论的 createTime 发布时间的 UTC 毫秒值，id：该评论主键）；
     * 排序规则 ORDER BY create_time DESC, id DESC，id 用来在两条评论创建时间完全相同时分先后，
     * 因此翻页期间新插入的评论不会把已读数据整体向后推。
     * limit 有效范围 1～50，不传时默认 20。
     * 批量查询示例（一页 20 条一级评论、其下共 30 条回复）：
     * 第 1 条 SQL 查一级评论（LIMIT 21，多查 1 条判断 hasMore）；
     * 第 2 条把 20 个一级评论 ID 用 IN 一次性查回全部回复；
     * 第 3 条把 50 条评论的作者 ID 和被回复的 answerId 去重后，一次性查回作者用户；
     * 第 4 条把 answerId 去重后一次性查回被回复评论（只为拿到“回复 @某人”的用户 ID），共 4 条 SQL；
     * 若不批量，仅作者查询就会变成每条评论发一条 SQL（最多 50 条）。
     */
    Result queryComments(Long blogId, String cursor, Integer limit);

    /**
     * 删除当前用户自己的评论；只有评论作者本人可以删，他人删除返回 403。
     * 使用场景：DELETE /blog-comments/{id}——评论作者在博客详情页点击自己评论的“删除”并确认时由前端触发；
     * 内部调用方：无其他 Service，仅 BlogCommentsController 调用。
     * 删除回复只删这一行；删除一级评论时先删它下面的全部回复，再删一级评论本身，
     * 并按数据库实际删除的行数扣减 tb_blog.comments（一级评论带 2 条回复就是减 3）。
     */
    Result deleteComment(Long commentId);
}
