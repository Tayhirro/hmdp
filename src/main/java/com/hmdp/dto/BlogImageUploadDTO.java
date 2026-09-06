package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 博客图片上传成功后的响应。
 *
 * 类别：上传接口的响应 DTO。
 * 用途：{@code POST /upload/blog} 返回图片资产 ID 和预览地址；发布或编辑博客时，
 * 前端只需提交 {@link #id}，服务端再校验图片归属和状态。
 * 边界：{@link #url} 只用于展示，不是删除凭证，也不应作为发布接口的可信入参。
 */
@Data
@AllArgsConstructor
public class BlogImageUploadDTO {

    /** 图片资产记录 ID，供后续发布、编辑或删除临时图片时使用。 */
    private Long id;

    /** 图片的公开访问地址，供前端上传完成后立即预览。 */
    private String url;
}
