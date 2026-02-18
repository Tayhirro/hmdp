package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 组合解析器：允许多种认证方式“并存”。
 *
 * 示例：先尝试 JWT，再尝试 Session（或反过来）。
 * 你可以在构造时传入 resolver 列表来控制顺序。
 */
public class CompositeAuthResolver implements AuthResolver {

    private final List<AuthResolver> delegates;

    public CompositeAuthResolver(List<AuthResolver> delegates) {
        this.delegates = delegates;
    }

    @Override
    @Nullable
    public UserDTO resolve(HttpServletRequest request) {
        for (AuthResolver delegate : delegates) {
            UserDTO user = delegate.resolve(request);
            if (user != null) {
                return user;
            }
        }
        return null;
    }
}
