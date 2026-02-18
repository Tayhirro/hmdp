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
