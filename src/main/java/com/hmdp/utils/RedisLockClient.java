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


/**
 * 基于 Redis SET NX EX + Lua 校验的分布式锁客户端。
 * 加锁值带 JVM 级唯一前缀与线程 id，解锁走 unlock.lua 比对值一致才删除，保证只释放自己持有的锁。
 */
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
    
    /**
     * 生成当前线程的加锁标识值。
     *
     * 使用场景：{@link #tryLock} 写入 value、{@link #unlock} 作为 ARGV 传给 Lua 比对时调用。
     * 组成（按代码原样）：常量 LOCK_SHOP_TTL 的字面值 "10" + ID_PERFIX（JVM 启动时生成的
     * 随机 UUID，跨实例唯一）+ 当前线程 id，可区分不同应用实例与不同线程。
     *
     * @return 形如 "10{uuid}{threadId}" 的唯一标识
     */
    private String lockVal(){
        return LOCK_SHOP_TTL + ID_PERFIX + Thread.currentThread().getId();
    }

    /**
     * 尝试获取分布式锁（非阻塞，抢不到立即失败）。
     *
     * 使用场景：ShopServiceImpl 缓存重建（queryWithMutex）等需要互斥的地方调用，
     * key 通常传 "lock:shop:" + 店铺 id（常量 LOCK_SHOP_KEY 拼 id）。
     * 关键操作：Redis SET NX EX——setIfAbsent(key, lockVal(), LOCK_SHOP_TTL, TimeUnit.SECONDS)，
     * 即锁值 = {@link #lockVal()}，TTL = LOCK_SHOP_TTL = 10 秒（自动过期防持有者宕机死锁）。
     *
     * @param key 完整锁 key（由调用方拼好业务前缀）
     * @return true 表示抢到锁；false 表示已被占用
     */
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, lockVal(), LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }


    /**
     * 释放分布式锁（仅当锁仍由当前线程持有时才删除）。
     *
     * 使用场景：业务拿到锁并在 finally 中调用，key 必须与 {@link #tryLock} 一致。
     * 关键操作：执行 classpath 下 unlock.lua——GET KEYS[1] 等于 ARGV[1]（即当前
     * {@link #lockVal()}）才 DEL，否则返回 0 不动别人的锁，避免误删与超时后误删新锁。
     *
     * @param key 与加锁时相同的完整锁 key
     */
    public void unlock(String key) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), lockVal());
    }

}
