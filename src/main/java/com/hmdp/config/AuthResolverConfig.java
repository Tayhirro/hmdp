package com.hmdp.config;

import com.hmdp.auth.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

@Configuration
public class AuthResolverConfig {

    @Bean
    public JwtAuthResolver jwtAuthResolver() {
        return new JwtAuthResolver(null);
    }

    @Bean
    public RedisSessionAuthResolver redisSessionAuthResolver() {
        return new RedisSessionAuthResolver();
    }

    @Bean
    public RedisTokenAuthResolver redisTokenAuthResolver(StringRedisTemplate stringRedisTemplate) {
        return new RedisTokenAuthResolver(stringRedisTemplate);
    }

    @Bean
    public CompositeAuthResolver compositeAuthResolver(
            JwtAuthResolver jwtAuthResolver,
            RedisTokenAuthResolver redisTokenAuthResolver,
            RedisSessionAuthResolver redisSessionAuthResolver,
            @Value("${hmdp.auth.method:auto}") String method
    ) {
        return new CompositeAuthResolver(buildDelegates(method, jwtAuthResolver, redisTokenAuthResolver, redisSessionAuthResolver));
    }

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