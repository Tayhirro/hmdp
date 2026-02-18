package com.hmdp.interceptor;

import com.hmdp.auth.AuthResolver;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 认证上下文拦截器（模板）。
 *
 * 目标：在一次请求的生命周期内，把“当前登录用户”写入 UserHolder(ThreadLocal)，便于业务层随取随用。
 *
 * 典型流程：
 * - preHandle: AuthResolver.resolve(...) -> UserHolder.saveUser(...)
 * - afterCompletion: UserHolder.removeUser()
 */
public class AuthContextInterceptor implements HandlerInterceptor {
    private final AuthResolver authResolver;

    public AuthContextInterceptor(AuthResolver authResolver) {
        this.authResolver = authResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO user = authResolver.resolve(request);
        // userHolder
        if (user != null) {
            UserHolder.saveUser(user);
        }
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

