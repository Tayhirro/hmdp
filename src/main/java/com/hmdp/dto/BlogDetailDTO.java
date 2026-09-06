package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单篇博客详情响应。
 *
 * 类别：服务端返回给前端的响应 DTO，由 {@code BlogAssembler} 组装。
 * 用途：博客详情页需要正文和完整展示信息，因此它比 {@link BlogCardDTO} 多正文和更新时间。
 * 边界：这个类明确列出详情接口允许返回的字段。即使数据库实体以后增加审核状态、
 * 内部备注等仅供服务端使用的列，也不会自动返回给前端。正文按纯文本存储，
 * 前端应以文本方式安全渲染，不能把它当作可信 HTML 直接执行。
 */
@Data
@Accessors(chain = true)
public class BlogDetailDTO {

    /** 博客 ID。 */
    private Long id;

    /** 关联商户 ID；未关联商户时可以为空。 */
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

    /** 按展示顺序返回图片资产 ID，作者编辑时原样提交希望保留的图片。 */
    private List<Long> imageIds;

    /** 博客纯文本正文；显示时保留换行，但不执行其中的 HTML。 */
    private String content;

    /** 博客当前点赞总数。 */
    private Integer liked;

    /** 博客当前评论总数。 */
    private Integer comments;

    /** 博客发布时间；项目统一按 UTC 时间语义读写。 */
    private LocalDateTime createTime;

    /** 博客最近一次修改时间；项目统一按 UTC 时间语义读写。 */
    private LocalDateTime updateTime;
}
