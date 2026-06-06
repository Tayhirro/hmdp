package com.hmdp.service.strategy.recall.impl.blog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.strategy.recall.RecallContext;
import com.hmdp.service.strategy.recall.RecallStrategy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class FollowFeedRecall implements RecallStrategy {

    private final Cache<Long, List<Long>> followCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Resource
    private IFollowService followService;

    @Resource
    private BlogMapper blogMapper;

    @Override
    public List<Long> recall(RecallContext ctx) {
        List<Long> followedIds = followCache.get(ctx.getUserId(), id -> {
            List<Follow> follows = followService.query().eq("user_id", id).list();
            return follows.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
        });
        if (followedIds == null || followedIds.isEmpty()) {
            return Collections.emptyList();
        }

        QueryWrapper<Blog> wrapper = new QueryWrapper<>();
        wrapper.select("id").in("user_id", followedIds);
        if (ctx.getMaxTime() != null && ctx.getMaxTime() > 0) {
            LocalDateTime maxDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(ctx.getMaxTime()), ZoneId.systemDefault());
            wrapper.lt("create_time", maxDateTime);
        }
        wrapper.orderByDesc("create_time");
        wrapper.last("LIMIT " + ctx.getLimit());

        List<Blog> blogs = blogMapper.selectList(wrapper);
        return blogs.stream().map(Blog::getId).collect(Collectors.toList());
    }

    @Override
    public String getStrategyName() {
        return "follow";
    }
}
