package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

/**
 * @author 虎哥
 */
public class RegexUtils {
    /**
     * 是否是无效手机格式
     *
     * 使用场景：UserServiceImpl 发送验证码、短信登录、注册等入口校验手机号，
     * 返回 true 时直接拒绝请求。
     *
     * @param phone 要校验的手机号
     * @return true：无效（不符合正则常量类 {@link RegexPatterns} 的 PHONE_REGEX），false：有效
     */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    /**
     * 是否是无效邮箱格式
     *
     * 使用场景：预留校验（邮箱注册/绑定场景）；当前主代码无调用方，RegexUtilsTest 有单测覆盖。
     *
     * @param email 要校验的邮箱
     * @return true：无效（不符合 {@link RegexPatterns#EMAIL_REGEX}），false：有效
     */
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * 是否是无效验证码格式
     *
     * 使用场景：UserServiceImpl 短信登录、绑定手机号等流程校验用户提交的验证码格式，
     * 格式无效即失败，不必查 Redis。
     *
     * @param code 要校验的验证码
     * @return true：无效（不符合 {@link RegexPatterns#VERIFY_CODE_REGEX}），false：有效
     */
    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    /**
     * 校验字符串是否不符合正则格式（空白视为无效）。
     *
     * 使用场景：本类三个 isXxxInvalid 方法的公共实现；
     * null 或空白字符串直接判无效，否则用 String.matches 全串匹配后取反。
     *
     * @param str 待校验字符串
     * @param regex 全匹配用正则
     * @return true：无效；false：符合正则
     */
    // 校验是否不符合正则格式
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
