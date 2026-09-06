package com.hmdp.utils;


import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 密码加密与校验工具（盐值 + MD5 摘要，存储格式为 "盐@md5hex"）。
 * 注意：MD5 加盐强度有限，工程内当前无调用方，属预留实现，生产建议改用 BCrypt 等慢哈希。
 */
public class PasswordEncoder {

    /**
     * 加密明文密码：先生成 20 位随机盐（Hutool RandomUtil.randomString(20)），再走 {@link #encode(String, String)}。
     *
     * 使用场景：用户注册/创建账号时调用，返回值（格式 盐@md5(密码+盐) 的十六进制串）直接存库；
     * 当前工程主代码未直接调用（预留工具）。
     *
     * @param password 用户输入的明文密码
     * @return 形如 "盐@摘要" 的加密串
     */
    public static String encode(String password) {
        // 生成盐
        String salt = RandomUtil.randomString(20);
        // 加密
        return encode(password,salt);
    }
    /**
     * 用给定盐做 MD5 摘要拼出存储串。
     *
     * 使用场景：由 {@link #encode(String)}（随机盐）和 {@link #matches}（用库存盐重算）内部调用。
     * 算法：Spring DigestUtils.md5DigestAsHex 对 UTF-8 字节串 (password + salt) 求 MD5，
     * 返回 "salt + @ + 32 位十六进制摘要"。
     *
     * @param password 明文密码
     * @param salt 盐值
     * @return 形如 "盐@摘要" 的加密串
     */
    private static String encode(String password, String salt) {
        // 加密
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    /**
     * 校验明文密码与库存加密串是否匹配。
     *
     * 使用场景：登录/改密时对用户输入做校验（与 encode 生成的库存串配对使用）。
     * 规则：任一参数为 null 返回 false；加密串不含 "@" 直接抛 RuntimeException("密码格式不正确！")；
     * 否则按 "@" 拆出前段盐，用 {@link #encode(String, String)} 重算并做字符串全等比较。
     *
     * @param encodedPassword 库中存储的 "盐@摘要" 串
     * @param rawPassword 用户输入的明文密码
     * @return true 表示匹配
     */
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("密码格式不正确！");
        }
        String[] arr = encodedPassword.split("@");
        // 获取盐
        String salt = arr[0];
        // 比较
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
