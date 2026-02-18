package com.hmdp.config;

import com.hmdp.sms.SmsSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsSenderConfig {
    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender missingSmsSender(@Value("${hmdp.sms.provider:log}") String provider) {
        return (phone, code) -> {
            throw new IllegalStateException(
                    "No SmsSender is configured for hmdp.sms.provider=" + provider
                            + ". Configure a real SMS provider or use provider=log for local development.");
        };
    }
}

