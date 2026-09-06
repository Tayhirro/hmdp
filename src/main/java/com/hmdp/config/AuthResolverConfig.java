package com.hmdp.config;

import com.hmdp.auth.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

/**
 * 认证解析器 {@link com.hmdp.auth.AuthResolver}（认证解析器接口）各实现的 Bean 装配工厂。
 * 组合顺序由配置项 hmdp.auth.method 控制（默认 auto：jwt 到 redis-token 到 session）。
 */
@Configuration
public class AuthResolverConfig {

    /**
     * 创建 JWT 解析器。
     *
     * 使用场景：Spring 启动时被容器调用，产物供 {@link #compositeAuthResolver} 组装。
     * 注意：当前硬编码传入 null 密钥（配置项 hmdp.auth.jwt.secret 未接入，默认空），
     * 密钥为空时 {@link JwtAuthResolver}（JWT 解析器）的 resolve 恒返回 null，即 JWT 认证实际不生效。
     *
     * @return 未配置密钥的 {@link JwtAuthResolver}（占位状态）
     */
    @Bean
    public JwtAuthResolver jwtAuthResolver() {
        return new JwtAuthResolver(null);
    }

    /**
     * 创建基于 HttpSession（属性名 "user"）的解析器。
     *
     * 使用场景：Spring 启动时被容器调用；配合 Spring Session + Redis 时会话数据存 Redis。
     *
     * @return {@link RedisSessionAuthResolver} 实例
     */
    @Bean
    public RedisSessionAuthResolver redisSessionAuthResolver() {
        return new RedisSessionAuthResolver();
    }

    /**
     * 创建基于 Redis token（key = "login:token:" + token）的解析器。
     *
     * 使用场景：Spring 启动时被容器调用；这是 application.yaml 当前配置
     * hmdp.auth.method=redis-token 下实际生效的解析器。
     *
     * @param stringRedisTemplate 操作 Redis 登录 Hash 的模板
     * @return {@link RedisTokenAuthResolver} 实例
     */
    @Bean
    public RedisTokenAuthResolver redisTokenAuthResolver(StringRedisTemplate stringRedisTemplate) {
        return new RedisTokenAuthResolver(stringRedisTemplate);
    }

    /**
     * 按配置项 hmdp.auth.method（默认 "auto"）把三个解析器组装成组合解析器。
     *
     * 使用场景：Spring 启动时被容器调用；产物注册进 {@link AuthMvcConfig}（Web 拦截器注册配置）的拦截器链。
     * 取值规则见 {@link #buildDelegates}：auto/default 为 jwt、redis-token、session
     * 优先级从高到低，也可逗号分隔自定义顺序（如 jwt,redis-token）；
     * 含未知取值或解析结果为空时启动即抛 {@link IllegalArgumentException}。
     *
     * @param jwtAuthResolver JWT 解析器
     * @param redisTokenAuthResolver Redis token 解析器
     * @param redisSessionAuthResolver Session 解析器
     * @param method 配置项 hmdp.auth.method 的值，默认 "auto"
     * @return 组合后的 {@link CompositeAuthResolver}
     */
    @Bean
    public CompositeAuthResolver compositeAuthResolver(
            JwtAuthResolver jwtAuthResolver,
            RedisTokenAuthResolver redisTokenAuthResolver,
            RedisSessionAuthResolver redisSessionAuthResolver,
            @Value("${hmdp.auth.method:auto}") String method
    ) {
        return new CompositeAuthResolver(buildDelegates(method, jwtAuthResolver, redisTokenAuthResolver, redisSessionAuthResolver));
    }

    /**
     * 把 method 配置字符串解析为去重后的解析器优先级列表。
     *
     * 使用场景：仅被 {@link #compositeAuthResolver} 在启动装配时调用。
     * 规则：null 或空白，或等于 auto/default（忽略大小写）时，返回固定顺序
     * jwt、redis-token、session；否则按逗号拆分，每个 token 经
     * {@link #normalizeMethodToken} 归一化后映射到 JWT、REDIS_TOKEN、SESSION 三类，
     * 重复取值跳过；遇到未知取值抛 IllegalArgumentException，最终列表为空也抛
     * IllegalArgumentException。
     *
     * @param method 配置原始字符串
     * @param jwtAuthResolver JWT 解析器
     * @param redisTokenAuthResolver Redis token 解析器
     * @param redisSessionAuthResolver Session 解析器
     * @return 按优先级排列且已去重的解析器列表
     */
    private static List<AuthResolver> buildDelegates(
            String method,
            JwtAuthResolver jwtAuthResolver,
            RedisTokenAuthResolver redisTokenAuthResolver,
            RedisSessionAuthResolver redisSessionAuthResolver
    ) {
        if (method == null) {
            method = "";
        }
        String trimmed = method.trim();
        
        // 如果为 auto / default --- 则使用默认的三者优先级
        if (trimmed.isEmpty() || "auto".equalsIgnoreCase(trimmed) || "default".equalsIgnoreCase(trimmed)) {
            return Arrays.asList(jwtAuthResolver, redisTokenAuthResolver, redisSessionAuthResolver);
        }

        ArrayList<AuthResolver> delegates = new ArrayList<>(3);
        boolean hasJwt = false;
        boolean hasRedisToken = false;
        boolean hasSession = false;

        StringTokenizer tokenizer = new StringTokenizer(trimmed, ",");
        while (tokenizer.hasMoreTokens()) {
            String raw = tokenizer.nextToken().trim();
            if (raw.isEmpty()) {
                continue;
            }//跳过空格
            switch (normalizeMethodToken(raw)) {
                case "JWT":
                    if (!hasJwt) {
                        delegates.add(jwtAuthResolver);
                        hasJwt = true;
                    }
                    break;
                case "REDIS_TOKEN":
                    if (!hasRedisToken) {
                        delegates.add(redisTokenAuthResolver);
                        hasRedisToken = true;
                    }
                    break;
                case "SESSION":
                    if (!hasSession) {
                        delegates.add(redisSessionAuthResolver);
                        hasSession = true;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown hmdp.auth.method: " + raw + " (expected auto, jwt, redis-token, session, or a comma-separated priority list)");
            }
        }

        if (delegates.isEmpty()) {
            throw new IllegalArgumentException("hmdp.auth.method is blank (expected auto, jwt, redis-token, session, or a comma-separated priority list)");
        }
        return delegates;
    }

    /**
     * 把配置 token 归一化为大写下划线形式，并兼容常见别名。
     *
     * 使用场景：仅被 {@link #buildDelegates} 逐个 token 调用。
     * 规则：去首尾空白，'-' 与空格替换为 '_'，统一转大写；
     * 别名映射：REDISTOKEN 映射为 REDIS_TOKEN，REDISSESSION 映射为 SESSION，JWT 原样。
     *
     * @param token 单个配置 token（如 "redis-token"）
     * @return 归一化后的取值（JWT、REDIS_TOKEN、SESSION 或原样大写）
     */
    // 统一大写化
    private static String normalizeMethodToken(String token) {
        String t = token.trim();
        t = t.replace('-', '_');
        t = t.replace(' ', '_');
        t = t.toUpperCase(Locale.ROOT);
        if ("REDISTOKEN".equals(t)) {
            return "REDIS_TOKEN";
        }
        if ("REDISSESSION".equals(t)) {
            return "SESSION";
        }
        if ("JWT".equals(t)) {
            return "JWT";
        }
        return t;
    }
}