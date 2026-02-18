package com.hmdp.config;

import com.hmdp.auth.CompositeAuthResolver;
import com.hmdp.interceptor.AuthContextInterceptor;
import com.hmdp.interceptor.LoginRequiredInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 拦截器注册（模板：支持 Session + JWT 并存）。
 *
 * 默认不启用，避免你还没补全 JWT/Session 写入逻辑就影响运行。
 * 启用方式：在 application.yaml 里加：
 *   hmdp:
 *     auth:
 *       enabled: true
 */

@Configuration
@ConditionalOnProperty(prefix = "hmdp.auth", name = "enabled", havingValue = "true")
public class AuthMvcConfig implements WebMvcConfigurer {

    // 延迟注入复合的认证解析器，避免循环依赖
    @Autowired
    private CompositeAuthResolver authResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 认证上下文：所有请求都尝试解析用户，并写入 ThreadLocal
        registry.addInterceptor(new AuthContextInterceptor(authResolver))
                .addPathPatterns("/**")
                .order(0);
        // 2) 登录校验：只拦截"需要登录"的接口（你按项目需要调整排除列表）
        registry.addInterceptor(new LoginRequiredInterceptor())
.excludePathPatterns(  
                        "/user/code",
                        "/user/login",
                        "/user/signup",
                        "/user/bind-phone",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/blog/hot",
                        "/upload/**"
                )
                .order(1);
    }
}
