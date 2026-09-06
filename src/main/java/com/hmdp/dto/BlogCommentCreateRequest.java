package com.hmdp.dto;

import lombok.Data;

/**
 * 创建博客评论或回复的请求。
 *
 * 使用场景：POST /blog-comments 的请求体，由 BlogCommentsServiceImpl.createComment 处理。
 * 服务端校验约束：blogId 必填且博客需存在；content 必填，去除首尾空白后 1～255 个字符；
 * parentId 与 answerId 要么都不传（一级评论，按 0 处理），要么同时传且必须同属本博客同一条评论串。
 */
@Data
public class BlogCommentCreateRequest {

    /** 目标博客 ID；必填，博客不存在时返回错误。 */
    private Long blogId;

    /** 评论正文；必填，去除首尾空白后 1～255 个字符。 */
    private String content;

    /** 一级评论 ID；发表一级评论时不传，服务端按 0 处理。 */
    private Long parentId;

    /** 被回复的评论 ID；发表一级评论时不传，回复时必须属于同一博客同一评论串。 */
    private Long answerId;
}
