package com.hmdp.service.strategy.ranking.impl;

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

    @Override
    public double score(Blog blog, RankingContext ctx) {
        double recency = calcRecency(blog, ctx);
        double popularity = calcPopularity(blog);
        double affinity = calcAffinity(blog, ctx);
        return RECENCY_WEIGHT * recency + POPULARITY_WEIGHT * popularity + AFFINITY_WEIGHT * affinity;
    }

    @Override
    public List<Blog> rank(List<Blog> blogs, RankingContext context) {
        return blogs;
    }

    @Override
    public String getStrategyName() {
        return "simple";
    }

    private double calcRecency(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null || ctx.getNow() == null) return 0;
        long ageHours = ChronoUnit.HOURS.between(blog.getCreateTime(), ctx.getNow());
        return 1.0 / (1.0 + ageHours / 24.0);
    }

    private double calcPopularity(Blog blog) {
        int liked = blog.getLiked() != null ? blog.getLiked() : 0;
        return (double) liked / (liked + 10);
    }

    private double calcAffinity(Blog blog, RankingContext ctx) {
        return ctx.getAuthorAffinity() != null
                ? ctx.getAuthorAffinity().getOrDefault(blog.getUserId(), 0.5)
                : 0.5;
    }
}
