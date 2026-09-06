package com.hmdp.service.strategy.ranking.impl;

/*
 * 现实业务背景：用户打开关注流时通常希望先看到关注作者最新发布的内容。
 * 实际触发：following 模式完成关注召回后调用本策略，按发布时间降序、同时间按 blogId 降序排列。
 *
 * 实现要点：
 * 1. score 就是博客发布时间 createTime 转成的 UTC 毫秒时间戳（toEpochMilli），
 *    所以"分数高 = 发布得晚 = 内容更新"；createTime 为 null 时记 0 分（排最后）。
 *    注意这里的分数是时间戳量级，和 WeightedRankingStrategy 那种 0~1 的分数没有可比性。
 * 2. rank() 按分数降序排；两篇博客发布时间完全相同（同一毫秒）时，再按 blogId 降序，
 *    保证顺序稳定可复现，翻页时不因排序抖动出现重复或遗漏。
 * 3. 策略注册名为 "time"，由 FeedMode 的 FOLLOWING 模式指定（following -> "time"）。
 * 4. 本策略完全不读 {@link RankingContext}（排序输入信号：用户、当前时间、作者亲和度等），
 *    关注流对所有用户都是同一套"最新优先"规则。
 */

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

@Component
public class SimpleTimeRankingStrategy implements RankingStrategy<Blog> {

    /**
     * 返回博客发布时间的 UTC 毫秒时间戳，即"分数高 = 发布晚 = 内容新"；createTime 为 null 记 0 分（排最后）。
     * 使用场景：仅被本类 rank 的排序比较器调用；外部经 {@link RankingStrategy}（排序策略接口）
     * 以 "time" 名称取用，关注流（following 模式）的排序就是它。
     * 实现要点：blog.createTime.toInstant(ZoneOffset.UTC).toEpochMilli()；
     * 分数是时间戳量级，与 "simple"/"weighted" 那种个位数加权分没有可比性。
     */
    @Override
    public double score(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null) return 0;
        return blog.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 对传入 List 按发布时间降序就地排序（List.sort，返回值就是原列表）；同一毫秒内 blogId 大者排前。
     * 使用场景：被 BlogFeedService.rebuild 经 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}
     * （排序策略注册表）以 "time" 名调用（FeedMode.FOLLOWING 映射 "time"）；
     * 测试 RankingStrategyOrderTest 也调用验证同分规则。
     * 实现要点：Double.compare(score(b), score(a)) 降序；blogId 为 null 按 Long.MIN_VALUE（视为最小）
     * 参与比较，再 Long.compare(bId, aId)，保证翻页不因排序抖动出现重复或遗漏；不读传入的 RankingContext。
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
     * 返回策略注册名 "time"。
     * 使用场景：被 {@link com.hmdp.service.strategy.ranking.RankingStrategyRegistry}（排序策略注册表）
     * 的 init 以返回值作键登记（@PostConstruct 建 Map）；FeedMode.FOLLOWING 的 getRankingStrategy()
     * 返回 "time"，BlogFeedService.rebuild 据此选中本策略。
     * 实现要点：返回常量字符串 "time"，是注册表的查找键。
     */
    @Override
    public String getStrategyName() {
        return "time";
    }
}
