package com.hmdp.service.strategy.recall;

import java.util.List;

public interface RecallStrategy {

    List<Long> recall(RecallContext ctx);

    String getStrategyName();
}
