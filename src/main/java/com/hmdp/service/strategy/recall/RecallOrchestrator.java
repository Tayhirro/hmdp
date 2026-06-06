package com.hmdp.service.strategy.recall;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RecallOrchestrator {

    @Resource
    private RecallStrategyRegistry registry;

    public List<Long> multiRecall(List<String> strategyNames, RecallContext ctx) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String name : strategyNames) {
            RecallStrategy strategy = registry.getStrategy(name);
            if (strategy != null) {
                ids.addAll(strategy.recall(ctx));
            }
        }
        return new ArrayList<>(ids);
    }

    public List<Long> multiRecallAll(RecallContext ctx) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (RecallStrategy strategy : registry.getAllStrategies()) {
            ids.addAll(strategy.recall(ctx));
        }
        return new ArrayList<>(ids);
    }
}
