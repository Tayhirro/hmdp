package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 统一搜索 `/search` 接口的聚合响应。
 *
 * 统一入口不等于统一业务模型。它保存查询理解后的标准词和各垂直域分组，
 * 以后可由编排层控制召回哪些域、每个域给多少名额以及如何排序。
 */
@Data
@Accessors(chain = true)
public class UnifiedSearchResultDTO {

    /** 查询理解后的标准关键词；原始关键词仍保留在 SearchQuery。 */
    private String normalizedKeyword;

    /** 店铺、博客、用户等垂直结果分组。 */
    private List<SearchSectionDTO> sections;
}
