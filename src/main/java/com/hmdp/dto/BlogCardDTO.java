package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 博客列表中的卡片响应。
 *
 * 类别：服务端返回给前端的响应 DTO，由 {@code BlogAssembler} 批量组装。
 * 用途：热门博客、用户主页、Feed 和搜索列表只返回首屏展示必需的数据。
 * 边界：故意不包含完整正文、更新时间、发布幂等信息等字段，避免列表接口传输大正文，
 * 也避免把数据库实体的内部字段直接暴露给前端。
 */
@Data
@Accessors(chain = true)
public class BlogCardDTO implements SearchResultItemDTO {

    /** 博客 ID，前端可用它进入详情页。 */
    private Long id;

    /** 关联商户 ID；普通动态未关联商户时可以为空。 */
    private Long shopId;

    /** 博客作者的用户 ID。 */
    private Long userId;

    /** 作者头像地址，由服务端根据 {@link #userId} 补充。 */
    private String icon;

    /** 作者昵称，由服务端根据 {@link #userId} 补充。 */
    private String name;

    /** 当前登录用户是否点赞了这篇博客；未登录时为 {@code false}。 */
    private Boolean isLike;

    /** 博客标题。 */
    private String title;

    /** 博客图片地址集合，沿用项目的逗号分隔字符串格式。 */
    private String images;

    /** 博客当前点赞总数，是全局计数，不是当前用户的点赞次数。 */
    private Integer liked;

    /** 博客当前评论总数。 */
    private Integer comments;

    /** 博客发布时间；项目统一按 UTC 时间语义读写。 */
    private LocalDateTime createTime;
}
