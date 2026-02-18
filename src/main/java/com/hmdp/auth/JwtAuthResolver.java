package com.hmdp.auth;

import com.hmdp.dto.UserDTO;

import org.springframework.lang.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT 认证解析器（模板）。
 *
 * 注意：
 * - JWT 的 payload 默认不加密，只是 Base64URL 编码
 * - 防篡改靠 signature（验签）
 *
 * 
 * headerBase64Url.payloadBase64Url.signatureBase64Url
 * 
 * 
 */

// {
//   "alg": "HS256",
//   "typ": "JWT"
// }
// {
//   "id": 123,
//   "nickName": "tom",
//   "icon": "/imgs/1.png",
//   "exp": 1896300000,
//   "nbf": 1896296400
// }
public class JwtAuthResolver implements AuthResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private static final Base64.Decoder BASE64URL_DECODER = Base64.getUrlDecoder();


    private final byte[] secret; //

    public JwtAuthResolver(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Nullable
    public UserDTO resolve(HttpServletRequest request) {
        String authorization = request.getHeader("authorization");
        if (StrUtil.isBlank(authorization)) {
            return null;
        }

        String token = authorization.trim();
        if(StrUtil.startWithIgnoreCase(token, "Bearer ")){
            token = token.substring("Bearer ".length()).trim();
        }
        if (token.isEmpty()) {
            return null;
        }
        // Redis token 通常不包含 '.'；JWT 必须包含 2 个 '.'
        int firstDot = token.indexOf('.');
        if (firstDot < 0) {
            return null;
        }
        int secondDot = token.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return null;
        }
        if (secret.length == 0) {
            return null;
        }

        String headerB64 = token.substring(0, firstDot);
        String payloadB64 = token.substring(firstDot + 1, secondDot);
        String sigB64 = token.substring(secondDot + 1);
        if (headerB64.isEmpty() || payloadB64.isEmpty() || sigB64.isEmpty()) {
            return null;
        }


        Map<String, Object> header;
        Map<String, Object> payloadclaims;
        // 将 byte 的 json 转为 指定格式map
        try {
            header = OBJECT_MAPPER.readValue(BASE64URL_DECODER.decode(headerB64), MAP_TYPE);
            payloadclaims = OBJECT_MAPPER.readValue(BASE64URL_DECODER.decode(payloadB64), MAP_TYPE);
        } catch (Exception e) {
            return null;
        }

        Object alg = header.get("alg");
        if (!"HS256".equals(alg)) {     //通过HS256进行加密
            return null;
        }
        // 验证 signature
        if (!verifyHs256(token.substring(0, secondDot), sigB64)) {
            return null;
        }
        // 验证是否过期
        if (isExpired(payloadclaims)) {
            return null;
        }

        // 从 payload 中获取用户信息
        Long id = getLong(payloadclaims.get("id"));
        if (id == null) {
            return null;
        }
        UserDTO user = new UserDTO();
        user.setId(id);
        Object nickName = payloadclaims.get("nickName");
        if (nickName != null) {
            user.setNickName(String.valueOf(nickName));
        }
        Object icon = payloadclaims.get("icon");
        if (icon != null) {
            user.setIcon(String.valueOf(icon));
        }
        return user;
    }

    private boolean verifyHs256(String signingInput, String signatureB64) {
        // 输入 ----加密--->  对比 签名
        byte[] expected;
        try {  
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
        byte[] actual;
        try {
            actual = BASE64URL_DECODER.decode(signatureB64);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    // notbefore or notafter
    private static boolean isExpired(Map<String, Object> claims) {
        long now = Instant.now().getEpochSecond();
        Long nbf = getLong(claims.get("nbf"));
        if (nbf != null && now < nbf) {
            return true;
        }
        Long exp = getLong(claims.get("exp"));
        return exp != null && now >= exp;
    }

    private static Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
