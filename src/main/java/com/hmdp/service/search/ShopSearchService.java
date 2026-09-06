package com.hmdp.service.search;

/*
 * 现实业务背景：用户主动输入店铺关键词时，需要独立于店铺详情、缓存和管理命令的检索入口。
 * 实际触发：店铺搜索框调用 GET /search/shops；旧客户端的 GET /shop/of/name 也由搜索 Controller 兼容转发。
 *
 * 设计精华：
 * 1. 搜索是跨内容域能力，不把关键词 SQL 继续揉进 ShopController。
 * 2. Controller 只处理 HTTP 协议，本接口返回搜索 DTO（{@link ShopSearchItemDTO}，
 *    店铺搜索结果卡片：id、name、typeId、images、area、address、评分销量等摘要字段），
 *    数据库实现放在独立适配器中。
 * 3. 未来接 Elasticsearch 时替换实现，不改变前端的搜索合同，也不污染店铺核心 Service。
 */

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;

public interface ShopSearchService extends VerticalSearchService<ShopSearchItemDTO> {

    /**
     * 店铺搜索是统一搜索中的一个垂直域，不是其他内容搜索的父接口。
     *
     * 使用场景：统一搜索编排实现
     * {@link com.hmdp.service.search.impl.DefaultUnifiedSearchService}（GET /search 的聚合实现）
     * 在构造时调用本方法把店铺搜索注册进域到服务的注册表，之后请求按 SHOP 域路由到店铺搜索。
     *
     * 实现要点：直接返回常量 {@link SearchScope#SHOP}，不访问数据库、无副作用；
     * 由本接口 default 方法统一提供。
     */
    @Override
    default SearchScope scope() {
        return SearchScope.SHOP;
    }

    /**
     * 按店铺名称关键词分页搜索。
     *
     * 使用场景：SearchController 的两个路由——GET /search/shops（新客户端店铺 Tab）
     * 和 GET /shop/of/name（旧客户端兼容入口，响应结构另行适配）都调用本重载。
     *
     * 实现要点：当前 MySQL 实现只对 tb_shop 的 name 字段做 LIKE 匹配，不匹配 address 等其他字段；
     * 关键词先去首尾空格（超过 64 字符报错），再把 %、_、\ 转义成普通字符防止 LIKE 通配符注入；
     * 空白关键词直接返回空页；页码从 1 起（小于 1 报错），本重载不接受页大小，固定每页 10 条（上限 10），
     * 按 id 升序保证翻页顺序稳定。
     *
     * @param keyword 用户输入的关键词；空白关键词返回空页
     * @param current 从 1 开始的页码
     * @return 店铺摘要及明确的分页元数据
     */
    PageResultDTO<ShopSearchItemDTO> search(String keyword, Integer current);

    /**
     * 默认适配保留旧的两参数实现（忽略 pageSize，固定每页 10 条）。
     *
     * 使用场景：统一搜索编排实现 DefaultUnifiedSearchService.search()（GET /search 的 SHOP 分组）
     * 通过 {@link VerticalSearchService}（单个搜索领域各自的搜索接口）合同调用本方法；
     * SearchController 不直接调用本重载（店铺 Tab 走两参数版本）。
     *
     * 实现要点：当前 MySQL 实现会覆盖本方法，读取 {@link SearchQuery}（统一搜索的公共查询上下文，
     * 含关键词、检索域、页码、页大小等）里的 pageSize，以支持统一搜索给每个分组分配更小的配额
     * （统一层默认每组 5 条、上限 10）；未传 pageSize 时回退为每页 10 条。
     */
    @Override
    default PageResultDTO<ShopSearchItemDTO> search(SearchQuery query) {
        return search(
                query == null ? null : query.getKeyword(),
                query == null ? 1 : query.getCurrent()
        );
    }
}
