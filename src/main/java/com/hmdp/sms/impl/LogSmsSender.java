package com.hmdp.sms.impl;

import com.hmdp.sms.SmsSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hmdp.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);
    /**
     * 用 INFO 日志打印验证码，不真实发送短信。
     *
     * 使用场景：hmdp.sms.provider 配置为 log 或未配置（matchIfMissing=true）时本 bean 生效，
     * 由 UserServiceImpl 的发码流程调用；验证码的真正下发在 Redis 校验（key = login:code:手机号），
     * 开发期从日志 [SMS:log] phone=..., loginCode=... 中取码即可。
     *
     * @param phone 接收验证码的手机号
     * @param code 登录验证码
     */
    @Override
    public void sendLoginCode(String phone, String code) {
        log.info("[SMS:{}] phone={}, loginCode={}", "log", phone, code);
    }
}
