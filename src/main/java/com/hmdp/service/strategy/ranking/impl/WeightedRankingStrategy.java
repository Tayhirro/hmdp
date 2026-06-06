package com.hmdp.service.strategy.ranking.impl;

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
    private static final double SHARE_WEIGHT = 0.4;
    private static final double FRESHNESS_WEIGHT = 0.3;
    private static final double NOT_INTERESTED_WEIGHT = -0.5;

    @Override
    public double score(Blog blog, RankingContext ctx) {
        double pLike = estimateLikeProb(blog, ctx);
        double pComment = estimateCommentProb(blog, ctx);
        double pDislike = estimateNotInterestedProb(blog, ctx);
        double freshness = calcFreshness(blog, ctx);

        return LIKE_WEIGHT * pLike
                + COMMENT_WEIGHT * pComment
                + FRESHNESS_WEIGHT * freshness
                + NOT_INTERESTED_WEIGHT * pDislike;
    }

    @Override
    public List<Blog> rank(List<Blog> blogs, RankingContext context) {
        blogs.sort((a, b) -> Double.compare(score(b, context), score(a, context)));
        return blogs;
    }

    @Override
    public String getStrategyName() {
        return "weighted";
    }

    private double estimateLikeProb(Blog blog, RankingContext ctx) {
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

    private double estimateCommentProb(Blog blog, RankingContext ctx) {
        int comments = blog.getComments() != null ? blog.getComments() : 0;
        return (double) comments / (comments + 15);
    }

    private double estimateNotInterestedProb(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null) return 0.3;
        long ageDays = ChronoUnit.DAYS.between(blog.getCreateTime(), ctx.getNow());
        if (ageDays > 7) return 0.6;
        if (ageDays > 3) return 0.3;
        return 0.1;
    }

    private double calcFreshness(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null || ctx.getNow() == null) return 0;
        long ageHours = ChronoUnit.HOURS.between(blog.getCreateTime(), ctx.getNow());
        return 1.0 / (1.0 + ageHours / 12.0);
    }
}
