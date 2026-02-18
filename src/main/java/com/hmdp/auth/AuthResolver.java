package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;
import javax.servlet.http.HttpServletRequest;

/**
 * 认证信息解析器（模板）。
 *
 * 作用：从一次 HTTP 请求中解析出当前登录用户（如果有的话）。
 * - 返回 null：表示本次请求未登录/无法识别
 * - 返回 UserDTO：表示已登录
 */
public interface AuthResolver {
    @Nullable
    UserDTO resolve(HttpServletRequest request);    
}
