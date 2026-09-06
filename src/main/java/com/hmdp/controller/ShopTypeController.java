package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 
 * 商铺分类前端控制器（根路径 {@code /shop-type}），目前只提供分类列表查询。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询全部商铺分类列表。
     * 使用场景：前端首页或发布笔记页首次加载分类筛选栏时，发送 GET /shop-type/list。
     * 数据库：查 tb_shop_type 全表并按 sort 字段升序返回；本查询不经过 Redis 缓存。
     */
    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
