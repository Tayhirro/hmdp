package com.hmdp.service.search;

/*
 * 现实业务背景：用户只看到一个搜索框，但同一 Query 可能指向店铺、博客或用户，
 * 需要“综合”页一次请求就同时给出三类分组结果。
 * 实际触发：全局搜索页调用 GET /search；未指定 scope（即 SearchQuery.scopes 为空）时
 * 聚合店铺、笔记和用户三个域，指定后只查对应 Tab。
 *
 * 设计精华：
 * 1. 第一阶段统一层负责关键词标准化、确定性检索域选择和结果分组，不直接编写各业务 SQL。
 * 2. ShopSearchService 等垂直服务（实现 {@link VerticalSearchService}，
 *    单个搜索领域各自的搜索接口）只负责本域；新增内容类型通过该接口注册即自动接入。
 * 3. “任意搜索”只覆盖已注册且允许公开检索的业务域，绝不等于搜索任意数据库字段。
 */

import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.UnifiedSearchResultDTO;

public interface UnifiedSearchService {

    /**
     * 统一搜索合同：按关键词一次返回多个检索域的分组结果。
     *
     * 使用场景：SearchController 的 GET /search?keyword=&scope=&current=&pageSize= 路由，
     * “综合”页（scope 为空）与指定 Tab 共用这一个入口。
     *
     * 实现要点：当前按明确 scope 选择垂直域（未指定时三个域各执行一次分页查询，共 3 个子查询，
     * 在当前请求线程内串行执行）；实现层会先标准化参数——关键词去首尾空格（超过 64 字符报错）、
     * 页码从 1 起、每页数量未指定时取 5（各域上限 10）、scopes 为空时展开为全部已注册域——
     * 再按 {@link SearchScope} 枚举顺序逐域召回并组装成分组返回，各分组携带 total 与 hasMore；
     * 任一垂直域失败则本次请求整体失败。智能意图识别和跨域混排留给后续阶段。
     */
    UnifiedSearchResultDTO search(SearchQuery query);
}
