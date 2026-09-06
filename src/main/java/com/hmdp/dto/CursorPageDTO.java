package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 游标分页的统一响应结构。
 *
 * 类别：列表接口的通用响应 DTO。
 * 用途：热门博客、用户博客、点赞用户榜和 Feed 使用游标继续读取下一页，
 * 避免页码分页在数据新增或排序变化时产生大量重复、遗漏和深分页扫描。
 * 客户端不要解析或修改 {@link #nextCursor}，只需在下一次请求中原样回传。
 *
 * @param <T> 当前列表中每个元素的响应类型
 */
@Data
@AllArgsConstructor
public class CursorPageDTO<T> {

    /** 当前页数据；无数据时返回空集合，不返回 {@code null}。 */
    private List<T> list;

    /** 下一页位置的不透明游标；没有下一页时为 {@code null}。 */
    private String nextCursor;

    /** 是否仍有下一页，前端据此决定是否继续加载。 */
    private Boolean hasMore;

    /**
     * 创建标准空页，统一空列表、空游标和 {@code hasMore=false} 的表达。
     */
    public static <T> CursorPageDTO<T> empty() {
        return new CursorPageDTO<>(Collections.emptyList(), null, false);
    }
}
