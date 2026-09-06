package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缓存「逻辑过期」包装对象：把业务数据与逻辑过期时间一起序列化进 Redis。
 * 使用场景：逻辑过期方案中，缓存 value 存 {@link RedisData}（内嵌真实对象 + expireTime），
 * 命中后比较 expireTime 判断是否逻辑过期，过期则返回旧数据并异步重建；
 * 本类无显式方法，getter/setter 由 Lombok 的 {@code @Data} 生成。
 * 当前工程缓存采用「空值缓存 + 互斥锁」方案（见 ShopServiceImpl），本类暂无调用方（模板预留）。
 */
@Data
public class RedisData {
    /** 逻辑过期时间点（非 Redis TTL，数据在 Redis 中不过期，由该字段判旧）。 */
    private LocalDateTime expireTime;
    /** 被包装的真实业务数据（如 Shop 对象）。 */
    private Object data;
}
