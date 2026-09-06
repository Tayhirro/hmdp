package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 博客评论响应。
 *
 * 评论 Entity 不直接返回前端；作者和被回复用户只暴露公开摘要，一级评论附带当前已有回复。
 * 使用场景：BlogCommentsServiceImpl.queryComments 组装后经 GET /blog-comments?blogId=... 返回，
 * 也作为发布评论（POST /blog-comments）响应中的评论数据来源。
 */
@Data
@Accessors(chain = true)
public class BlogCommentDTO {

    /** 评论 ID。 */
    private Long id;
    /** 所属博客 ID。 */
    private Long blogId;
    /** 评论作者的用户 ID。 */
    private Long userId;
    /** 所属一级评论 ID；一级评论本身为 0。 */
    private Long parentId;
    /** 被回复的评论 ID；一级评论为 0，回复时指向被回复评论。 */
    private Long answerId;
    /** 评论正文，纯文本；服务端已去除首尾空白，最长 255 个字符。 */
    private String content;
    /** 评论点赞数；服务端把数据库空值按 0 返回并保证不为负。 */
    private Integer liked;
    /** 评论发布时间。 */
    private LocalDateTime createTime;
    /** 评论作者的公开摘要（ID、昵称、头像）。 */
    private UserDTO author;
    /** 被回复用户的公开摘要；仅回复评论时有值，一级评论为 null。 */
    private UserDTO answerUser;
    /** 该一级评论下的回复列表；一级评论才挂载，无回复时为空集合。 */
    private List<BlogCommentDTO> replies = Collections.emptyList();
}
