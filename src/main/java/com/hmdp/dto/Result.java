package com.hmdp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTTP 接口的统一业务响应外壳。
 *
 * 类别：所有 Controller 对外返回的通用响应 DTO。
 * 成功响应使用 {@link #data} 携带结果；失败响应使用稳定的 {@link #errorCode}、
 * 可读的 {@link #errorMsg} 和便于排查的 {@link #traceId}。
 * 注意：{@link #success} 描述业务结果，不能替代 HTTP 状态码。未认证、无权限、参数错误等
 * 仍应由控制器或全局异常处理器返回正确的 401、403、400 等状态。
 */
@Data
@NoArgsConstructor
public class Result {

    /** 业务是否成功。 */
    private Boolean success;

    /** 面向调用方的错误说明；成功时为空。 */
    private String errorMsg;

    /** 供前端稳定判断错误类型的机器可读错误码；成功时为空。 */
    private String errorCode;

    /** 单次请求的追踪标识，用于服务端日志定位；成功或旧式失败响应中可能为空。 */
    private String traceId;

    /** 实际业务数据，不同接口对应不同 DTO。 */
    private Object data;

    /** 旧式页码分页的总记录数；非该类分页响应时为空。 */
    private Long total;

    /** 统一由静态工厂创建响应，避免调用方漏设成功标志。 */
    private Result(Boolean success, String errorMsg, Object data, Long total) {
        this.success = success;
        this.errorMsg = errorMsg;
        this.data = data;
        this.total = total;
    }

    /** 创建不携带业务数据的成功响应。 */
    public static Result ok(){
        return new Result(true, null, null, null);
    }

    /** 创建携带单个对象或自定义响应 DTO 的成功响应。 */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }

    /** 创建旧式页码分页成功响应；新游标分页应把 {@link CursorPageDTO} 放入 data。 */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }

    /** 创建只带可读提示的兼容失败响应。 */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }

    /** 创建包含稳定错误码和追踪 ID 的标准失败响应。 */
    public static Result fail(String errorCode, String errorMsg, String traceId) {
        Result result = fail(errorMsg);
        result.setErrorCode(errorCode);
        result.setTraceId(traceId);
        return result;
    }
}
