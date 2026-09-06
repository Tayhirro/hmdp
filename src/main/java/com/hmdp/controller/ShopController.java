package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 
 * 商铺前端控制器（根路径 {@code /shop}），提供商铺详情、新增、更新与分类/附近分页查询；
 * 详情查询走 Redis 缓存（含空值缓存与互斥锁），更新走“先更新库再删缓存”策略。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息。
     * 使用场景：用户在首页、店铺列表或搜索结果中点击店铺进入详情页时，前端发送 GET /shop/{id}。
     * Redis/数据库：先读缓存 cache:shop:{id}（空字符串代表“确认不存在”的空值缓存，TTL 2 分钟）；
     * 未命中时用分布式锁 lock:shop:{id}（10 秒自动过期）互斥回源 tb_shop，查到缓存 30 分钟，查不到写空值缓存。
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息。
     * 使用场景：管理端提交新店铺表单时，前端发送 POST /shop，请求体为商铺 JSON。
     * 数据库：直接向 tb_shop 插入一行并返回生成的主键；本方法不写店铺缓存与 GEO 数据。
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息。
     * 使用场景：管理端编辑店铺资料后保存时，前端发送 PUT /shop，请求体必须携带商铺 id。
     * 数据库/Redis：按主键更新 tb_shop，随后删除缓存 cache:shop:{id}，下次详情查询回源数据库重建缓存。
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息，传经纬度时按距离由近到远排序。
     * 使用场景：首页按分类浏览店铺（不传 x/y）或查看“附近店铺”（传 x/y）时，前端发送
     * GET /shop/of/type?typeId=&current=&x=&y=；current 未传默认 1。
     * 数据库/Redis：不传 x/y 时按 type_id 在 MySQL 普通页码分页（每页 5 条）；传 x/y 时在 Redis GEO
     * （key 为 shop:geo:{typeId}）以坐标为圆心、5000 米为半径取从近到远的前 current*5 家，
     * 跳过之前页数后用一条按 FIELD(id,...) 排序的 SQL 回表 tb_shop，并把距离写入每条结果。
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

}
