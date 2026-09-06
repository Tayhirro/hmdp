package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 编辑博客的请求参数。
 *
 * 类别：前端提交给 {@code PUT /blog/{id}} 的请求 DTO。
 * 用途：只允许更新白名单内的业务字段。博客 ID 来自路径，操作者来自登录上下文；
 * 作者、点赞数、评论数和图片 URL 均不能由请求体改写。
 * {@link #imageIds} 表示编辑后的完整图片集合，而不是“本次新增图片”；
 * 服务端会校验图片归属，并解绑不再使用的旧图片。
 */
@Data
public class BlogUpdateRequest {

    /** 编辑后关联的商户 ID；清除关联时可以为空。 */
    private Long shopId;

    /** 编辑后的完整标题。 */
    private String title;

    /** 编辑后的完整纯文本正文。 */
    private String content;

    /** 编辑后的完整图片资产 ID 列表，顺序就是最终展示顺序。 */
    private List<Long> imageIds;
}
