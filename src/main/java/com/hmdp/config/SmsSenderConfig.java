package com.hmdp.config;

import com.hmdp.sms.SmsSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsSenderConfig {
    /**
     * 提供一个「未配置短信平台」的兜底 {@link SmsSender}（短信发送接口），调用即抛异常快速失败。
     *
     * 使用场景：Spring 启动时评估；仅当容器中不存在任何 SmsSender bean
     * （即 hmdp.sms.provider 配成了 log 以外的值且没有接入对应实现）时才创建本 bean。
     * 默认 hmdp.sms.provider=log（缺省同）时 {@link com.hmdp.sms.impl.LogSmsSender}（日志短信实现）
     * 已存在，本 bean 不会被创建。这样能在开发期避免误发真实短信，又能让
     * 生产环境忘配短信实现时在第一次发码处得到明确报错。
     *
     * @param provider 配置项 hmdp.sms.provider 的值，默认 "log"，仅用于拼装报错信息
     * @return 调用 sendLoginCode 即抛 {@link IllegalStateException} 的占位实现
     */
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

