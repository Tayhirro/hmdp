package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@Slf4j
@Component
public class ShopGeoDataInitializer {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 应用启动时把 MySQL 全量店铺数据预热到 Redis GEO。
     *
     * 使用场景：Spring 启动时（依赖注入完成后）由 {@code @PostConstruct} 自动执行一次，
     * 为「附近店铺」查询（{@link com.hmdp.service.impl.ShopServiceImpl}（店铺服务实现）按
     * shop:geo:{typeId} 做 GEO 检索）准备数据。
     * 关键操作：shopService.list() 全量读 tb_shop 表（数据库），按 typeId 分组后逐组
     * 写 Redis GEO，key = "shop:geo:" + 店铺类型 id（常量 SHOP_GEO_KEY 拼 typeId），
     * member = 店铺 id 字符串，坐标 Point(x, y)（x 为经度、y 为纬度）；无 TTL。
     * 表为空时仅打日志跳过；重复启动会对同 key 重复 add（GEO 底层 ZSet 同 member 覆盖坐标，幂等）。
     */
    @PostConstruct
    public void loadShopGeoData() {
        List<Shop> shops = shopService.list();
        if (shops == null || shops.isEmpty()) {
            log.info("No shop data found, skip GEO preload.");
            return;
        }

        Map<Long, List<Shop>> groupByType = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long, List<Shop>> entry : groupByType.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> sameTypeShops = entry.getValue();
            

            //  radis 注入 add( key, List<RedisGeoCommands.GeoLocation<String>>)
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(sameTypeShops.size());
            for (Shop shop : sameTypeShops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
        log.info("Shop GEO preload done. typeCount={}, shopCount={}", groupByType.size(), shops.size());
    }
}
