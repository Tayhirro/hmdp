package com.hmdp.service.strategy.ranking;

import java.util.List;

public interface RankingStrategy<T> {

    double score(T item, RankingContext context);

    List<T> rank(List<T> items, RankingContext context);

    String getStrategyName();
}
