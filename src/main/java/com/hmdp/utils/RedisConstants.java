package com.hmdp.utils;

/**
 * Redis key 前缀与 TTL 常量集中定义。
 * 约定：所有 key 都由「前缀常量 + 业务 id」拼接而成，TTL 常量本身不带单位，
 * 实际单位由调用处在 TimeUnit 参数中指定（各常量注释按当前调用方核实标注）。
 */
public class RedisConstants {
    /** 短信验证码 key 前缀。完整 key = "login:code:" + 手机号，String 类型，value 为验证码；写入与校验删除均在 UserServiceImpl。 */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 验证码 TTL。UserServiceImpl 以 TimeUnit.MINUTES 使用，即验证码有效期 2 分钟。 */
    public static final Long LOGIN_CODE_TTL = 2L;
    /** 验证码发送频率锁 key 前缀。完整 key = "login:code:send:lock:" + 手机号，String，value "1"，UserServiceImpl 用 SET NX EX 抢锁，发送失败删锁允许重试。 */
    public static final String LOGIN_CODE_SEND_LOCK_KEY = "login:code:send:lock:";
    /** 发送频率锁 TTL。UserServiceImpl 以 TimeUnit.SECONDS 使用，即 60 秒内同一手机号只能发一次验证码。 */
    public static final Long LOGIN_CODE_SEND_LOCK_TTL = 60L;
    /** 登录令牌 key 前缀。完整 key = "login:token:" + token（UUID），Hash 类型，value 为 UserDTO 字段；写：UserServiceImpl 登录成功；读+续期：{@link com.hmdp.auth.RedisTokenAuthResolver}（Redis token 认证解析器）；删：登出。 */
    public static final String LOGIN_USER_KEY = "login:token:";
    /** 登录令牌 TTL。UserServiceImpl 登录时以 TimeUnit.SECONDS 设置、RedisTokenAuthResolver 命中时以秒刷新，即 36000 秒 = 10 小时。 */
    public static final Long LOGIN_USER_TTL = 36000L;

    /** 缓存空值 TTL。ShopServiceImpl 查不到店铺时写空串占位（防缓存穿透）并以 TimeUnit.MINUTES 使用，即空值缓存 2 分钟。 */
    public static final Long CACHE_NULL_TTL = 2L;

    /** 店铺缓存 TTL。ShopServiceImpl 缓存命中重建时以 TimeUnit.MINUTES 使用，即店铺 JSON 缓存 30 分钟。 */
    public static final Long CACHE_SHOP_TTL = 30L;
    /** 店铺缓存 key 前缀。完整 key = "cache:shop:" + 店铺 id，String 类型，value 为店铺 JSON；读写与更新后删除均在 ShopServiceImpl。 */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /** 店铺缓存重建互斥锁 key 前缀。完整 key = "lock:shop:" + 店铺 id；ShopServiceImpl 传给 {@link com.hmdp.utils.RedisLockClient}（分布式锁客户端）加锁防缓存击穿。 */
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    /** 互斥锁 TTL。RedisLockClient.tryLock 以 TimeUnit.SECONDS 做 SET NX EX，即锁 10 秒自动过期，防止持有者宕机死锁。 */
    public static final Long LOCK_SHOP_TTL = 10L;

    /** 秒杀库存 key 前缀（预留）。完整 key = "seckill:stock:" + 优惠券 id，String 类型存库存数；当前工程代码尚未引用。 */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** Feed 推模式收件箱 key 前缀（预留）。完整 key = "feed:" + 用户 id，List/ZSet 存收到的笔记 id；当前工程代码尚未引用（收件箱改由 FEED_CACHE_KEY 快照方案实现）。 */
    public static final String FEED_KEY = "feed:";
    /** 店铺 GEO key 前缀。完整 key = "shop:geo:" + 店铺类型 id，GEO 类型，member = 店铺 id；写：ShopGeoDataInitializer 启动预热；读：ShopServiceImpl 附近店铺分页。 */
    public static final String SHOP_GEO_KEY = "shop:geo:";
    /** 签到 BitMap key 前缀。完整 key = "sign:" + 用户 id + ":yyyyMM"（如 sign:5:202203），UserServiceImpl 签到/统计使用。 */
    public static final String USER_SIGN_KEY = "sign:";

    /** 关注关系 Set key 前缀。完整 key = "follow:" + 用户 id，Set 类型，member = 被关注用户 id；FollowServiceImpl 关注/取关/共同关注（SINTER）读写，FollowChangedEventListener 在关注变更时删除对应 key 以同步缓存。 */
    public static final String FOLLOW_KEY = "follow:"; // 关注
    /** Feed 收件箱快照缓存 key 前缀。完整 key = "feed:cache:" + 用户 id + ":" + 推荐模式 + ":" + 算法版本（+ ":current" 指针或 ":snapshot:" + 快照 id），FeedCacheService 使用。 */
    public static final String FEED_CACHE_KEY = "feed:cache:";
    /** Feed 曝光记录 key 前缀。完整 key = "feed:exposure:" + 用户 id，ZSet 类型，member = 笔记 id（score 为曝光时间）；FeedExposureService 用于去重与降权。 */
    public static final String FEED_EXPOSURE_KEY = "feed:exposure:";
    /** 博客接口限流计数 key 前缀。完整 key = "rate:blog:" + 规则名 + ":" + 主体（"u:用户 id" 或 "ip:IP"），BlogRateLimitInterceptor 以 Lua 脚本窗口计数。 */
    public static final String BLOG_RATE_LIMIT_KEY = "rate:blog:";
}
