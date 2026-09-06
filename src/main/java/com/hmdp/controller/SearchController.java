package com.hmdp.controller;

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.service.search.BlogSearchService;
import com.hmdp.service.search.SearchScope;
import com.hmdp.service.search.ShopSearchService;
import com.hmdp.service.search.UnifiedSearchService;
import com.hmdp.service.search.UserSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 统一搜索入口。
 *
 * 搜索属于跨店铺、博客和用户的独立读取能力，不归属于任一业务实体的 CRUD Controller。
 * 第一阶段已落地三个 MySQL 垂直搜索与确定性统一编排；中文分词、语义检索和自动意图路由尚未实现。
 *
 * 设计要点：
 * 
 *     1. 新客户端只依赖 {@code /search/**} 合同，底层从 MySQL 升级到 Elasticsearch 时不改页面路由。
 *     2. 每类内容使用独立搜索 Service，避免未来把所有检索逻辑重新堆进一个大函数。
 *     3. 旧路径只做兼容适配，不再拥有查询逻辑；移除前应先确认所有旧客户端已经迁移。
 * 
 */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final ShopSearchService shopSearchService;
    private final BlogSearchService blogSearchService;
    private final UserSearchService userSearchService;
    private final UnifiedSearchService unifiedSearchService;

    /**
     * 一个搜索框的统一入口。scope 为空表示“综合”；传 BLOG、SHOP 或 USER 时只返回所选 Tab。
     * 使用场景：用户在顶部搜索框提交关键词时，前端发送 GET /search?keyword=&scope=&current=&pageSize=；
     * current 未传默认 1，pageSize 未传默认 5（各垂直域实际上限 10）。
     * 数据库：由统一编排服务对 scope 覆盖的每个域各执行一条 MySQL LIKE 分页查询
     * （综合模式共 3 条，分别查 tb_shop、tb_blog、tb_user），当前线程内串行执行。
     */
    @GetMapping("/search")
    public Result search(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "scope", required = false) Set<SearchScope> scopes,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize
    ) {
        SearchQuery query = new SearchQuery()
                .setKeyword(keyword)
                .setScopes(scopes)
                .setCurrent(current)
                .setPageSize(pageSize);
        return Result.ok(unifiedSearchService.search(query));
    }

    /**
     * 店铺名称垂直搜索。用户在店铺列表输入名称片段并提交搜索时，前端发送 GET /search/shops?keyword=&current=；
     * current 未传默认 1，每页数量上限 10。
     * 数据库：对 tb_shop.name 做 LIKE 匹配（转义 %、_、\ 通配符），按 id 升序稳定分页；空关键词直接返回空页。
     */
    @GetMapping("/search/shops")
    public Result searchShops(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return Result.ok(shopSearchService.search(keyword, current));
    }

    /**
     * 笔记垂直搜索。用户切换到“笔记”Tab 后提交关键词时，前端发送 GET /search/blogs?keyword=&current=&pageSize=；
     * current 未传默认 1，pageSize 未传默认 10。
     * 数据库：对 tb_blog 的 title 或 content 任一字段 LIKE 命中即召回，按 create_time、id 倒序分页，
     * SELECT 只取卡片所需列，作者由装配器批量补齐。
     */
    @GetMapping("/search/blogs")
    public Result searchBlogs(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        return Result.ok(blogSearchService.search(new SearchQuery()
                .setKeyword(keyword)
                .setCurrent(current)
                .setPageSize(pageSize)));
    }

    /**
     * 用户垂直搜索。用户切换到“用户”Tab 后提交关键词时，前端发送 GET /search/users?keyword=&current=&pageSize=；
     * current 未传默认 1，pageSize 未传默认 10。
     * 数据库：只对 tb_user.nick_name 做 LIKE 匹配（绝不允许按 account、phone 搜索），只读 id、nick_name、icon 三列，按 id 升序分页。
     */
    @GetMapping("/search/users")
    public Result searchUsers(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        return Result.ok(userSearchService.search(new SearchQuery()
                .setKeyword(keyword)
                .setCurrent(current)
                .setPageSize(pageSize)));
    }

    /**
     * 旧版店铺名称搜索兼容入口。
     *
     * 使用场景：仅旧客户端仍会调用 GET /shop/of/name?name=&current=（current 未传默认 1）；
     * 新代码不得继续调用该路径。
     * 兼容行为：继续返回 records 数组和顶层 total，避免旧页面因响应结构变化失效。
     * 数据库：与 /search/shops 一致，对 tb_shop.name 做 LIKE 分页匹配（转义 %、_、\），按 id 升序。
     */
    @Deprecated
    @GetMapping("/shop/of/name")
    public Result searchShopsLegacy(
            @RequestParam(value = "name", defaultValue = "") String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        PageResultDTO<ShopSearchItemDTO> page = shopSearchService.search(name, current);
        return Result.ok(page.getList(), page.getTotal());
    }
}
