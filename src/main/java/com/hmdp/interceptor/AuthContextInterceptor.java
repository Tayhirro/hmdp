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
 * 注册方式：由 AuthMvcConfig 注册（order=0，拦截器链第一位），拦截所有路径（/**）；
 * 仅当配置 hmdp.auth.enabled=true 时该拦截器才被注册生效。
 *
 * 典型流程：
 * - preHandle: AuthResolver.resolve(...) -> UserHolder.saveUser(...)
 * - afterCompletion: UserHolder.removeUser()
 */
public class AuthContextInterceptor implements HandlerInterceptor {
    private final AuthResolver authResolver;

    /**
     * 构造函数：注入认证解析器（由 AuthMvcConfig 在注册拦截器时创建实例并传入）。
     */
    public AuthContextInterceptor(AuthResolver authResolver) {
        this.authResolver = authResolver;
    }

    /**
     * 请求进入 Controller 前，解析当前登录用户并写入 ThreadLocal。
     * 执行时机：拦截所有路径，在拦截器链第一位（order=0）执行，必须先于 LoginRequiredInterceptor；
     * 每个 HTTP 请求进入处理器前由 Spring MVC 调用一次。
     * 使用场景：为本次请求建立用户上下文——已登录时通过 {@link AuthResolver}（从 HTTP 请求解析登录用户的认证接口，
     * 支持 Session/JWT 组合实现）取得 {@link UserDTO}（用户脱敏信息 DTO）存入 {@link UserHolder}（ThreadLocal 用户上下文）；
     * 未登录时不写入但仍然放行（始终返回 true），是否强制登录由后续拦截器决定。
     * 数据库/Redis：本方法自身不查询；凭据校验与还原由具体 AuthResolver 实现决定（如从 Redis 登录 Hash 还原用户）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO user = authResolver.resolve(request);
        // userHolder
        if (user != null) {
            UserHolder.saveUser(user);
        }
        return true;
    }
    /**
     * 请求完全结束后清理 ThreadLocal 中的用户上下文。
     * 执行时机：每个被拦截请求的 afterCompletion 回调，无论 Controller 正常返回还是抛出异常都会执行。
     * 使用场景：Tomcat 工作线程会被复用，必须在请求末尾移除用户，避免下一个请求读到上一个用户的身份（串号）或造成内存泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

