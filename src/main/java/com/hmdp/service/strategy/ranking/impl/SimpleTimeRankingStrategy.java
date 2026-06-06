package com.hmdp.service.strategy.ranking.impl;

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

@Component
public class SimpleTimeRankingStrategy implements RankingStrategy<Blog> {

    @Override
    public double score(Blog blog, RankingContext ctx) {
        if (blog.getCreateTime() == null) return 0;
        return blog.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public List<Blog> rank(List<Blog> blogs, RankingContext context) {
        blogs.sort((a, b) -> Double.compare(score(b, context), score(a, context)));
        return blogs;
    }

    @Override
    public String getStrategyName() {
        return "time";
    }
}
