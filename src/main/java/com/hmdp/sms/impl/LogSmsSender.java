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
    @Override
    public void sendLoginCode(String phone, String code) {
        log.info("[SMS:{}] phone={}, loginCode={}", "log", phone, code);
    }
}
