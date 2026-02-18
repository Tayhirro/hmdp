package com.hmdp.utils;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import cn.hutool.core.lang.UUID;


@Component
public class RedisLockClient {
    private static final String ID_PERFIX = UUID.randomUUID().toString();


    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    private String lockVal(){
        return LOCK_SHOP_TTL + ID_PERFIX + Thread.currentThread().getId();
    }

    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, lockVal(), LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }


    public void unlock(String key) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), lockVal());
    }

}
