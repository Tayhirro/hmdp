package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 组合解析器：允许多种认证方式“并存”。
 *
 * 示例：先尝试 JWT，再尝试 Session（或反过来）。
 * 你可以在构造时传入 resolver 列表来控制顺序。
 */
public class CompositeAuthResolver implements AuthResolver {

    private final List<AuthResolver> delegates;

    /**
     * 按传入顺序组装多种认证方式，越靠前优先级越高。
     *
     * 使用场景：Spring 启动时由 {@link com.hmdp.config.AuthResolverConfig}（认证解析器装配配置类）的
     * compositeAuthResolver 方法创建，顺序由配置项 hmdp.auth.method 决定（默认 auto：jwt 到 redis-token 到 session）。
     *
     * @param delegates 委托的解析器列表，按优先级从高到低排列
     */
    public CompositeAuthResolver(List<AuthResolver> delegates) {
        this.delegates = delegates;
    }

    /**
     * 依次尝试各委托解析器，返回第一个非 null 的解析结果。
     *
     * 使用场景：每次 HTTP 请求由 {@link com.hmdp.interceptor.AuthContextInterceptor}（认证上下文拦截器）间接调用；
     * 全部委托都返回 null 时本方法才返回 null，表示本次请求未登录。
     *
     * @param request 当前 HTTP 请求
     * @return 第一个解析成功的用户；全部失败返回 null
     */
    @Override
    @Nullable
    public UserDTO resolve(HttpServletRequest request) {
        for (AuthResolver delegate : delegates) {
            UserDTO user = delegate.resolve(request);
            if (user != null) {
                return user;
            }
        }
        return null;
    }
}
