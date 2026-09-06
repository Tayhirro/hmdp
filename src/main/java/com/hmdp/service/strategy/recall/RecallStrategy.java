package com.hmdp.service.strategy.recall;

/*
 * 现实业务背景：不同候选来源有不同查询规则，Feed 主流程只关心最终得到哪些 blogId。
 * 实际触发：每个召回实现提供名称和 recall()；{@link RecallOrchestrator}（召回编排器：
 * 按通道依次调用各策略、去重合并候选 ID）通过统一接口组合它们。
 *
 * 本项目现有两个实现：
 * 1. "follow"（FollowFeedRecall）：从用户已关注的作者中按发布时间倒序取博客 ID。
 * 2. "for-you"（ForYouRecall）：从偏好作者 + 陌生作者两路取博客 ID，供为你推荐发现圈外内容。
 */

import java.util.List;

/**
 * 召回策略接口。"召回"指从海量博客中先粗筛出一批候选 ID，之后再由排序策略决定先后。
 * 返回的 ID 顺序就是通道内的重要性顺序，编排器合并时保留这个顺序。
 */
public interface RecallStrategy {

    /**
     * 执行一次召回，返回候选博客 ID 列表（最多 ctx.limit 条；没有候选时返回空列表而不是 null）。
     * 使用场景：仅被 {@link RecallOrchestrator}（召回编排器：按通道名调用各策略并用 LinkedHashSet
     * 去重合并候选 ID）的 multiRecall / multiRecallAll 调用，源头是 BlogFeedService.rebuild（limit 固定 200）；
     * 现有两个实现：FollowFeedRecall（"follow"，按关注作者查博客）、ForYouRecall（"for-you"，偏好作者 + 发现两路）。
     * 实现要点：实现类需自行处理游标边界（ctx.maxTime 与 extra.lastId）、内部排序和 LIMIT 截断，
     * 返回顺序即通道内的重要性顺序，编排器合并时保留；还可往 {@link RecallContext}（召回上下文：
     * 用户、时间边界、候选上限、共享信号）的 extra 写入共享信号（如 ForYouRecall 写入
     * "authorInteractions" 供排序阶段复用，避免重复查库）。
     */
    List<Long> recall(RecallContext ctx);

    /**
     * 返回召回通道的注册名（现有取值 "follow" 和 "for-you"）。
     * 使用场景：被 {@link RecallStrategyRegistry}（召回策略注册表）的 init 以返回值作键登记
     * （@PostConstruct 建 "通道名 -> 实现" Map）；BlogFeedService 通过 FeedMode 决定用哪些通道
     * （following -> ["follow"]，for_you -> ["follow","for-you"]）后经 RecallOrchestrator.multiRecall 按名查找。
     * 实现要点：返回常量字符串；通道名只在服务内部流转，不暴露给客户端。
     */
    String getStrategyName();
}
