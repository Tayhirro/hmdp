package com.hmdp.dto;

/**
 * 统一搜索结果项的最小公共合同。
 *
 * 店铺、博客和用户仍使用各自 DTO，不强行共享名称、图片、评分等含义不同的字段。
 * 统一结果只要求每个项目提供可进入详情的业务 ID，并由所在结果分组说明其检索域。
 */
public interface SearchResultItemDTO {

    /** 返回所属业务对象的公开 ID。 */
    Long getId();
}
