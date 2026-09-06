package com.hmdp.dto;

import lombok.Data;

/**
 * 可安全暴露的用户摘要，也是请求期间的登录用户上下文。
 *
 * 类别：认证上下文和用户列表响应共用的 DTO。
 * 登录成功后，该对象可保存到 Redis Token 或 Session；认证拦截器解析后放入
 * {@code UserHolder}，业务代码据此取得当前用户 ID。
 * 边界：只包含公开展示所需字段，不携带手机号、账号、密码摘要等敏感数据库字段。
 */
@Data
public class UserDTO implements SearchResultItemDTO {

    /** 用户 ID；鉴权后的业务操作以此识别当前用户。 */
    private Long id;

    /** 用户公开昵称。 */
    private String nickName;

    /** 用户公开头像地址。 */
    private String icon;
}
