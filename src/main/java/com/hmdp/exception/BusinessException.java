package com.hmdp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 携带 HTTP 状态码与机器可读错误码的业务异常。
 *
 * 使用场景：由各 Service/Controller 在参数不合法、未登录、无权限或数据不存在等业务场景抛出，
 * 统一由 WebExceptionAdvice（全局异常处理器）的 @ExceptionHandler(BusinessException.class) 捕获，
 * 转换成“HTTP 状态码 + errorCode + errorMsg + traceId”的 JSON 失败响应，业务代码无需自行拼装错误响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 以 400（BUSINESS_ERROR）创建仅含用户可读提示的业务异常。
     * 使用场景：服务层只需说明错误原因、无专属错误码时抛出（如“图片ID不能为空”）。
     */
    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", message, null);
    }

    /**
     * 以 400（BUSINESS_ERROR）创建带根因异常的业务异常。
     * 使用场景：捕获底层异常（如 SQL、文件存储失败）后，向上传递用户可读信息同时保留原始堆栈时抛出。
     */
    public BusinessException(String message, Throwable cause) {
        this(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", message, cause);
    }

    /**
     * 以指定 HTTP 状态码和错误码创建业务异常。
     * 使用场景：需要前端按稳定 errorCode 做分支处理（如 BLOG_NOT_FOUND）时抛出。
     */
    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    /**
     * 全参构造：以指定状态码、错误码、消息和根因创建业务异常；status 或 code 为 null 时分别回退为 400 和 BUSINESS_ERROR。
     * 使用场景：主要供其他构造器和静态工厂复用，业务代码一般不直接调用。
     */
    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
        this.code = code == null ? "BUSINESS_ERROR" : code;
    }

    /**
     * 创建 400 状态、自定义错误码的参数错误异常。
     * 使用场景：服务层入参校验失败（字段缺失、超长、分页越界、游标无效等）时抛出。
     */
    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * 创建 401、错误码 AUTH_REQUIRED 的未登录异常。
     * 使用场景：从登录上下文（UserHolder）取不到当前用户，却访问需要登录的能力时抛出。
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", message);
    }

    /**
     * 创建 403、错误码 FORBIDDEN 的无权限异常。
     * 使用场景：当前用户不是资源所有者（如删除他人评论、编辑他人博客）时抛出。
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /**
     * 创建 404 状态、自定义错误码的资源不存在异常。
     * 使用场景：按 id 查不到博客、评论、店铺等数据时抛出。
     */
    public static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    /**
     * 创建 409 状态、自定义错误码的冲突异常。
     * 使用场景：请求与数据当前状态冲突（如重复创建、状态机不允许的变更）时抛出。
     */
    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
