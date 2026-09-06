package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 页码分页的统一响应结构。
 *
 * 类别：列表接口的通用响应 DTO。
 * 用途：把当前页数据和分页元数据一起返回，前端不再通过“再请求一次空页”猜测是否到底。
 * 边界：适合浅分页；搜索结果以后出现深分页需求时，应改用搜索引擎的 search_after，
 * 而不是无限增大 {@link #current}。
 *
 * @param <T> 当前页元素类型
 */
@Data
@AllArgsConstructor
public class PageResultDTO<T> {

    /** 当前页数据；无结果时返回空集合。 */
    private List<T> list;

    /** 当前页码，从 1 开始。 */
    private long current;

    /** 每页最大返回数量。 */
    private long pageSize;

    /** 满足查询条件的结果总数。 */
    private long total;

    /** 是否仍有下一页。 */
    private boolean hasMore;

    /** 创建标准空页，避免各 Service 重复拼装空响应。 */
    public static <T> PageResultDTO<T> empty(long current, long pageSize) {
        return new PageResultDTO<>(Collections.emptyList(), current, pageSize, 0L, false);
    }
}
