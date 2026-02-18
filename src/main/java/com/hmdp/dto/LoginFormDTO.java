package com.hmdp.dto;

import lombok.Data;

@Data
public class LoginFormDTO {
    /**
     * 账号（用户名/邮箱/手机号）。推荐用这个字段作为统一登录名。
     */
    private String account;
    private String phone;
    private String code;
    private String password;
}
