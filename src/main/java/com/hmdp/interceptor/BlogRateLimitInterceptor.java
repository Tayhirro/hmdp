package com.hmdp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

/**
 * 限制博客接口在一分钟内可以被调用的次数，防止连续点击、脚本刷接口或突发流量压垮服务。
 *     1. 分别计数：Redis key 同时包含“用户（未登录时用 IP）+ 操作类型”。
 *     某个用户频繁点赞，不会消耗其他用户或发布接口的次数。
 *     2. 计数和设置 60 秒有效期一次完成：Lua 脚本让 Redis 把这两个动作作为一个整体执行，
 *     避免并发时只加了次数却漏设过期时间，留下永远不会清除的计数。
 *     3. Redis 故障时暂时放行：这里的限流只负责保护服务器容量，不负责保证业务数据正确。
 *     即使暂时放行，数据库唯一约束和事务仍会阻止重复点赞、重复关注等脏数据。
 *     4. 注册方式：由 AuthMvcConfig 注册（order=2），只拦截 /blog 与 /blog/**，
 *     在认证上下文（order=0）和登录校验（order=1）之后执行，因此计数主体可直接取 ThreadLocal 中的登录用户。
 *
 */
@Slf4j
@Component
public class BlogRateLimitInterceptor implements HandlerInterceptor {

    private static final int WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); " +
                    "if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n;",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数：注入 Redis 模板与 JSON 序列化器（由 Spring 在创建该组件时调用一次）。
     */
    public BlogRateLimitInterceptor(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 在请求进入 Controller 前执行博客接口限流判定。
     * 执行时机：拦截 /blog 与 /blog/**（order=2），在认证上下文（order=0）、登录校验（order=1）之后，
     * 每个 HTTP 请求进入处理器前由 Spring MVC 调用一次。
     * 使用场景：先按“HTTP 方法 + 路径”解析限流规则，未命中规则直接放行；命中后以
     * “登录用户 ID（未登录时用请求 IP）+ 规则名”为维度计数。
     * Redis：对 key（rate:blog:{规则名}:{u:用户ID 或 ip:地址}）执行 Lua 脚本 INCR 并在首次计数时 EXPIRE 60 秒；
     * 计数超过规则阈值时直接写回 429 响应（Retry-After: 60，JSON 错误码 RATE_LIMITED）并返回 false；
     * Redis 异常时降级放行，由数据库唯一约束和事务兜底。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Rule rule = resolveRule(request);
        if (rule == null) {
            return true;
        }
        String subject = UserHolder.getUser() != null && UserHolder.getUser().getId() != null
                ? "u:" + UserHolder.getUser().getId()
                : "ip:" + request.getRemoteAddr();
        String key = RedisConstants.BLOG_RATE_LIMIT_KEY + rule.name + ":" + subject;
        try {
            Long count = stringRedisTemplate.execute(
                    LIMIT_SCRIPT, Collections.singletonList(key), String.valueOf(WINDOW_SECONDS));
            if (count == null || count <= rule.limit) {
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("博客接口限流不可用，本次降级放行，key={}", key, e);
            return true;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail("RATE_LIMITED", "请求过于频繁，请稍后重试", MDC.get("traceId"))));
        return false;
    }

    /**
     * 按“HTTP 方法 + 请求路径”解析适用的限流规则；未匹配任何规则时返回 null，表示不限流。
     * 使用场景：preHandle 对每个被拦截请求调用一次。规则与阈值（均按 60 秒窗口计数）：
     * POST /blog（发布博客）10 次；PUT 或 DELETE /blog/{数字id}/like（点赞/取消点赞）60 次；
     * PUT 或 DELETE /blog/{数字id}（编辑/删除博客）20 次；GET /blog/feed（拉取 Feed）120 次。
     */
    private Rule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(method) && "/blog".equals(path)) {
            return new Rule("publish", 10);
        }
        if (("PUT".equals(method) || "DELETE".equals(method)) && path.matches("/blog/\\d+/like")) {
            return new Rule("like", 60);
        }
        if (("PUT".equals(method) || "DELETE".equals(method)) && path.matches("/blog/\\d+")) {
            return new Rule("write", 20);
        }
        if ("GET".equals(method) && "/blog/feed".equals(path)) {
            return new Rule("feed", 120);
        }
        return null;
    }

    @AllArgsConstructor
    private static class Rule {
        private final String name;
        private final long limit;
    }
}
