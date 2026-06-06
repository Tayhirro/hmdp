package com.hmdp.service.strategy.ranking;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class RankingContext {
    private Long currentUserId;
    private LocalDateTime now;
    private Map<Long, Double> authorAffinity;
    private Map<Long, Integer> authorInteractionCount;
}
