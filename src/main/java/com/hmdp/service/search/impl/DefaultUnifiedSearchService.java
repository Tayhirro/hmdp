package com.hmdp.service.search.impl;

/*
 * 现实业务背景：前端只有一个搜索框，但“综合”页需要一次请求同时展示店铺、笔记和用户三个分组。
 * 实际触发：GET /search；未指定 scope（SearchQuery.scopes 为空）时召回全部已注册域
 * （共执行 3 个子查询，对应 SHOP/BLOG/USER 三个 MySQL 分页 SELECT），指定 Tab 时只召回对应域（1 个子查询）。
 *
 * 设计精华：
 * 1. 第一阶段采用确定性路由：请求 scope 决定调用哪些垂直服务，不伪装成已经具备 AI 意图识别。
 * 2. 统一层只编排和分组，不写任何店铺、博客或用户 SQL；Spring 启动时把容器里所有
 *    {@link VerticalSearchService}（各搜索域的垂直搜索接口）实现按 scope 收进注册表，
 *    同一 scope 出现两个实现会在启动时直接失败，新域加一个实现类即自动接入。
 * 3. 按 SearchScope 枚举顺序稳定返回分组；“综合”是聚合视图，不是一个新的业务域。
 * 4. 3 个子查询在当前请求线程里串行执行，没有并行、超时或熔断；任一垂直域失败时
 *    本次请求整体失败，超时、隔离和部分降级留到故障增强阶段。
 * 5. 统一层负责参数标准化：关键词去首尾空格（超过 64 字符报错）、页码从 1 起、
 *    每页数量未指定时取 5（各域上限 10），scopes 为空时展开为全部已注册域。
 */

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.SearchResultItemDTO;
import com.hmdp.dto.SearchSectionDTO;
import com.hmdp.dto.UnifiedSearchResultDTO;
import com.hmdp.service.search.SearchScope;
import com.hmdp.service.search.UnifiedSearchService;
import com.hmdp.service.search.VerticalSearchService;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultUnifiedSearchService implements UnifiedSearchService {

    private final Map<SearchScope, VerticalSearchService<? extends SearchResultItemDTO>> servicesByScope;

    /**
     * 构造统一搜索服务，把容器里所有垂直搜索实现按域收进注册表。
     *
     * 使用场景：Spring 容器启动创建该 @Service Bean 时注入全部
     * {@link VerticalSearchService}（各搜索域的垂直搜索接口）实现；
     * 单元测试直接 new 本类时也会触发同样的注册与校验。
     *
     * 实现要点：以 {@link SearchScope} 枚举为键构建 {@link EnumMap} 注册表；
     * 同一域出现两个实现立即抛 IllegalStateException（“搜索域存在重复实现”），
     * 让应用启动直接失败，而不是运行时被静默覆盖。
     */
    public DefaultUnifiedSearchService(
            List<VerticalSearchService<? extends SearchResultItemDTO>> verticalSearchServices
    ) {
        this.servicesByScope = new EnumMap<>(SearchScope.class);
        for (VerticalSearchService<? extends SearchResultItemDTO> service : verticalSearchServices) {
            VerticalSearchService<? extends SearchResultItemDTO> previous =
                    servicesByScope.put(service.scope(), service);
            if (previous != null) {
                throw new IllegalStateException("搜索域存在重复实现: " + service.scope());
            }
        }
    }

    /**
     * 执行统一搜索：标准化请求后按 scope 逐域调用垂直搜索并组装分组结果。
     *
     * 使用场景：{@link UnifiedSearchService}（统一搜索入口接口）的实现方法，
     * 唯一的生产调用方是 SearchController 的 GET /search 路由（综合页与指定 Tab 共用）。
     *
     * 实现要点：先经 normalizeQuery 标准化（关键词去首尾空格、超 64 字符报错；页码从 1 起；
     * 每页未指定取 5、上限 10；scopes 为空展开为全部已注册域），再按 {@link SearchScope}
     * 枚举顺序对每个请求域从注册表取实现、调用其 search 并经 toSection 转成分组——
     * 未指定 scope 时即串行执行 3 个子查询（tb_shop、tb_blog、tb_user 各一条 MySQL 分页 SELECT），
     * 没有并行、超时或熔断，任一域失败则本次请求整体失败；
     * 响应携带标准化后的关键词和各分组的 total、hasMore。
     */
    @Override
    public UnifiedSearchResultDTO search(SearchQuery query) {
        SearchQuery normalizedQuery = normalizeQuery(query);
        Set<SearchScope> requestedScopes = normalizedQuery.getScopes();
        List<SearchSectionDTO> sections = new ArrayList<>(requestedScopes.size());

        for (SearchScope scope : SearchScope.values()) {
            if (!requestedScopes.contains(scope)) {
                continue;
            }
            VerticalSearchService<? extends SearchResultItemDTO> service = servicesByScope.get(scope);
            if (service == null) {
                throw new IllegalStateException("搜索域缺少实现: " + scope);
            }
            sections.add(toSection(scope, service.search(normalizedQuery)));
        }

        return new UnifiedSearchResultDTO()
                .setNormalizedKeyword(normalizedQuery.getKeyword())
                .setSections(sections);
    }

    /**
     * 把原始请求复制成一份参数完备的标准化 {@link SearchQuery}。
     *
     * 使用场景：仅被本类 search() 在调用各垂直域之前调用一次，
     * 保证所有域拿到同一份标准化查询，Controller 创建的原对象不被修改。
     *
     * 实现要点：关键词经 MySqlSearchSupport（本包三个 MySQL 垂直搜索共用的输入边界工具类）
     * trim 去首尾空格、超过 64 字符抛 BusinessException；页码为 null 取 1、小于 1 报错；
     * 页大小为 null 取默认 5、不在 1 到 10 范围内报错；scopes 为 null 或空集合时展开为
     * 全部 {@link SearchScope} 域，非空则拷贝成 {@link EnumSet}；
     * cityCode、longitude、latitude 原样透传（当前各垂直实现尚未使用，为本地生活 Query 预留）。
     */
    private SearchQuery normalizeQuery(SearchQuery query) {
        String keyword = MySqlSearchSupport.normalizeKeyword(query == null ? null : query.getKeyword());
        int pageNumber = MySqlSearchSupport.normalizePage(query == null ? null : query.getCurrent());
        int pageSize = MySqlSearchSupport.normalizePageSize(
                query == null ? null : query.getPageSize(),
                SystemConstants.DEFAULT_PAGE_SIZE
        );
        Set<SearchScope> requestedScopes = query == null ? null : query.getScopes();
        Set<SearchScope> normalizedScopes = requestedScopes == null || requestedScopes.isEmpty()
                ? EnumSet.allOf(SearchScope.class)
                : EnumSet.copyOf(requestedScopes);

        return new SearchQuery()
                .setKeyword(keyword == null ? "" : keyword)
                .setScopes(normalizedScopes)
                .setCurrent(pageNumber)
                .setPageSize(pageSize)
                .setCityCode(query == null ? null : query.getCityCode())
                .setLongitude(query == null ? null : query.getLongitude())
                .setLatitude(query == null ? null : query.getLatitude());
    }

    /**
     * 把单个垂直域的分页结果转换成统一响应里的一个分组。
     *
     * 使用场景：仅被本类 search() 在每个域召回完成后调用，逐域包装。
     *
     * 实现要点：把原分页列表拷贝为 {@link SearchResultItemDTO}（只约定返回业务对象公开 ID 的
     * 最小合同）列表，并透传 scope、total、hasMore；本方法不做跨域排序或二次截断，
     * 每组条数由垂直域按统一层下发的页大小（默认 5）自行限制。
     */
    private SearchSectionDTO toSection(
            SearchScope scope,
            PageResultDTO<? extends SearchResultItemDTO> page
    ) {
        List<SearchResultItemDTO> items = new ArrayList<>(page.getList());
        return new SearchSectionDTO()
                .setScope(scope)
                .setItems(items)
                .setTotal(page.getTotal())
                .setHasMore(page.isHasMore());
    }
}
