package com.hmdp.utils;

/**
 * @author 虎哥
 */
public abstract class RegexPatterns {
    /**
     * 手机号正则：11 位、1 开头，第二三位限 13x/38x、4[579]、5[0-3,5-9]、66、7[0135678]、9[89] 号段，后 8 位任意数字。
     * 使用处：正则校验工具 {@link RegexUtils} 的 isPhoneInvalid 方法，UserServiceImpl 发送验证码、短信登录、注册等入口的格式校验。
     */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
    /**
     * 邮箱正则：@ 前后均为字母/数字/下划线/连字符，且域名至少含一个点分段。
     * 使用处：正则校验工具 {@link RegexUtils} 的 isEmailInvalid 方法；当前主代码暂无调用方（预留，测试 RegexUtilsTest 有覆盖）。
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    /**
     * 密码正则。4~32位的字母、数字、下划线（即 \\w 量词 {4,32}）。
     * 使用处：预留常量，RegexUtils 未提供对应校验方法，当前工程无调用方。
     */
    public static final String PASSWORD_REGEX = "^\\w{4,32}$";
    /**
     * 验证码正则, 6位数字或字母（[a-zA-Z\d] 恰好 6 个）。
     * 使用处：正则校验工具 {@link RegexUtils} 的 isCodeInvalid 方法，UserServiceImpl 短信登录、绑定手机号等校验验证码格式。
     */
    public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";

}
