package com.hmdp.config;

import com.hmdp.auth.CompositeAuthResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.interceptor.AuthContextInterceptor;
import com.hmdp.interceptor.BlogRateLimitInterceptor;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BlogRateLimitInterceptor blogRateLimitInterceptor;

    /**
     * 注册三个拦截器，构成认证与限流链。
     *
     * 使用场景：Spring 启动时由 Spring MVC 调用一次完成拦截器注册；
     * 本配置类仅在 hmdp.auth.enabled=true（application.yaml 当前为 true）时加载。
     * 注册内容：
     * 1. order=0，{@link AuthContextInterceptor}（认证上下文拦截器，新建并包装
     *    {@link CompositeAuthResolver}（组合解析器）），拦截 /**，每请求解析用户并写入
     *    {@link com.hmdp.utils.UserHolder}（ThreadLocal 用户上下文）；
     * 2. order=1，{@link LoginRequiredInterceptor}（登录校验拦截器），对未排除路径做登录校验，排除清单：
     *    /user/code、/user/login、/user/signup、/user/bind-phone、/search、/search/**、
     *    /shop/**、/shop-type/**、/voucher/**、/blog/hot；
     * 3. order=2，{@link BlogRateLimitInterceptor}（博客限流拦截器，容器注入的 bean），拦截 /blog/** 与 /blog 做限流。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 认证上下文：所有请求都尝试解析用户，并写入 ThreadLocal
        registry.addInterceptor(new AuthContextInterceptor(authResolver))
                .addPathPatterns("/**")
                .order(0);
        // 2) 登录校验：只拦截"需要登录"的接口（你按项目需要调整排除列表）
        registry.addInterceptor(new LoginRequiredInterceptor(objectMapper))
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/user/signup",
                        "/user/bind-phone",
                        "/search",
                        "/search/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/blog/hot"
                )
                .order(1);
        registry.addInterceptor(blogRateLimitInterceptor)
                .addPathPatterns("/blog/**", "/blog")
                .order(2);
    }
}
