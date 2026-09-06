package com.hmdp.service;

/*
 * 现实业务背景：用户查看店铺详情、按分类/距离找店，以及运营人员修改店铺时，需要店铺领域入口。
 * 实际触发：ShopController 的详情、类型/GEO 查询和更新调用本接口；关键词检索已独立到 service/search。
 * 注意：新增店铺走的是 POST /shop 直接调用继承自 IService 的 save()（不经过本接口声明的方法）；
 * delete() 目前没有任何 Controller 入口，且实现不清理 Redis 缓存，开放前需补缓存一致性和权限校验。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * 店铺服务。数据存于 tb_shop；店铺详情在 Redis 有 JSON 缓存（key 为 cache:shop:{店铺 ID}，TTL 30 分钟）。
 * 所有方法返回 {@link Result}（本项目统一的 HTTP 响应包装：{@code success/data/errorCode/errorMsg/traceId}）。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 按 ID 查店铺详情（GET /shop/{id}）：先读 Redis 缓存；空字符串代表“确认不存在”的空值缓存（TTL 2 分钟），
     * 用来挡住恶意反复查询不存在的 ID；缓存未命中时用互斥锁（lock:shop:{id}）保证同一店铺只有一个请求回源 MySQL。
     * 使用场景：用户在首页、店铺列表或搜索结果点击店铺进入详情页时触发；内部调用方仅 ShopController
     * （ShopGeoDataInitializer 启动建 GEO 用的继承 list()、BlogCommandService 校验商户用的继承 getById()，均不经过本方法）。
     */
    Result queryById(Long id);

    /**
     * 更新店铺（PUT /shop）：先更新 MySQL，再删除该店铺的 Redis 缓存（先更库后删缓存），
     * 下一次详情查询会回源数据库并重建缓存，避免读到旧数据。
     * 使用场景：管理端编辑店铺资料后保存时触发；内部调用方仅 ShopController。
     */
    Result update(Shop shop);

    /**
     * 按主键删除店铺；当前没有对外接口调用，也不会清理 Redis 缓存。
     * 使用场景：无——前端与内部 Service 当前均无调用方，属于未接线能力，开放前需补缓存清理和权限校验。
     */
    Result delete(Long id);

    /**
     * 按店铺类型分页查询（GET /shop/of/type），页大小固定 5：
     * 1. 不传经纬度 x/y 时，按 type_id 在 MySQL 做普通页码分页（第 1 条 SQL）。
     * 2. 传经纬度时，先在 Redis GEO（key 为 shop:geo:{typeId}）里查 5 公里内由近到远的前“页码×5”家，
     *    截取本页 5 家的 ID，第 2 条 SQL 用 IN 批量查店铺详情并按 GEO 距离顺序恢复排序，同时把距离（米）写入每家店铺。
     *    例：current=2 时取最近的 10 家并跳过前 5 家，返回第 6～10 家及各自距离。
     * 使用场景：首页按分类浏览店铺（不传 x/y）或查看“附近店铺”（传 x/y）时触发；内部调用方仅 ShopController。
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

}   
