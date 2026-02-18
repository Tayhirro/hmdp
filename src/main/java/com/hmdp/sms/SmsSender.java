package com.hmdp.sms;

/**
 * SMS sending abstraction.
 *
 * <p>Default implementation in this project logs the code (for local development).
 * Replace with a real provider (Aliyun/Tencent/Twilio, etc.) in production.</p>
 */
public interface SmsSender {
    void sendLoginCode(String phone, String code);
}