package com.hmdp.sms;

/**
 * SMS sending abstraction.
 *
 * Default implementation in this project logs the code (for local development).
 * Replace with a real provider (Aliyun/Tencent/Twilio, etc.) in production.
 */
public interface SmsSender {
    /**
     * 向指定手机号发送登录短信验证码。
     *
     * 使用场景：UserServiceImpl 的发码流程（字段 smsSender 注入）在校验手机号格式、
     * 完成发送频率控制后调用；实现类按 hmdp.sms.provider 选择——
     * log（或缺省）走 {@link com.hmdp.sms.impl.LogSmsSender}（日志短信实现）打日志，
     * 生产环境替换为真实短信平台（阿里云/腾讯云等）实现。
     *
     * @param phone 接收验证码的手机号（已通过 RegexUtils.isPhoneInvalid 校验）
     * @param code 登录验证码，6 位字母或数字，调用方负责存入 Redis（key = login:code:手机号，TTL 2 分钟）
     */
    void sendLoginCode(String phone, String code);
}