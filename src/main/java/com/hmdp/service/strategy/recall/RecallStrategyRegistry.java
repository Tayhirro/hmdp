package com.hmdp.service.strategy.recall;

/*
 * 现实业务背景：系统需要把 follow、for-you 等召回实现按稳定名称注册，避免依赖 Spring Bean 枚举顺序。
 * 实际触发：Spring 启动时收集全部 {@link RecallStrategy}（召回策略接口：给定上下文，返回一批候选博客 ID）；
 * {@link RecallOrchestrator}（召回编排器：按通道调用各策略并去重合并）在 Feed 请求中按产品模式查找。
 *
 * 工作方式：
 * 1. strategies 由 Spring 注入容器里全部召回实现，当前是 "follow"（FollowFeedRecall）和
 *    "for-you"（ForYouRecall）两个 @Component。
 * 2. init() 在 Bean 创建完成后（@PostConstruct）把列表转成 "通道名 -> 实现" 的 Map。
 *    注意 Collectors.toMap 遇到重复通道名会直接抛异常，相当于启动时就能暴露命名冲突。
 * 3. 与排序侧的 RankingStrategyRegistry 不同：getStrategy() 找不到名字时返回 null 而不是回退默认值，
 *    由编排器决定跳过（RecallOrchestrator.multiRecall 对 null 直接 continue）。
 * 4. getAllStrategies() 返回全部实现，供 multiRecallAll 做"全通道召回"。
 */

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

    /**
     * 把 Spring 注入的全部召回实现转成 "通道名 -> 实现" 的 Map，供后续按名查找。
     * 使用场景：仅由 Spring 容器在本 Bean 初始化完成后调用（@PostConstruct），项目内无手动调用方。
     * 实现要点：strategies.stream().collect(Collectors.toMap(RecallStrategy::getStrategyName, Function.identity()))；
     * 遇到重复通道名 Collectors.toMap 直接抛 IllegalStateException，相当于启动时即暴露命名冲突。
     */
    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(RecallStrategy::getStrategyName, Function.identity()));
    }

    /**
     * 按通道名取召回实现；名字不存在时返回 null，不回退默认值，由调用方决定是否跳过。
     * 使用场景：仅被 {@link RecallOrchestrator}（召回编排器：按通道名调用各策略并去重合并）的
     * multiRecall 调用，对 null 直接跳过该通道。这与排序侧 RankingStrategyRegistry 找不到就回退
     * "simple" 的做法不同。
     * 实现要点：直接 strategyMap.get(strategyName)，O(1) 哈希查找，无其他逻辑。
     */
    public RecallStrategy getStrategy(String strategyName) {
        return strategyMap.get(strategyName);
    }

    /**
     * 返回 Spring 注入的全部召回实现列表（当前 "follow" 与 "for-you" 两个）。
     * 使用场景：仅被 {@link RecallOrchestrator}（召回编排器）的 multiRecallAll（全通道召回备用入口，
     * 生产链路暂未使用）调用。
     * 实现要点：直接返回注入的 strategies 引用而非拷贝，顺序即 Spring 注入顺序；调用方不应修改该列表。
     */
    public List<RecallStrategy> getAllStrategies() {
        return strategies;
    }
}
