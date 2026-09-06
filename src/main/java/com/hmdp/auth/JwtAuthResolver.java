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
 * headerBase64Url.payloadBase64Url.signatureBase64Url
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

    /**
     * 创建 JWT 解析器并保存 HMAC-SHA256 验签密钥。
     *
     * 使用场景：Spring 启动时由 {@link com.hmdp.config.AuthResolverConfig}（认证解析器装配配置类）
     * 的 jwtAuthResolver 方法创建；
     * 当前该工厂方法硬编码传入 null（未接配置项 hmdp.auth.jwt.secret，默认为空），
     * 密钥为空时 resolve 会直接返回 null，即本解析器实际不生效。
     *
     * @param secret 验签密钥（UTF-8 字节），为 null 时按空密钥处理
     */
    public JwtAuthResolver(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从请求头 authorization 中解析并校验 JWT，还原当前登录用户。
     *
     * 使用场景：每次 HTTP 请求由 {@link CompositeAuthResolver}（组合解析器）调用（auto 模式下排在第一位）。
     * 校验链：取 header authorization，剥离可选的 "Bearer " 前缀（忽略大小写），
     * token 必须恰好含 2 个 '.'（header.payload.signature，否则视为 Redis token 直接放弃），
     * 密钥非空，header 的 alg 必须为 HS256，验签通过，未过期，最后从 payload
     * 的 id/nickName/icon 三个 claim 组装 {@link UserDTO}（用户 DTO，id 缺失则失败）。
     * 任一步失败都返回 null，不抛异常。
     *
     * @param request 当前 HTTP 请求
     * @return 校验全部通过时返回用户信息；否则返回 null
     */
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

    /**
     * 用 HS256 重算签名并与 token 携带的签名比对。
     *
     * 使用场景：{@link #resolve} 在读取 header/payload 之后调用，验签通过才继续解析 claims。
     * 算法：对 signingInput（即 "header.payload" 的原始 Base64URL 文本，US-ASCII 字节）
     * 用构造时保存的密钥做 HmacSHA256，再与 Base64URL 解码后的签名做
     * MessageDigest.isEqual 常量时间比较，防止时序攻击。
     * MAC 初始化失败或签名 Base64URL 非法都按验签失败（false）处理。
     *
     * @param signingInput 待验签的 "header.payload" 原文
     * @param signatureB64 token 第三段的 Base64URL 签名
     * @return true 表示签名一致未被篡改
     */
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

    /**
     * 校验 JWT 是否处于生效时间窗之外（未生效或已过期）。
     *
     * 使用场景：{@link #resolve} 验签通过后调用。claims 中：
     * nbf（not before，Unix 秒）存在且当前时间早于它，视为未生效；
     * exp（expire，Unix 秒）存在且当前时间大于等于它，视为已过期。
     * 两个 claim 都可缺省，缺省即不做该项限制。
     *
     * @param claims JWT payload 的键值对
     * @return true 表示不可用（未生效或已过期）
     */
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

    /**
     * 把 JSON claim 值宽容地转换为 Long。
     *
     * 使用场景：{@link #resolve} 读取 id claim、{@link #isExpired} 读取 nbf/exp claim 时调用。
     * 规则：Number 直接取 longValue；字符串去空白后按十进制解析；null、空串、
     * 非数字字符串或其他类型一律返回 null。
     *
     * @param value 原始 claim 值
     * @return 转换后的 Long；无法转换时返回 null
     */
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
