package com.hmdp.service.search.impl;

/*
 * 现实业务背景：用户提交“火锅攻略”后，笔记域需要从标题和正文中召回可公开展示的探店内容。
 * 实际触发：GET /search/blogs，或 GET /search 的 BLOG 分组调用。
 *
 * 设计精华：
 * 1. 对 tb_blog 的 title 和 content 两个字段做同一个关键词的 LIKE 匹配（OR 关系，
 *    任一字段命中即召回）；标题与正文属于同一个笔记文档的检索字段，不拆成两个 SearchScope。
 * 2. 空白关键词直接返回空页；关键词里的 %、_、\ 会转义成普通字符，防止用户输入通配符扩大匹配范围。
 * 3. SELECT 只取卡片和作者装配需要的列（id、shop_id、user_id、title、images、liked、comments、create_time）；
 *    完整正文虽然参与 WHERE 匹配，但不会进入搜索响应。
 * 4. 按 createTime DESC、id DESC（创建时间相同再比 id）稳定分页，每页数量上限 10；
 *    作者资料由 {@link BlogAssembler}（把 Blog 实体装配成博客卡片的组装器）把本页作者 ID
 *    收集后一次批量查询用户表补齐，避免逐篇查用户。
 * 5. MySQL 只是第一阶段正确性基线，未来可替换为全文索引而不改变 BlogSearchService 接口。
 */

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.blog.BlogAssembler;
import com.hmdp.service.search.BlogSearchService;
import com.hmdp.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MySqlBlogSearchService implements BlogSearchService {

    private final BlogMapper blogMapper;
    private final BlogAssembler blogAssembler;

    /**
     * 在 tb_blog 中按关键词搜索公开笔记，分页返回博客卡片。
     *
     * 使用场景：SearchController 的 GET /search/blogs 路由（“笔记”Tab），
     * 以及 DefaultUnifiedSearchService.search() 的 BLOG 分组（GET /search 综合页）。
     *
     * 实现要点：对 tb_blog 的 title 和 content 两个字段做同一个关键词的 LIKE 匹配（OR 关系，
     * 任一字段命中即召回）；关键词先去首尾空格（超过 64 字符报错）、空白直接返回空页，
     * 再把 %、_、\ 转义成普通字符防止 LIKE 通配符注入；页码从 1 起（小于 1 报错），
     * 每页数量未指定取 10、上限 10；SELECT 只取 id、shop_id、user_id、title、images、liked、
     * comments、create_time 八列（content 只参与 WHERE 匹配，不进入搜索响应），
     * 按 create_time DESC、id DESC（创建时间相同再比 id）稳定分页；作者与点赞信息由
     * {@link BlogAssembler}（把 Blog 实体装配成博客卡片的组装器）收集本页作者 ID 批量查询补齐，
     * 避免逐篇查用户；hasMore 按 当前页 x 每页条数 小于 total 计算。
     */
    @Override
    public PageResultDTO<BlogCardDTO> search(SearchQuery query) {
        int pageNumber = MySqlSearchSupport.normalizePage(query == null ? null : query.getCurrent());
        int pageSize = MySqlSearchSupport.normalizePageSize(
                query == null ? null : query.getPageSize(),
                SystemConstants.MAX_PAGE_SIZE
        );
        String keyword = MySqlSearchSupport.normalizeKeyword(query == null ? null : query.getKeyword());
        if (StrUtil.isBlank(keyword)) {
            return PageResultDTO.empty(pageNumber, pageSize);
        }

        String escapedKeyword = MySqlSearchSupport.escapeLikeKeyword(keyword);
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .select(
                        Blog::getId,
                        Blog::getShopId,
                        Blog::getUserId,
                        Blog::getTitle,
                        Blog::getImages,
                        Blog::getLiked,
                        Blog::getComments,
                        Blog::getCreateTime
                )
                .and(condition -> condition
                        .like(Blog::getTitle, escapedKeyword)
                        .or()
                        .like(Blog::getContent, escapedKeyword))
                .orderByDesc(Blog::getCreateTime)
                .orderByDesc(Blog::getId);
        Page<Blog> page = blogMapper.selectPage(new Page<>(pageNumber, pageSize), wrapper);

        List<BlogCardDTO> items = blogAssembler.toCards(page.getRecords());
        boolean hasMore = page.getCurrent() * page.getSize() < page.getTotal();
        return new PageResultDTO<>(items, page.getCurrent(), page.getSize(), page.getTotal(), hasMore);
    }
}
