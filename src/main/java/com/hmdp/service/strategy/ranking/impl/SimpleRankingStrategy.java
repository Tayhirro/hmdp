package com.hmdp.service.strategy.ranking.impl;

/*
 * 现实业务背景：当系统需要一个通用内容排序基线时，综合新鲜度、热度和作者亲和度比只按单一字段更贴近浏览体验。
 * 实际触发：RankingStrategyRegistry 在请求指定 simple 或找不到策略时提供本实现；当前产品 Feed 主要使用 time/weighted。
 *
 * 排序分 = 0.5 * 新鲜度 + 0.3 * 热度 + 0.2 * 作者亲和度，各因子归一到 0~1：
 * 1. 新鲜度 recency = 1 / (1 + 博客年龄小时数 / 24)。刚发布约 1.0，每过 24 小时约衰减一半
 *    （如 1 天前约 0.5，2 天前约 0.33）。年龄 = now - createTime。
 * 2. 热度 popularity = 点赞数 / (点赞数 + 10)。10 赞约 0.5，90 赞约 0.9，越大越接近 1 但永不封顶。
 * 3. 作者亲和度 affinity：从 {@link RankingContext}（排序输入信号：用户、当前时间、作者亲和度等）的
 *    authorAffinity 取该博客作者的分值，作者不在 Map 里或上下文没带 Map 时默认 0.5。
 * 三条权重在常量 RECENCY_WEIGHT / POPULARITY_WEIGHT / AFFINITY_WEIGHT 中定义。
 * 同分时按 blogId 降序（新博客排前）；rank() 直接对传入 List 就地排序。
 */

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class SimpleRankingStrategy implements RankingStrategy<Blog> {

    private static final double RECENCY_WEIGHT = 0.5;
    private static final double POPULARITY_WEIGHT = 0.3;
    private static final double AFFINITY_WEIGHT = 0.2;

    /**
     * 计算基线加权分：0.5 * 新鲜度 + 0.3 * 热度 + 0.2 * 作者亲和度
     * （权重常量 RECENCY_WEIGHT / POPULARITY_WEIGHT / AFFINITY_WEIGHT）。
     * 使用场景：仅被本类 rank 的排序比较器调用；外部经 {@link RankingStrategy}（排序策略接口）
     * 以 "simple" 名称取用，是注册表查不到指定策略时的回退目标，生产 Feed 当前实际走 time/weighted。
     * 实现要点：三个因子各自由 calcRecency/calcPopularity/calcAffinity 归一到 0~1，加权和也在 0~1 内。
     */
    @Override
    public double score(Blog blog, RankingContext ctx) {
        double recency = calcRecency(blog, ctx);
        double popularity = calcPopularity(blog);
        double affinity = calcAffinity(blog, ctx);
        return RECENCY_WEIGHT * recency + POPULARITY_WEIGHT * popularity + AFFINITY_WEIGHT * affinity;
    }

    /**
     * 对传入 List 按分数降序就地排序（List.sort，返回值就是原列表）；同分时 blogId 大者排前。
     * 使用场景：被 BlogFeedService.rebuild 经 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}
     * （排序策略注册表）以 "simple" 名调用（生产 Feed 当前实际走 time/weighted）；
     * 测试 RankingStrategyOrderTest 也调用验证排序结果。
     * 实现要点：Double.compare(score(b), score(a)) 降序；blogId 为 null 按 Long.MIN_VALUE
     * （视为最小）参与比较，再 Long.compare(bId, aId)，保证顺序稳定可复现。
     */
    @Override
    public List<Blog> rank(List<Blog> blogs, RankingContext context) {
        blogs.sort((a, b) -> {
            int scoreCompare = Double.compare(score(b, context), score(a, context));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            long aId = a.getId() == null ? Long.MIN_VALUE : a.getId();
            long bId = b.getId() == null ? Long.MIN_VALUE : b.getId();
            return Long.compare(bId, aId);
        });
        return blogs;
    }

    /**
     * 返回策略注册名 "simple"。
     * 使用场景：被 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}（排序策略注册表）
     * 的 init 以返回值作键登记（@PostConstruct 建 Map）；getStrategy 找不到请求的名字时也按 "simple" 回退查找。
     * 实现要点：返回常量字符串 "simple"，是注册表的查找键。
     */
    @Override
    public String getStrategyName() {
        return "simple";
    }

    /**
     * 新鲜度因子：1.0 / (1.0 + 博客年龄小时数 / 24.0)，刚发布约 1.0，每过 24 小时约衰减一半。
     * 使用场景：仅被本类 score 调用，作为三项加权因子之一（权重 0.5）。
     * 实现要点：年龄 = ChronoUnit.HOURS.between(blog.createTime, ctx.now)；
     * createTime 或 ctx.now 为 null 时直接记 0 分（排最后）。
     */
    private double calcRecency(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null || ctx.getNow() == null) return 0;
        long ageHours = ChronoUnit.HOURS.between(blog.getCreateTime(), ctx.getNow());
        return 1.0 / (1.0 + ageHours / 24.0);
    }

    /**
     * 热度因子：点赞数 / (点赞数 + 10)，10 赞约 0.5、90 赞约 0.9，随点赞数增长趋近 1 但永不封顶。
     * 使用场景：仅被本类 score 调用，作为三项加权因子之一（权重 0.3）。
     * 实现要点：liked 取 blog.getLiked()，为 null 按 0 计算（结果 0 分）。
     */
    private double calcPopularity(Blog blog) {
        int liked = blog.getLiked() != null ? blog.getLiked() : 0;
        return (double) liked / (liked + 10);
    }

    /**
     * 作者亲和度因子：从 {@link RankingContext}（排序输入信号：用户、当前时间、作者亲和度等）
     * 的 authorAffinity 取该博客作者的分值，取不到时默认 0.5。
     * 使用场景：仅被本类 score 调用，作为三项加权因子之一（权重 0.2）。
     * 实现要点：ctx.authorAffinity 为 null 或作者不在 Map 里都取默认 0.5；
     * Map 值由 BlogFeedService.rankingContext 算出：min(1.0, 用户给该作者的点赞次数 / 5.0)。
     */
    private double calcAffinity(Blog blog, RankingContext ctx) {
        return ctx.getAuthorAffinity() != null
                ? ctx.getAuthorAffinity().getOrDefault(blog.getUserId(), 0.5)
                : 0.5;
    }
}
