package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.feed")
public class FeedProperties {

    /**
     * 超过该粉丝阈值可按大V策略处理（当前仅配置，后续策略逻辑使用）
     */
    private Long bigVFansThreshold = 10000L;

    /**
     * 每个用户 inbox 在 Redis 热层保留的最大条数
     */
    private Integer inboxCacheMaxSize = 1000;
}
