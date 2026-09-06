package com.hmdp.dto;

import lombok.Data;

/**
 * 登录、注册和绑定手机号共用的认证请求参数。
 *
 * 类别：前端请求 DTO，供 {@code /user/login}、{@code /user/signup} 和
 * {@code /user/bind-phone} 使用。
 * 服务端根据字段组合选择流程：密码非空时走账号/手机号密码登录；否则走手机号验证码登录。
 * 注册和绑定接口也只读取各自需要的字段。
 * 安全边界：验证码和密码属于敏感输入，不应记录到日志、响应或异常详情中。
 */
@Data
public class LoginFormDTO {

    /**
     * 独立登录账号（可采用用户名或邮箱风格）；账号注册、账号登录和两阶段绑定手机号时使用。
     */
    private String account;

    /** 手机号；用于短信验证码登录、手机号注册、手机号密码登录和绑定手机号。 */
    private String phone;

    /** 短信验证码；用于验证码登录、手机号注册和绑定手机号。 */
    private String code;

    /** 登录或注册密码；为空时登录接口会选择手机号验证码流程。 */
    private String password;
}
