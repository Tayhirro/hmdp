package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 旧版按 Redis ZSet 分值滚动分页的响应结构。
 *
 * 类别：兼容保留的旧分页 DTO；当前博客列表、Feed 和点赞榜已经使用
 * {@link CursorPageDTO}，生产代码不应再混用两套分页协议。
 * {@code lastScore + lastId} 共同表示上一页末尾位置：分值相同时通过 ID
 * 继续区分记录，减少重复或遗漏。
 */
@Data
public class ScrollResult {

    /** 当前批次数据。 */
    private List<?> list;

    /** 当前批次最后一条记录的 Redis ZSet 分值。 */
    private Double lastScore;

    /** 当前批次最后一条记录 ID，用作同分记录的续读边界。 */
    private Long lastId;

    /** 是否仍有下一批数据。 */
    private Boolean hasMore;

    /** 创建一个没有下一页的标准空结果。 */
    public ScrollResult() {
        this.list = new ArrayList<>();
        this.lastScore = null;
        this.lastId = null;
        this.hasMore = false;
    }

    /** 创建指定数据和续读位置的滚动分页结果。 */
    public ScrollResult(List<?> list, Double lastScore, Long lastId, Boolean hasMore) {
        this.list = list;
        this.lastScore = lastScore;
        this.lastId = lastId;
        this.hasMore = hasMore;
    }
}
