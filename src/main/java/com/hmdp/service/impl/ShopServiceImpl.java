package com.hmdp.service.impl;

/*
 * 现实业务背景：用户点击店铺进入详情、按分类找店或查看附近店铺，运营人员更新店铺时，会进入本服务。
 * 实际触发：GET /shop/{id}、GET /shop/of/type 和 PUT /shop 直接触发；不存在 ID 还可能来自旧链接、过期列表或伪造 URL。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.utils.RedisLockClient;
import com.hmdp.utils.SystemConstants;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;



/**
 * 
 *  服务实现类
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisLockClient redisLockClient;

    /**
     * 按 ID 查询店铺的完整流程：先读 Redis 缓存（key 为 {@code cache:shop:<id>}）；非空 JSON 直接反序列化返回，
     * 使用场景：用户点击店铺卡片进入详情页时，前端发送 GET /shop/{id}，由 ShopController.queryShopById() 调用；项目内没有其他调用方。
     * 空字符串表示“已确认不存在”的空值缓存（TTL 2 分钟，由常量 CACHE_NULL_TTL 决定）；
     * 缓存完全未命中时用 {@link RedisLockClient}（封装了 Redis 分布式锁获取/释放的工具类，SETNX 实现）抢互斥锁
     * （key 为 {@code lock:shop:<id>}，10 秒自动过期，由常量 LOCK_SHOP_TTL 决定），
     * 未抢到就 {@code Thread.sleep(10)} 睡 10 毫秒后递归重试，抢到后查询 MySQL：
     * 查到写正常缓存（TTL 30 分钟，由常量 CACHE_SHOP_TTL 决定），查不到写空值缓存，最后释放锁。
     * 具体例子：店铺 100 首次访问时查库并缓存；随后请求直接读缓存。不存在的 999 会缓存空字符串 2 分钟，避免恶意请求反复打到数据库。
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // redis 查询
        if(StrUtil.isNotBlank(shopJson)){   
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(shopJson != null) return Result.fail("店铺不存在");

        
        // 数据库查询
        Shop shop = null;
        boolean isLock = false;
        try{
            isLock = redisLockClient.tryLock(lockKey);
            if(!isLock){
                Thread.sleep(10);
                return queryById(id);
            }else{
                shop = getById(id);
                if(shop == null){
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return Result.fail("店铺不存在");
                }else{
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                }
            }
        } catch (InterruptedException e){
            Thread.currentThread().interrupt(); // Restore interrupted status
            return Result.fail("查询失败");
        } finally {
            if(isLock) redisLockClient.unlock(lockKey);
        }
        return Result.ok(shop);
    }     
    /**
     * 删除店铺的完整流程：先校验 ID，再调用 MyBatis-Plus 按主键删除并返回成功。
     * 使用场景：当前代码库没有调用方（Controller 与其他 Service 均未引用本方法），属于预留的按主键删除能力。
     * 具体例子：{@code delete(100)} 删除 {@code tb_shop.id=100}；传 null 返回“店铺id不能为空”。
     * 注意：当前实现没有同步清理店铺缓存，也没有对外 Controller 入口；如以后开放删除接口，必须先补缓存一致性和权限校验。
     */
    @Override
    public Result delete(Long id){
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        removeById(id);
        return Result.ok();
    }



    /**
     * 按类型分页查询店铺的完整流程：
     * 使用场景：首页按分类浏览店铺或查看附近店铺时，前端发送 GET /shop/of/type?typeId=&current=（可带经纬度 x/y），
     * 由 ShopController.queryShopByType() 调用。
     * 1. 未传经纬度时，直接按 typeId 在 MySQL 使用普通页码分页。
     * 2. 传入经纬度时，在 Redis GEO（key 为 {@code shop:geo:<typeId>}）中以 5000 米为半径查从近到远的前 {@code current * 5} 个结果
     *    （5 是常量 DEFAULT_PAGE_SIZE，即每页条数），再跳过前 {@code (current - 1) * 5} 个截取本页 ID。
     * 3. 用一条 {@code id IN (...) ORDER BY FIELD(id, ...)} SQL 批量查 MySQL 店铺详情，按 GEO 返回的 ID 顺序恢复排序，并把 GEO 返回的距离写入每个 Shop 后返回。
     * 具体例子：每页 5 条、current=2 时，Redis 先取最近 10 家并跳过前 5 家；若第 6～10 家距离为 800～1500 米，
     * 返回的就是这 5 家及各自 distance。没有 x/y 时第二页只是普通数据库分页，不计算距离。
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y){
        if( x == null || y ==null){
            Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }else{
            //分页
            int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
            int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
            
            String key = SHOP_GEO_KEY + typeId;
            // 分页查询 redis  0 --- end
            GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                    key, 
                    GeoReference.fromCoordinate(x, y), 
                    new Distance(5000), 
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(end)
            );

            if (geoResults == null) 
                return Result.ok(Collections.emptyList());
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultList = geoResults.getContent();
            if(geoResultList.size() <= from) {
                return Result.ok(Collections.emptyList());
            }
            // 截取 id + 距离 --map id-distance --- 查shop SQL  --- shop返回
            List<Long> ids = new ArrayList<>(geoResultList.size());
            Map<String,Distance> distanceMap = new HashMap<>(geoResultList.size());
            geoResultList.stream().skip(from).forEach(
                result ->{
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr, distance);
                }
            );
            String idsStr = StrUtil.join(",", ids);
            List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
            for (Shop shop : shops){
                shop.setDistance(distanceMap.get(shop.getId().toString()).getValue()); // Set distance for each shop
            }
            return Result.ok(shops);
        }
    }


    /**
     * 更新店铺的完整流程：校验请求中含店铺 ID，按主键更新 MySQL，然后删除该店铺的 Redis 缓存；
     * 使用场景：客户端修改店铺信息时，前端发送 PUT /shop，由 ShopController.updateShop() 调用。
     * 下一次详情查询会回源数据库并重建缓存，避免继续读旧数据。
     * 具体例子：店铺 100 名称从“老店”改为“新店”，数据库更新后删除 {@code cache:shop:100}；下一位访客会读到“新店”。
     */
    @Override
    public Result update(Shop shop){
        if(shop.getId() == null) return Result.fail("店铺id不能为空");
        // 进行数据库更新
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
