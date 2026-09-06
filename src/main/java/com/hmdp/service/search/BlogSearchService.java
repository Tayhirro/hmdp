package com.hmdp.service.search;

/*
 * 现实业务背景：用户在统一搜索框输入“火锅攻略”等关键词时，需要从公开探店笔记中找到相关内容。
 * 实际触发：GET /search 综合页的笔记分组，或用户切换到“笔记”Tab 后调用 GET /search/blogs。
 *
 * 设计精华：
 * 1. 笔记是 {@link VerticalSearchService}（单个搜索领域——博客/商铺/用户——各自的搜索接口）
 *    的平级垂直域之一，不继承 ShopSearchService（店铺搜索接口），也不把博客 SQL 写进统一编排层。
 * 2. 第一阶段只建立 MySQL 关键词基线：对 tb_blog 的 title 和 content 两个字段做 LIKE 匹配，
 *    任一字段命中即召回；中文分词、语义召回和相关度排序以后替换实现。
 * 3. 返回 {@link BlogCardDTO}（博客列表卡片：id、标题、封面、点赞数等首屏字段），
 *    不返回完整正文；搜索列表与博客详情保持不同的数据边界。
 */

import com.hmdp.dto.BlogCardDTO;

public interface BlogSearchService extends VerticalSearchService<BlogCardDTO> {

    /**
     * 声明当前垂直服务只负责公开笔记搜索。
     *
     * 使用场景：统一搜索编排实现
     * {@link com.hmdp.service.search.impl.DefaultUnifiedSearchService}（GET /search 的聚合实现）
     * 在构造时遍历容器里的 {@link VerticalSearchService} 实现并调用本方法建立域到服务的注册表，
     * 之后请求按 BLOG 域路由到笔记搜索。
     *
     * 实现要点：直接返回常量 {@link SearchScope#BLOG}，不访问数据库、无副作用；
     * 由本接口 default 方法统一提供，笔记搜索实现类无需重复声明。
     */
    @Override
    default SearchScope scope() {
        return SearchScope.BLOG;
    }
}
