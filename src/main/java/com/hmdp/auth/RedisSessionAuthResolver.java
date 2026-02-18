package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Redis Session 认证解析器（模板）。
 *
 * 说明：
 * - “Redis Session”一般指 Spring Session + Redis：把 HttpSession 的数据存到 Redis
 * - 业务代码的读取方式仍然是 request.getSession().getAttribute(...)
 *
 */
public class RedisSessionAuthResolver implements AuthResolver {

    private static final String SESSION_USER_KEY = "user";

    @Override
    @Nullable
    public UserDTO resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER_KEY);
        if (value == null) {
            return null;
        }
        if (value instanceof UserDTO) {
            return (UserDTO) value;
        }
        return null;
    }
}
