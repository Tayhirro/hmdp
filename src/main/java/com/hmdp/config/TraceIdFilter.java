package com.hmdp.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/** 为每个请求建立可回传、可检索的 traceId，且请求结束后清理线程上下文。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 为每个请求确定 traceId 并贯穿请求生命周期。
     *
     * 使用场景：每个 HTTP 请求经过滤器链时执行一次（继承 {@link OncePerRequestFilter}，
     * 每请求最多一次），须在业务拦截器之前运行以便日志都带上 traceId。
     * 关键操作：优先复用请求头 X-Trace-Id（须匹配正则 [A-Za-z0-9_-]{8,64}，即 8~64 位
     * 字母/数字/下划线/连字符，否则视为非法），非法或缺失时生成 32 位去连字符 UUID；
     * 随后写入 MDC（key 为 "traceId"，供日志模板输出），并回写响应头 X-Trace-Id 便于前端回传排查；
     * finally 中 MDC.remove("traceId") 清理，防止线程池复用导致串号。
     *
     * @param request 当前 HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 后续过滤器与业务处理链
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || !traceId.matches("[A-Za-z0-9_-]{8,64}")) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
