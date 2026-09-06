package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 点赞或取消点赞命令执行后的服务端权威状态。
 *
 * 类别：点赞写接口的响应 DTO。
 * 用途：{@code PUT /blog/{id}/like} 和 {@code DELETE /blog/{id}/like} 执行后，
 * 把数据库中的最终状态返回给前端。
 * 前端使用方式：客户端应直接用这两个字段覆盖本地状态，不自行猜测 {@code +1/-1}；
 * 即使请求被重试，界面也能与服务端真实状态重新对齐。
 */
@Data
@AllArgsConstructor
public class BlogLikeStateDTO {

    /** 当前用户执行命令后是否已点赞该博客。 */
    private Boolean liked;

    /** 执行命令后该博客的点赞总数。 */
    private Integer likeCount;
}
