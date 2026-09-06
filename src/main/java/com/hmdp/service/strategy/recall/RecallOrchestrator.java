package com.hmdp.service.strategy.recall;

/*
 * 现实业务背景：为你推荐需要合并关注和发现等多个候选来源，并保持通道顺序同时去重。
 * 实际触发：BlogFeedService 按产品模式传入明确的通道名称，本类依次调用策略并用 LinkedHashSet 合并 blogId。
 *
 * 用一次真实请求把"召回 -> 排序"流程走一遍（以 for_you 模式为例，数字均来自代码）：
 * 1. 用户请求 GET /blog/feed?mode=for_you，进入 {@link com.hmdp.service.feed.BlogFeedService}
 *    （Feed 读链路入口服务）。快照缓存未命中时需要重建 Feed。
 * 2. BlogFeedService 构造 {@link RecallContext}（召回上下文：用户、时间边界、候选上限 200 等）：
 *    userId = 当前用户，limit = 200（候选池大小），maxTime/lastId 取自翻页游标（首页为空）。
 * 3. 调用 multiRecall(["follow", "for-you"], ctx)：
 *    - follow 通道（{@link FollowFeedRecall}，关注作者召回）：从用户已关注的作者里按发布时间倒序
 *      取最多 200 条博客 ID；
 *    - for-you 通道（{@link ForYouRecall}，为你推荐召回）：从"点过赞的偏好作者"取
 *      min(200/2, 偏好作者数*10) 条（如 3 个偏好作者则 30 条），再用"发现"部分补满剩余名额，
 *      合计最多 200 条，按点赞数、发布时间倒序；
 *    - 两路结果依次放进 LinkedHashSet：既去重，又保留"先 follow 后 for-you"的通道顺序。
 * 4. 合并结果（最多 200 条 ID）返回给 BlogFeedService，它按 ID 批量查出博客实体；
 *    for_you 模式还会先用曝光服务过滤掉用户已看过的博客。
 * 5. 排序：BlogFeedService 按模式取排序策略——for_you 模式取 "weighted"
 *    （WeightedRankingStrategy：1.0*点赞 + 0.6*评论 + 0.3*新鲜度 - 0.5*陈旧信号的加权排序），
 *    following 模式则只召回 follow 通道并取 "time"（按发布时间倒序）。
 * 6. 输出：排好序的候选整体写入快照缓存，本次返回前 50 条（页面大小 PAGE_SIZE），
 *    剩下的靠游标继续翻页取。
 *
 * 两个方法：
 * - multiRecall：按给定通道名列表依次召回，名字查不到的策略直接跳过（不报错）。
 * - multiRecallAll：把注册表里全部 {@link RecallStrategy}（召回策略接口）都跑一遍再合并；
 *   目前生产链路只走 multiRecall，这个方法留作全通道召回的备用入口。
 */

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RecallOrchestrator {

    @Resource
    private RecallStrategyRegistry registry;

    /**
     * 按给定通道名列表依次召回，用 LinkedHashSet 去重合并各通道返回的博客 ID，保留通道先后顺序。
     * 使用场景：仅被 BlogFeedService.rebuild 调用——following 模式传 ["follow"]，for_you 模式传
     * ["follow", "for-you"]（通道组合由 FeedMode 对应的固定搭配决定）；候选上限 ctx.limit 固定 200。
     * 实现要点：对每个名字查 {@link RecallStrategyRegistry}（召回策略注册表：按通道名取出召回实现），
     * 查不到返回 null 就直接跳过该通道不报错；各通道结果 addAll 进 LinkedHashSet（去重且保持插入顺序），
     * 最后转成 ArrayList 返回，元素仍保持各通道内部排好的顺序。
     */
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

    /**
     * 把注册表里全部召回通道各跑一遍并去重合并，遍历顺序即 Spring 注入实现的顺序（当前 follow、for-you）。
     * 使用场景：当前生产代码无调用方（生产链路只走 multiRecall），留作"全通道召回"的备用入口。
     * 实现要点：遍历 registry.getAllStrategies() 逐个 recall(ctx)，结果 addAll 进 LinkedHashSet
     * 去重保序后转成 ArrayList 返回；与 multiRecall 不同，任何通道都不会被跳过。
     */
    public List<Long> multiRecallAll(RecallContext ctx) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (RecallStrategy strategy : registry.getAllStrategies()) {
            ids.addAll(strategy.recall(ctx));
        }
        return new ArrayList<>(ids);
    }
}
