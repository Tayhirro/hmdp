package com.hmdp.service.strategy.ranking;

/*
 * 现实业务背景：系统启动后需要把多个排序实现按名称登记，Feed 请求才能根据产品模式稳定选择。
 * 实际触发：Spring 初始化时收集所有 RankingStrategy；BlogFeedService 每次重建 Feed 时按名称查询。
 *
 * 工作方式：
 * 1. 字段 strategies 由 Spring 注入容器里全部 {@link RankingStrategy}（排序策略接口）实现，
 *    当前是 simple / time / weighted 三个 @Component。
 * 2. init() 在 Bean 创建完成后（@PostConstruct）把列表转成 "策略名 -> 实现" 的 Map，
 *    策略名来自各实现的 getStrategyName()。
 * 3. getStrategy() 按名字取策略；名字不存在时回退到 "simple"，保证调用方永远拿得到非空策略。
 *    例如 FeedMode 给 following 映射 "time"、给 for_you 映射 "weighted"，
 *    即使将来加新模式配错名字，Feed 也不会因为找不到策略而报错，只是退回基线排序。
 * 4. getDefaultStrategy() 显式取 "simple"；getStrategyNames() 返回全部已注册策略名，便于排查注册情况。
 */

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

    /**
     * 把 Spring 注入的全部排序实现转成 "策略名 -> 实现" 的 Map，供后续按名查找。
     * 使用场景：仅由 Spring 容器在本 Bean 初始化完成后调用（@PostConstruct），项目内无手动调用方。
     * 实现要点：strategies.stream().collect(Collectors.toMap(RankingStrategy::getStrategyName, Function.identity()))；
     * 键来自各实现的 getStrategyName()（simple/time/weighted）；两个实现重名时 toMap 抛 IllegalStateException，
     * 相当于启动时即暴露命名冲突。
     */
    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(RankingStrategy::getStrategyName, Function.identity()));
    }

    /**
     * 按注册名取排序策略；名字不存在时回退 "simple"，保证调用方永远拿到非空策略。
     * 使用场景：仅被 BlogFeedService.rebuild 调用（以 FeedMode 映射出的 "time"/"weighted" 为键）；
     * 本类 getDefaultStrategy 也等价于 getStrategy("simple")。
     * 实现要点：strategyMap.get(strategyName) 为 null 时改取 strategyMap.get("simple")；
     * 返回前强转为泛型 T（@SuppressWarnings("unchecked")），调用方需保证 T 与实际策略的元素类型一致。
     */
    @SuppressWarnings("unchecked")
    public <T> RankingStrategy<T> getStrategy(String strategyName) {
        RankingStrategy<?> strategy = strategyMap.get(strategyName);
        if (strategy == null) {
            strategy = strategyMap.get("simple");
        }
        return (RankingStrategy<T>) strategy;
    }

    /**
     * 显式取基线排序策略 "simple"（SimpleRankingStrategy：0.5 新鲜度 + 0.3 热度 + 0.2 作者亲和度）。
     * 使用场景：当前生产代码无调用方（getStrategy 的回退是直接查 "simple" 键，不经过本方法），
     * 留作需要默认策略时的入口。
     * 实现要点：等价于 getStrategy("simple")；只有 "simple" 实现从容器消失时才会返回 null
     * （三个实现都是 @Component，正常运行不会发生）。
     */
    public <T> RankingStrategy<T> getDefaultStrategy() {
        return getStrategy("simple");
    }

    /**
     * 返回全部已注册的排序策略名（当前 simple/time/weighted）。
     * 使用场景：当前生产代码无调用方，主要用于排查注册情况（确认容器里有哪些策略名可用）。
     * 实现要点：对 Spring 注入的 strategies 列表逐个取 getStrategyName() 收集成 List；
     * 顺序即 Spring 注入顺序，不是按名字排序。
     */
    public List<String> getStrategyNames() {
        return strategies.stream()
                .map(RankingStrategy::getStrategyName)
                .collect(Collectors.toList());
    }
}
