package com.hmdp.service.search;

/*
 * 现实业务背景：统一搜索（{@link UnifiedSearchService}，聚合店铺/笔记/用户结果的 /search 入口）
 * 把同一个 Query 分发给不同供给域时，需要一个共同扩展点。
 * 实际触发：统一搜索根据请求 scope（{@link SearchScope}，枚举出 SHOP/BLOG/USER 三个允许被搜索的域）
 * 选择若干垂直搜索执行；当前店铺、笔记和用户三个域均已接入。
 *
 * 设计精华：
 * 1. 各垂直域平级实现本接口，BlogSearchService（笔记搜索）不继承 ShopSearchService（店铺搜索）。
 * 2. 泛型 T 保留每个域自己的结果 DTO（如店铺的 ShopSearchItemDTO、博客的 BlogCardDTO），
 *    避免制造包含所有字段的万能搜索对象；T 统一实现 SearchResultItemDTO
 *    （只约定返回业务对象公开 ID 的最小合同）。
 * 3. 本接口只定义召回边界；跨域配额、融合和最终排序属于统一编排层。
 */

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.SearchResultItemDTO;

public interface VerticalSearchService<T extends SearchResultItemDTO> {

    /**
     * 返回该实现负责的唯一检索域，供统一搜索注册和路由。
     *
     * 使用场景：统一搜索编排实现
     * {@link com.hmdp.service.search.impl.DefaultUnifiedSearchService}（GET /search 的聚合实现）
     * 在构造时对容器里每个 {@link VerticalSearchService} 实现调用本方法建立域到服务的注册表
     * （同一域出现两个实现会在启动时直接失败）；请求到达后按请求 scope 从注册表取对应服务。
     *
     * 实现要点：各垂直域通过接口 default 方法返回固定常量（如 {@link SearchScope#SHOP}、
     * {@link SearchScope#BLOG}、{@link SearchScope#USER}），一个实现只声明一个域，
     * 不访问数据库、无副作用。
     */
    SearchScope scope();

    /**
     * 在本检索域内执行一次关键词分页搜索，不负责跨域融合。
     *
     * 使用场景：统一搜索编排实现 DefaultUnifiedSearchService.search() 对每个请求域各调用一次
     * （GET /search 的分组来源）；独立 Tab 下 SearchController 也会直接调用具体域实现——
     * GET /search/blogs、GET /search/users 走本方法，GET /search/shops 走店铺接口的两参数重载。
     *
     * 实现要点：入参 {@link SearchQuery}（统一搜索的公共查询上下文）由实现自行完成关键词、页码、
     * 页大小标准化（关键词上限 64 字符、页码从 1 起、每页上限 10，空白关键词返回空页）；
     * 返回 {@link PageResultDTO}（含 records、current、size、total、hasMore 的分页结果）时只含本域数据，
     * 域间配额分配和分组顺序由统一编排层决定。
     */
    PageResultDTO<T> search(SearchQuery query);
}
