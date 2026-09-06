package com.hmdp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录校验拦截器（模板）。
 *
 * 目标：对“必须登录”的接口进行拦截。
 * 注册方式：由 AuthMvcConfig 注册（order=1），采用“拦截全部路径 + 排除白名单”的方式，
 * 白名单为 /user/code、/user/login、/user/signup、/user/bind-phone、/search、/search/**、/shop/**、
 * /shop-type/**、/voucher/**、/blog/hot；其余路径（发布/编辑博客、点赞、评论、关注、上传图片、签到等）均要求登录。
 * 仅当配置 hmdp.auth.enabled=true 时该拦截器才被注册生效。
 * 前置条件：AuthContextInterceptor（order=0）已经先执行，把用户放到了 UserHolder。
 */
public class LoginRequiredInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    /**
     * 构造函数：注入 JSON 序列化器，用于拼装 401 响应体（由 AuthMvcConfig 在注册拦截器时创建实例并传入）。
     */
    public LoginRequiredInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 请求进入 Controller 前校验登录态，未登录直接终止请求。
     * 执行时机：拦截除白名单外的所有路径（见类注释），在认证上下文拦截器（order=0）之后、
     * 博客限流拦截器（order=2）之前执行；每个 HTTP 请求进入处理器前由 Spring MVC 调用一次。
     * 使用场景：保护需要登录的写操作与个人数据接口——从 {@link UserHolder}（ThreadLocal 用户上下文）
     * 取不到用户时，直接写回 401 JSON 响应（错误码 AUTH_REQUIRED，附带 traceId）并返回 false，Controller 不会执行。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail("AUTH_REQUIRED", "请先登录", MDC.get("traceId"))));
            return false;
        }
        return true;
    }
}

