package com.hmdp.dto;

import com.hmdp.service.search.SearchScope;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 搜索框输入提示项，目前只作为接口合同保留。
 *
 * 输入提示追求低延迟和短列表，与点击“搜索”后的完整召回不是同一个接口。
 * 提示可以是一条补全 Query，也可以直达某个店铺、博客或用户。
 */
@Data
@Accessors(chain = true)
public class SearchSuggestionDTO {

    /** 下拉框展示并可回填的文字。 */
    private String text;

    /** 直达业务对象时所属检索域；普通 Query 建议可以为空。 */
    private SearchScope scope;

    /** 直达对象 ID；普通 Query 建议可以为空。 */
    private Long targetId;
}
