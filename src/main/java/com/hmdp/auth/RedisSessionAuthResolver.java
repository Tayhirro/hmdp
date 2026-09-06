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

    /** Session 中存放登录用户的属性名，登录逻辑需以 {@code session.setAttribute("user", ...)} 写入。 */
    private static final String SESSION_USER_KEY = "user";

    /**
     * 从 HttpSession 的 "user" 属性中取当前登录用户。
     *
     * 使用场景：每次 HTTP 请求由 {@link CompositeAuthResolver}（组合解析器）调用；配合 Spring Session + Redis
     * 时 session 数据实际存 Redis。使用 request.getSession(false)（不创建新会话），
     * 会话不存在、属性缺失或类型不是 {@link UserDTO}（用户 DTO）时都返回 null。
     * 注意：当前工程登录流程写的是 Redis token（见 UserServiceImpl），尚无写入该 Session 属性的代码，
     * 本解析器属模板预留。
     *
     * @param request 当前 HTTP 请求
     * @return Session 中已登录的 {@link UserDTO}；否则返回 null
     */
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
