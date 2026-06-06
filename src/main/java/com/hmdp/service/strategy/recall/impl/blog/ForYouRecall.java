package com.hmdp.service.strategy.recall.impl.blog;

import com.hmdp.service.strategy.recall.RecallContext;
import com.hmdp.service.strategy.recall.RecallStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ForYouRecall implements RecallStrategy {

    @Override
    public List<Long> recall(RecallContext ctx) {
        // TODO: 基于用户点赞历史的个性化推荐
        // 实现思路：
        // 1. 查出用户最近点赞过的博客的作者 ID 或标签
        // 2. 按这些特征找到同类博客（SQL 或 Redis）
        // 3. 按 maxTime 做滚动分页，返回最多 count 个
        // 4. 避免推已经看过的博客（用 Redis Set 记录已曝光 ID）
        return Collections.emptyList();
    }

    @Override
    public String getStrategyName() {
        return "for-you";
    }
}
