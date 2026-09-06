package com.hmdp.service.strategy.ranking.impl;

/*
 * 现实业务背景：用户打开为你推荐时，需要把熟悉作者、互动热度和内容新鲜度组合成可解释的排序。
 * 实际触发：for_you 模式召回候选后调用本策略；它输出启发式顺序，不代表训练得到的点击概率。
 *
 * 排序分 = 1.0 * 点赞信号 + 0.6 * 评论信号 + 0.3 * 新鲜度 - 0.5 * 陈旧信号。
 * 四条权重定义在常量 LIKE_WEIGHT / COMMENT_WEIGHT / FRESHNESS_WEIGHT / NOT_INTERESTED_WEIGHT 中，
 * 各信号的计算公式（以代码为准）：
 * 1. 点赞信号 likeSignal（权重 1.0）：liked / (liked + 20)，liked 是博客的累计点赞数
 *    （20 赞约 0.5，180 赞约 0.9）。若当前用户给该作者点过赞超过 3 次
 *    （{@link RankingContext}（排序输入信号：用户、当前时间、作者亲和度等）的
 *    authorInteractionCount 中该作者计数 > 3），再乘 1.3 放大，但不超过 1.0。
 * 2. 评论信号 commentSignal（权重 0.6）：comments / (comments + 15)，comments 是博客评论数
 *    （15 条约 0.5，135 条约 0.9）。
 * 3. 新鲜度 freshness（权重 0.3）：1 / (1 + 博客年龄小时数 / 12)，刚发布约 1.0，
 *    每过 12 小时约衰减一半（12 小时前约 0.5，24 小时前约 0.33）。年龄 = now - createTime。
 * 4. 陈旧信号 staleSignal（权重 -0.5，起惩罚作用）：按博客年龄整天数分档——
 *    超过 7 天记 0.6，超过 3 天记 0.3，3 天以内记 0.1，createTime 为 null 记 0.3。
 *    因此一篇超过 7 天的旧博客会被扣 0.5 * 0.6 = 0.3 分。
 * 举例：一篇发布于 48 小时前（新鲜度 = 1/(1+48/12) = 0.2，陈旧信号 0.1）、30 赞、6 条评论、
 * 作者是"点过 4 次赞的熟面孔"的博客，得分 = 1.0 * min(1, 30/50*1.3) + 0.6 * 6/21 + 0.3 * 0.2 - 0.5 * 0.1
 * ≈ 0.78 + 0.17 + 0.06 - 0.05 = 0.96。
 * 同分时按 blogId 降序；rank() 直接对传入 List 就地排序。策略注册名为 "weighted"，
 * 由 FeedMode 的 FOR_YOU 模式指定（for_you -> "weighted"）。
 */

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class WeightedRankingStrategy implements RankingStrategy<Blog> {

    private static final double LIKE_WEIGHT = 1.0;
    private static final double COMMENT_WEIGHT = 0.6;
    private static final double FRESHNESS_WEIGHT = 0.3;
    private static final double NOT_INTERESTED_WEIGHT = -0.5;

    /**
     * 计算加权重排分：1.0 * 点赞信号 + 0.6 * 评论信号 + 0.3 * 新鲜度 - 0.5 * 陈旧信号
     * （权重常量 LIKE_WEIGHT / COMMENT_WEIGHT / FRESHNESS_WEIGHT / NOT_INTERESTED_WEIGHT）。
     * 使用场景：仅被本类 rank 的排序比较器调用；外部经 {@link RankingStrategy}（排序策略接口）
     * 以 "weighted" 名称取用，为你推荐（for_you 模式）的排序就是它。
     * 实现要点：点赞/评论/新鲜度信号各归一到 0~1（点赞信号可经熟悉作者加成但封顶 1.0），
     * 陈旧信号按博客年龄分档取 0.1/0.3/0.6；结果是可解释的启发式相对分，不是校准过的点击概率。
     */
    @Override
    public double score(Blog blog, RankingContext ctx) {
        double likeSignal = likeSignal(blog, ctx);
        double commentSignal = commentSignal(blog);
        double staleSignal = staleSignal(blog, ctx);
        double freshness = calcFreshness(blog, ctx);

        // 这是可解释的启发式排序分，不冒充经过训练和校准的点击概率。
        return LIKE_WEIGHT * likeSignal
                + COMMENT_WEIGHT * commentSignal
                + FRESHNESS_WEIGHT * freshness
                + NOT_INTERESTED_WEIGHT * staleSignal;
    }

    /**
     * 对传入 List 按分数降序就地排序（List.sort，返回值就是原列表）；同分时 blogId 大者排前。
     * 使用场景：被 BlogFeedService.rebuild 经 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}
     * （排序策略注册表）以 "weighted" 名调用（FeedMode.FOR_YOU 映射 "weighted"），发生在曝光过滤之后、
     * 作者打散（diversifyAuthors）之前；测试 RankingStrategyOrderTest 也调用验证同分规则。
     * 实现要点：Double.compare(score(b), score(a)) 降序；blogId 为 null 按 Long.MIN_VALUE（视为最小）
     * 参与比较，再 Long.compare(bId, aId)。
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
     * 返回策略注册名 "weighted"。
     * 使用场景：被 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}（排序策略注册表）
     * 的 init 以返回值作键登记（@PostConstruct 建 Map）；FeedMode.FOR_YOU 的 getRankingStrategy()
     * 返回 "weighted"，BlogFeedService.rebuild 据此选中本策略。
     * 实现要点：返回常量字符串 "weighted"，是注册表的查找键。
     */
    @Override
    public String getStrategyName() {
        return "weighted";
    }

    /**
     * 点赞信号：liked / (liked + 20)，当前用户给该作者点过赞超过 3 次时再乘 1.3 且封顶 1.0。
     * 使用场景：仅被本类 score 调用，作为四路加权之一（权重 1.0，占比最大）。
     * 实现要点：liked 取 blog.getLiked()，null 按 0（0 赞得 0 分，20 赞约 0.5，180 赞约 0.9）；
     * 熟悉作者判定读 {@link RankingContext}（排序输入信号：用户、当前时间、作者亲和度等）的
     * authorInteractionCount：该作者计数 > 3 时执行 Math.min(1.0, baseProb * 1.3)；
     * 计数由 BlogFeedService.rankingContext 从 ForYouRecall 写入 ctx.extra 的点赞聚合行算出。
     */
    private double likeSignal(Blog blog, RankingContext ctx) {
        int liked = blog.getLiked() != null ? blog.getLiked() : 0;
        double baseProb = (double) liked / (liked + 20);

        if (ctx.getAuthorInteractionCount() != null) {
            Integer myLikes = ctx.getAuthorInteractionCount().getOrDefault(blog.getUserId(), 0);
            if (myLikes > 3) {
                baseProb = Math.min(1.0, baseProb * 1.3);
            }
        }
        return baseProb;
    }

    /**
     * 评论信号：comments / (comments + 15)，15 条约 0.5、135 条约 0.9，随评论数趋近 1 但永不封顶。
     * 使用场景：仅被本类 score 调用，作为四路加权之一（权重 0.6）。
     * 实现要点：comments 取 blog.getComments()，null 按 0 计算（0 评论得 0 分）。
     */
    private double commentSignal(Blog blog) {
        int comments = blog.getComments() != null ? blog.getComments() : 0;
        return (double) comments / (comments + 15);
    }

    /**
     * 陈旧惩罚信号：按博客年龄整天数分档——超过 7 天记 0.6，超过 3 天记 0.3，其余记 0.1；createTime 为 null 记 0.3。
     * 使用场景：仅被本类 score 调用，以权重 -0.5（NOT_INTERESTED_WEIGHT）作为减分项，内容越陈旧扣得越多。
     * 实现要点：年龄 = ChronoUnit.DAYS.between(blog.createTime, ctx.now)；一篇超过 7 天的旧博客
     * 最多被扣 0.5 * 0.6 = 0.3 分；注意 3 天以内的博客也固定扣 0.5 * 0.1 = 0.05 分，没有"零惩罚"档。
     */
    private double staleSignal(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null) return 0.3;
        long ageDays = ChronoUnit.DAYS.between(blog.getCreateTime(), ctx.getNow());
        if (ageDays > 7) return 0.6;
        if (ageDays > 3) return 0.3;
        return 0.1;
    }

    /**
     * 新鲜度因子：1.0 / (1.0 + 博客年龄小时数 / 12.0)，刚发布约 1.0，每过 12 小时约衰减一半
     * （比 "simple" 策略的 24 小时半衰期更快，更强调新内容）。
     * 使用场景：仅被本类 score 调用，作为四路加权之一（权重 0.3）。
     * 实现要点：年龄 = ChronoUnit.HOURS.between(blog.createTime, ctx.now)；
     * createTime 或 ctx.now 为 null 时记 0 分。
     */
    private double calcFreshness(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null || ctx.getNow() == null) return 0;
        long ageHours = ChronoUnit.HOURS.between(blog.getCreateTime(), ctx.getNow());
        return 1.0 / (1.0 + ageHours / 12.0);
    }
}
