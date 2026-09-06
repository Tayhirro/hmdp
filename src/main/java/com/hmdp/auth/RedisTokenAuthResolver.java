package com.hmdp.auth;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Redis + Token 认证解析器（模板，可直接用）。
 *
 * 约定：
 * - 客户端在请求头携带 token（例如 header: authorization）
 * - 服务端在 Redis 中存储：key = login:token:{token}，value = UserDTO 的 Hash
 */
public class RedisTokenAuthResolver implements AuthResolver {
    private static final Logger log = LoggerFactory.getLogger(RedisTokenAuthResolver.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 注入 Redis 操作模板。
     *
     * 使用场景：Spring 启动时由 {@link com.hmdp.config.AuthResolverConfig}（认证解析器装配配置类）的
     * redisTokenAuthResolver 方法创建本 bean 时调用。
     *
     * @param stringRedisTemplate Spring Boot 自动配置的 StringRedisTemplate
     */
    public RedisTokenAuthResolver(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 用请求头中的 token 查 Redis，还原当前登录用户并续期。
     *
     * 使用场景：每次 HTTP 请求由 {@link CompositeAuthResolver}（组合解析器）调用；
     * application.yaml 当前配置 hmdp.auth.method=redis-token，即线上实际只走本解析器。
     * token 的写入方是 UserServiceImpl 登录成功时（key 同下）。
     * 流程：取 header authorization，剥离可选 "Bearer " 前缀（忽略大小写）；
     * 含 '.' 的 token 视为 JWT 直接放弃；查 Redis Hash，key = "login:token:" + token
     * （常量 LOGIN_USER_KEY 拼接 token），无数据返回 null；命中则用 Hutool
     * BeanUtil.fillBeanWithMap 还原 {@link UserDTO}（用户 DTO），并把该 key 的 TTL 重置为
     * LOGIN_USER_TTL = 36000 秒（10 小时，实现活跃用户续期）。
     *
     * @param request 当前 HTTP 请求
     * @return Redis 中已登录的 {@link UserDTO}；未登录或 token 失效返回 null
     */
    @Override
    @Nullable
    public UserDTO resolve(HttpServletRequest request) {
        // 查询 authorization 字段是否带有token
        String authorization = request.getHeader("authorization");
        if (StrUtil.isBlank(authorization)) {
            log.debug("[auth] missing authorization header, uri={}", request.getRequestURI());
            return null;
        }
        // 兼容 Bearer 前缀（如果你 Redis token 不带 Bearer，可按需删除这段）
        String token = authorization.trim();
        if (StrUtil.startWithIgnoreCase(token, "Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (StrUtil.isBlank(token)) {
            log.debug("[auth] empty token after trim/bearer, uri={}", request.getRequestURI());
            return null;
        }
        // 避免把 JWT 当成 Redis token（JWT 通常包含两个 '.'）
        if (token.indexOf('.') >= 0) {
            log.debug("[auth] token contains '.', treat as non-redis-token, uri={}", request.getRequestURI());
            return null;
        }
        String key = LOGIN_USER_KEY + token; 
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap == null || userMap.isEmpty()) {
            log.debug("[auth] redis token not found, key={}, uri={}", key, request.getRequestURI());
            return null;
        }
        log.debug("[auth] redis token resolved, key={}, fields={}", key, userMap.size());
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 刷新 TTL（按你项目的单位调整；这里按秒刷新）
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return userDTO;
    }
}
