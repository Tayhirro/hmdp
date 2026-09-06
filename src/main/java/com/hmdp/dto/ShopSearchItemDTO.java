package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 店铺搜索结果卡片。
 *
 * 类别：搜索接口专用响应 DTO。
 * 用途：承载搜索列表展示所需的店铺摘要，点击 {@link #id} 后再调用店铺详情接口。
 * 边界：不暴露经纬度、创建时间、更新时间等数据库内部字段；搜索域不直接返回 Shop Entity。
 */
@Data
@Accessors(chain = true)
public class ShopSearchItemDTO implements SearchResultItemDTO {

    /** 店铺 ID，用于进入详情页或关联探店博客。 */
    private Long id;

    /** 店铺名称，也是当前 MySQL 版本唯一参与匹配的字段。 */
    private String name;

    /** 店铺类型 ID，供结果页展示或后续筛选。 */
    private Long typeId;

    /** 店铺图片，沿用项目的逗号分隔字符串格式。 */
    private String images;

    /** 所属商圈。 */
    private String area;

    /** 可展示的店铺地址。 */
    private String address;

    /** 人均价格，单位沿用数据库定义。 */
    private Long avgPrice;

    /** 历史销量。 */
    private Integer sold;

    /** 评论数量。 */
    private Integer comments;

    /** 店铺评分，数据库以实际评分乘 10 保存。 */
    private Integer score;

    /** 营业时间。 */
    private String openHours;
}
