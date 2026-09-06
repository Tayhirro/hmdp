package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 发布博客的请求参数。
 *
 * 类别：前端提交给 {@code POST /blog} 的请求 DTO。
 * 用途：只接收用户能编辑的业务字段；作者 ID 从登录上下文获取，点赞数、评论数、
 * 图片 URL 等服务端字段不允许由前端伪造。
 * 图片只提交资产 ID，服务端会校验资产属于当前用户且处于可绑定状态。
 */
@Data
public class BlogPublishRequest {

    /**
     * 首次点击发布时生成，并与当时的完整请求内容一起保存。
     * 如果网络超时导致前端不知道发布是否成功，下一次必须再次发送完全相同的请求内容和这个 ID；
     * 若用户后来改稿，应先取得第一次创建的 blogId，再通过 PUT 更新这一篇博客。
     */
    private String clientRequestId;

    /** 关联商户 ID；不关联商户时可以为空。 */
    private Long shopId;

    /** 博客标题，服务端会统一去除首尾空白并校验长度。 */
    private String title;

    /** 博客纯文本正文；服务端会规范化，前端必须以安全文本方式渲染。 */
    private String content;

    /**
     * 本次发布要绑定的临时图片资产 ID，顺序就是最终展示顺序。
     * 这里不接收文件路径或 URL，防止越权引用任意文件。
     */
    private List<Long> imageIds;
}
