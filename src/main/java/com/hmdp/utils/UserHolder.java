package com.hmdp.utils;

import com.hmdp.dto.UserDTO;



/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * 存：{@link com.hmdp.interceptor.AuthContextInterceptor}（认证上下文拦截器）preHandle；
 * 取：Controller/Service 业务代码；清：同拦截器 afterCompletion，防止线程池串号。
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    /**
     * 把当前请求的登录用户绑定到当前线程。
     *
     * 使用场景：每次 HTTP 请求由 {@link com.hmdp.interceptor.AuthContextInterceptor} 的
     * preHandle 在解析成功后调用（user 非 null 才存），请求线程内后续随取随用。
     *
     * @param user 当前登录用户（解析器返回 null 时不会被调用）
     */
    public static void saveUser(UserDTO user){
        tl.set(user);
    }
    /**
     * 读取当前线程绑定的登录用户。
     *
     * 使用场景：业务代码在请求线程内调用，如 UserController.me()、
     * BlogQueryService、FollowServiceImpl、BlogRateLimitInterceptor 等；
     * 未登录或未被拦截器写入时返回 null，调用方需自行判空。
     *
     * @return 当前登录用户；无则 null
     */
    public static UserDTO getUser(){
        return tl.get();
    }
    /**
     * 清除当前线程绑定的用户，防止线程复用读到上一个请求的用户。
     *
     * 使用场景：每次 HTTP 请求由 {@link com.hmdp.interceptor.AuthContextInterceptor} 的
     * afterCompletion 在请求结束时调用（无论成功、异常都执行）。
     */
    public static void removeUser(){
        tl.remove();
    }
}
