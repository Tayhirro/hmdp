package com.hmdp.service.impl;

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
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisLockClient redisLockClient;

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
    @Override
    public Result delete(Long id){
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        removeById(id);
        return Result.ok();
    }



    // ||分页查询 redis --- 分页查询+距离排序+SQL --- 赋值shop 的距离 --返回
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


    // 更新 -- 删除缓存 
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
