package com.hmdp.dto;

import com.hmdp.service.search.SearchScope;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 统一搜索中一个垂直检索域的结果分组。
 *
 * 分组返回而不是把店铺、博客、用户拍平成一个万能 DTO：各域可以保留自己的展示字段，
 * 前端也能根据 {@link #scope} 选择对应卡片。
 */
@Data
@Accessors(chain = true)
public class SearchSectionDTO {

    /** 当前分组属于店铺、博客还是用户。 */
    private SearchScope scope;

    /** 当前分组的具体结果项，运行时为对应业务的专用 DTO。 */
    private List<SearchResultItemDTO> items;

    /** 满足该垂直检索条件的结果总数。 */
    private long total;

    /** 该分组是否还能继续加载。 */
    private boolean hasMore;
}
