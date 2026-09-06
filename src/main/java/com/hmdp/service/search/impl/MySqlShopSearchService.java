package com.hmdp.service.search.impl;

/*
 * 现实业务背景：当前数据量较小时，用户输入“火锅”等店名片段，由 MySQL 提供第一版可验证的搜索基线。
 * 实际触发：ShopSearchService.search() 收到标准化关键词后对 tb_shop 的 name 字段做 LIKE 匹配
 * （当前只匹配店名，不匹配 address 等其他字段），并组装搜索卡片。
 *
 * 设计精华：
 * 1. MySQL 是当前搜索数据源；搜索引擎以后只作为可重建索引，不能反过来成为店铺真相源。
 * 2. 空白关键词直接返回空页，避免一次误操作退化为“分页浏览全表”；
 *    关键词里的 %、_、\ 会转义成普通字符，防止用户输入 LIKE 通配符扩大匹配范围。
 * 3. 固定按 id 升序排序，避免同一份数据翻页时顺序漂移；页码从 1 起，每页数量上限 10
 *    （店铺独立 Tab 未传 pageSize 时即用每页 10 条）。
 * 4. Shop Entity 只停留在持久层，本服务统一转换为 ShopSearchItemDTO
 *    （店铺搜索卡片：id、name、typeId、images、area、address、均单价、销量、评价数、评分、营业时间）。
 */

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.search.ShopSearchService;
import com.hmdp.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MySqlShopSearchService implements ShopSearchService {

    private final ShopMapper shopMapper;

    /**
     * 按店铺名称关键词分页搜索，页大小固定为上限值 10。
     *
     * 使用场景：SearchController 的两个路由——GET /search/shops（新客户端店铺 Tab）
     * 和 GET /shop/of/name（旧客户端兼容入口）都调用本重载，
     * 是店铺搜索唯一被 Controller 直接调用的方法。
     *
     * 实现要点：委托 {@link #searchInternal}，页大小固定传 {@link SystemConstants#MAX_PAGE_SIZE}
     * （即 10），本重载不接受调用方指定页大小；匹配、排序、转义规则见 searchInternal
     * （tb_shop.name 单字段 LIKE、id 升序、%/_/\ 转义）。
     */
    @Override
    public PageResultDTO<ShopSearchItemDTO> search(String keyword, Integer current) {
        return searchInternal(keyword, current, SystemConstants.MAX_PAGE_SIZE);
    }

    /**
     * 以统一搜索的查询上下文执行店铺名称搜索，支持每组更小的页大小配额。
     *
     * 使用场景：DefaultUnifiedSearchService.search() 的 SHOP 分组（GET /search 综合页）；
     * 本方法覆盖 ShopSearchService 接口的 default 适配（default 版本忽略 pageSize、固定每页 10 条）。
     *
     * 实现要点：委托 {@link #searchInternal} 并透传 {@link SearchQuery}（统一搜索的公共查询上下文）
     * 里的页大小（统一层默认给每组分配 5 条）；未传时回退为每页 10 条；query 为 null 时各字段按
     * null 处理（页码取 1、页大小取 10、空白关键词返回空页）。
     */
    @Override
    public PageResultDTO<ShopSearchItemDTO> search(SearchQuery query) {
        return searchInternal(
                query == null ? null : query.getKeyword(),
                query == null ? null : query.getCurrent(),
                query == null ? null : query.getPageSize()
        );
    }

    /**
     * 店铺搜索的实际执行体：标准化入参后对 tb_shop.name 做 LIKE 分页查询并组装搜索卡片。
     *
     * 使用场景：仅被本类两个 search 重载调用，是 GET /search/shops、GET /shop/of/name
     * 和 GET /search 的 SHOP 分组共用的底层实现。
     *
     * 实现要点：页码从 1 起（小于 1 报错）、页大小未传取 10 且必须在 1 到 10 之间（超范围报错）；
     * 关键词去首尾空格（超过 64 字符报错）、空白直接返回空页（避免误操作退化为全表分页浏览），
     * 再经 escapeLikeKeyword 把 %、_、\ 转义成普通字符防止 LIKE 通配符注入；
     * 只匹配 tb_shop 的 name 字段（不匹配 address 等其他字段），按 id 升序保证翻页顺序稳定；
     * 结果经 toSearchItem 逐条转成 {@link ShopSearchItemDTO}，
     * hasMore 按 当前页 x 每页条数 小于 total 计算。
     */
    private PageResultDTO<ShopSearchItemDTO> searchInternal(
            String keyword,
            Integer current,
            Integer requestedPageSize
    ) {
        int pageNumber = MySqlSearchSupport.normalizePage(current);
        int pageSize = MySqlSearchSupport.normalizePageSize(
                requestedPageSize,
                SystemConstants.MAX_PAGE_SIZE
        );
        String normalizedKeyword = MySqlSearchSupport.normalizeKeyword(keyword);
        if (StrUtil.isBlank(normalizedKeyword)) {
            return PageResultDTO.empty(pageNumber, pageSize);
        }

        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>()
                .like(Shop::getName, escapeLikeKeyword(normalizedKeyword))
                .orderByAsc(Shop::getId);
        Page<Shop> page = shopMapper.selectPage(
                new Page<>(pageNumber, pageSize),
                query
        );

        List<ShopSearchItemDTO> items = page.getRecords().stream()
                .map(this::toSearchItem)
                .collect(Collectors.toList());
        boolean hasMore = page.getCurrent() * page.getSize() < page.getTotal();
        return new PageResultDTO<>(items, page.getCurrent(), page.getSize(), page.getTotal(), hasMore);
    }

    /**
     * 转义关键词中的 LIKE 通配符，使其按字面量参与匹配。
     *
     * 使用场景：仅被本类 searchInternal 在拼 LIKE 条件前调用。
     *
     * 实现要点：把反斜杠和 %、_ 分别替换成 \\、\%、\_（先转义反斜杠再转义通配符），
     * 逻辑委托 {@link MySqlSearchSupport}（本包三个 MySQL 垂直搜索共用的输入边界工具类）
     * 的静态方法，本类不做额外加工。
     */
    private String escapeLikeKeyword(String keyword) {
        return MySqlSearchSupport.escapeLikeKeyword(keyword);
    }

    /**
     * 把 Shop 实体转换成店铺搜索卡片 DTO。
     *
     * 使用场景：仅被本类 searchInternal 以方法引用方式对本页每条记录调用。
     *
     * 实现要点：字段一一拷贝（id、name、typeId、images、area、address、avgPrice、sold、
     * comments、score、openHours），不做加工、不发二次查询；
     * Shop Entity 只停留在持久层，不出现在搜索响应里。
     */
    private ShopSearchItemDTO toSearchItem(Shop shop) {
        return new ShopSearchItemDTO()
                .setId(shop.getId())
                .setName(shop.getName())
                .setTypeId(shop.getTypeId())
                .setImages(shop.getImages())
                .setArea(shop.getArea())
                .setAddress(shop.getAddress())
                .setAvgPrice(shop.getAvgPrice())
                .setSold(shop.getSold())
                .setComments(shop.getComments())
                .setScore(shop.getScore())
                .setOpenHours(shop.getOpenHours());
    }
}
