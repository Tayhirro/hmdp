package com.hmdp.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class BlogRankStrategyRouter {

    private final Map<String, BlogRankStrategy> strategyMap;

    public BlogRankStrategyRouter(List<BlogRankStrategy> strategies) {
        Map<String, BlogRankStrategy> map = strategies == null
                ? Collections.emptyMap()
                : strategies.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BlogRankStrategy::scene,
                        s -> s,
                        (a, b) -> {
                            throw new IllegalStateException("Duplicate blog rank strategy scene: " + a.scene());
                        }
                ));
        this.strategyMap = Collections.unmodifiableMap(map);
    }

    public BlogRankStrategy get(String scene) {
        return strategyMap.get(scene);
    }
}
