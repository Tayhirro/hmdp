package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;
import javax.servlet.http.HttpServletRequest;

/**
 * 认证信息解析器（模板）。
 *
 * 作用：从一次 HTTP 请求中解析出当前登录用户（如果有的话）。
 * - 返回 null：表示本次请求未登录/无法识别
 * - 返回 UserDTO：表示已登录
 */
public interface AuthResolver {
    /**
     * 从一次 HTTP 请求中解析出当前登录用户。
     *
     * 使用场景：每次 HTTP 请求由 {@link com.hmdp.interceptor.AuthContextInterceptor}（认证上下文拦截器）的 preHandle
     * 调用（通常经 {@link CompositeAuthResolver}（组合解析器）按优先级分发，需 hmdp.auth.enabled=true 才会注册），
     * 解析出的用户随后写入 {@link com.hmdp.utils.UserHolder}（ThreadLocal 用户上下文）供业务层随取随用。
     *
     * @param request 当前 HTTP 请求
     * @return 已登录返回 {@link UserDTO}；未登录或无法识别返回 null，调用方必须兼容 null
     */
    @Nullable
    UserDTO resolve(HttpServletRequest request);    
}
