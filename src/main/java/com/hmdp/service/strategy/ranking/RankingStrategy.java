package com.hmdp.service.strategy.ranking;

/*
 * 现实业务背景：关注流和为你推荐需要不同排序规则，但 Feed 主流程不应写死具体算法。
 * 实际触发：BlogFeedService 从注册表（RankingStrategyRegistry）取得实现后调用 score/rank；
 * 实现类通过名称声明自己的策略身份，本项目现有三个实现：
 * 1. "simple"（SimpleRankingStrategy）：新鲜度 + 热度 + 作者亲和度的加权基线排序。
 * 2. "time"（SimpleTimeRankingStrategy）：按发布时间倒序，关注流（following 模式）使用。
 * 3. "weighted"（WeightedRankingStrategy）：点赞、评论、新鲜度、不感兴趣信号加权，为你推荐（for_you 模式）使用。
 */

import java.util.List;

/**
 * 排序策略接口，泛型 T 是被排序的对象（本项目里都是 {@code Blog}）。
 * score 和 rank 必须给出一致的分数语义：rank 内部就是按 score 降序排，分数高者排前。
 */
public interface RankingStrategy<T> {

    /**
     * 给单个候选打分，分数只作为"相对先后"的依据。
     * 使用场景：仅被各实现类自己的 rank() 在排序比较器里调用（BlogFeedService.rebuild 经注册表
     * {@link RankingStrategyRegistry}（排序策略注册表：按名称取出排序实现）取得策略后只调 rank）；
     * 测试 RankingStrategyOrderTest 经 rank 间接覆盖。
     * 实现要点：不同实现的分数量纲不同且不能互相比较——"time" 是毫秒时间戳，"simple" 是
     * 0~1 的加权和，"weighted" 是四路加权和（理论上可超过 1）；分数不代表绝对质量或概率。
     */
    double score(T item, RankingContext context);

    /**
     * 对整批候选按 score 降序排好并返回列表。
     * 使用场景：仅被 BlogFeedService.rebuild 调用（for_you 模式在曝光过滤之后、作者打散之前排序；
     * following 模式召回后直接排序）；现有三个实现见类注释。
     * 实现要点：实现类对传入 List 就地排序（List.sort，返回值就是原列表）；同分时的次序
     * 由各实现定义（本项目三个实现都用 blogId 降序兜底，保证翻页顺序稳定）。
     */
    List<T> rank(List<T> items, RankingContext context);

    /**
     * 返回策略的注册名（现有取值 "simple" / "time" / "weighted"）。
     * 使用场景：被 {@link RankingStrategyRegistry}（排序策略注册表）的 init 以返回值作键登记
     * （@PostConstruct 建 "策略名 -> 实现" Map）；BlogFeedService 通过 FeedMode 映射出名称
     * （following -> "time"、for_you -> "weighted"）后按这个名字查找实现。
     * 实现要点：返回编译期常量字符串；名字是注册表的查找键，改名会导致 Feed 取不到策略而回退 "simple"。
     */
    String getStrategyName();
}
