package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

/**
 * 把各处抛出的异常统一转换成前端能理解的 HTTP 错误响应。
 *     1. HTTP 状态码说明错误大类：例如 400 是请求参数有误、401 是未登录、
 *     403 是没有权限、404 是数据不存在、500 是服务器内部异常。
 *     2. errorCode 供程序判断：前端可以稳定判断 {@code BLOG_NOT_FOUND} 等错误；
 *     errorMsg 只负责显示中文提示，文案变化不会破坏前端逻辑。
 *     3. traceId 用于排查问题：同一个编号同时写入响应和服务端日志。
 *     用户报告错误编号后，开发者可以找到这一次请求对应的日志。
 *     4. 未知异常不返回内部细节：前端只收到通用 500，完整异常堆栈只写服务端日志，
 *     防止把 SQL、文件路径或代码结构泄露出去。
 * 
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 处理业务异常，按异常自带的状态码与错误码返回。
     *
     * 使用场景：任意 Controller/Service 抛出 {@link BusinessException}（业务异常）时由 Spring MVC
     * 自动路由到本方法；HTTP 状态码、errorCode（如 BLOG_NOT_FOUND）、errorMsg 均取自异常本身。
     *
     * @param e 捕获到的业务异常
     * @return 携带 {@link Result}（统一响应体）错误体的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    /**
     * 处理三类参数绑定错误，统一返回 400。
     *
     * 使用场景：Spring MVC 在解析请求体、绑定参数失败时抛出
     * HttpMessageNotReadableException（JSON 不可读）、MissingServletRequestParameterException
     * （缺必填参数）、MethodArgumentTypeMismatchException（参数类型不匹配）时进入本方法；
     * 返回 400 INVALID_REQUEST「请求参数格式错误」。
     *
     * @param e 三者之一的参数绑定异常
     * @return 400 状态码与 {@link Result} 错误体
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Result> handleBadRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数格式错误");
    }

    /**
     * 处理 HTTP 方法不被支持，返回 405。
     *
     * 使用场景：请求的 URL 存在但 HTTP 方法与任何 Controller 方法不匹配时，
     * Spring MVC 抛出 HttpRequestMethodNotSupportedException 进入本方法；
     * 返回 405 METHOD_NOT_ALLOWED「HTTP 方法不受支持」。
     *
     * @param e 方法不匹配异常
     * @return 405 状态码与 {@link Result} 错误体
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP 方法不受支持");
    }

    /**
     * 处理上传文件超出大小限制，返回 413。
     *
     * 使用场景：上传请求超过 spring.servlet.multipart.max-file-size（application.yaml
     * 当前配置 5MB）时，Spring 抛出 MaxUploadSizeExceededException 进入本方法；
     * 返回 413 PAYLOAD_TOO_LARGE「上传文件超出大小限制」。
     *
     * @param e 上传超限异常
     * @return 413 状态码与 {@link Result} 错误体
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "上传文件超出大小限制");
    }

    /**
     * 兜底处理所有未显式捕获的 RuntimeException，返回 500。
     *
     * 使用场景：任何未被前面 handler 覆盖的运行时异常冒泡到 Spring MVC 时进入本方法；
     * 完整堆栈用 log.error 写服务端日志（便于结合 traceId 排查），前端只收到
     * 500 INTERNAL_ERROR「服务器异常」，不泄露 SQL、路径等内部细节。
     *
     * @param e 未预期的运行时异常
     * @return 500 状态码与 {@link Result} 错误体
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result> handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器异常");
    }

    /**
     * 统一组装错误响应体。
     *
     * 使用场景：本类所有 @ExceptionHandler 方法的出口，内部调用；
     * 生成 Result.fail(code, message, traceId) 并以指定 HTTP 状态码返回。
     *
     * @param status HTTP 状态码（错误大类）
     * @param code 面向程序的稳定错误码（如 INVALID_REQUEST）
     * @param message 面向用户的中文提示
     * @return 指定状态码的 {@link Result} 错误响应
     */
    private ResponseEntity<Result> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Result.fail(code, message, currentTraceId()));
    }

    /**
     * 读取当前请求的 traceId，缺失时现场生成兜底。
     *
     * 使用场景：仅被 {@link #error} 调用；正常路径下取的是 {@link TraceIdFilter}（链路追踪过滤器）
     * 写入 MDC 的 "traceId"（与响应头 X-Trace-Id、服务端日志一致），
     * MDC 为空（如非 HTTP 请求线程触发）时生成 32 位去连字符 UUID，保证返回体始终带 traceId。
     *
     * @return 当前 traceId；无则随机生成
     */
    private String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }
}
