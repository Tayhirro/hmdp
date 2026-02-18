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
    public RedisTokenAuthResolver(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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
