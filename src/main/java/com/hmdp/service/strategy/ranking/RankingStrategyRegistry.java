package com.hmdp.service.strategy.ranking;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RankingStrategyRegistry {

    @Resource
    private List<RankingStrategy<?>> strategies;

    private Map<String, RankingStrategy<?>> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(RankingStrategy::getStrategyName, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public <T> RankingStrategy<T> getStrategy(String strategyName) {
        RankingStrategy<?> strategy = strategyMap.get(strategyName);
        if (strategy == null) {
            strategy = strategyMap.get("simple");
        }
        return (RankingStrategy<T>) strategy;
    }

    public <T> RankingStrategy<T> getDefaultStrategy() {
        return getStrategy("simple");
    }
}
