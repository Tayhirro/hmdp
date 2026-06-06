package com.hmdp.service.strategy.recall;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RecallContext {
    private Long userId;
    private Long maxTime;
    private int limit;
    private Map<String, Object> extra;
}
