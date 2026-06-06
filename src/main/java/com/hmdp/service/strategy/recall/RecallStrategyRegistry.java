package com.hmdp.service.strategy.recall;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RecallStrategyRegistry {

    @Resource
    private List<RecallStrategy> strategies;

    private Map<String, RecallStrategy> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(RecallStrategy::getStrategyName, Function.identity()));
    }

    public RecallStrategy getStrategy(String strategyName) {
        return strategyMap.get(strategyName);
    }

    public List<RecallStrategy> getAllStrategies() {
        return strategies;
    }
}
