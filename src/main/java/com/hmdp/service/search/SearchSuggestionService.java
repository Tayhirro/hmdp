package com.hmdp.service.search;

/*
 * 现实业务背景：用户尚未点“搜索”时，输入框需要随输入实时下拉返回关键词补全或可直达的店铺/笔记/用户，
 * 让用户少打字、直接命中目标。
 * 实际触发：未来搜索框输入发生变化时调用；当前项目里没有任何 Controller 暴露它，
 * 也没有实现类，因此现在不会发起任何数据库查询。
 *
 * 设计精华：输入提示与完整搜索分别建模。提示接口（本文件）只返回少量低延迟候选
 * （{@link SearchSuggestionDTO}：可回填的 text，加上可选的所属检索域 scope 和直达对象 ID targetId），
 * 完整搜索（{@link UnifiedSearchService}，统一搜索入口，聚合各垂直域结果）才执行召回、分页和排序。
 */

import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.SearchSuggestionDTO;

import java.util.List;

public interface SearchSuggestionService {

    /**
     * 根据输入片段和城市/位置上下文返回少量搜索建议。
     *
     * 使用场景：预留给未来搜索框的输入联想（输入变化时实时下拉补全）；
     * 当前项目没有任何 Controller 路由或其他 Service 调用本方法，也没有实现类，
     * 因此现在不会发起任何数据库查询。
     *
     * 实现要点：入参复用 {@link SearchQuery}（统一搜索的公共查询上下文），此处预期只用其中的
     * keyword 输入片段和 cityCode、longitude、latitude 位置上下文；返回少量低延迟候选
     * {@link SearchSuggestionDTO}（可回填的 text，加可选检索域 scope 和直达对象 ID targetId），
     * 不执行召回、分页和排序——完整搜索由 {@link UnifiedSearchService} 负责。
     */
    List<SearchSuggestionDTO> suggest(SearchQuery query);
}
