package com.hmdp.service.feedcache;

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FeedCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long DEFAULT_TTL_MINUTES = 5;

    // 从 Redis ZSET 取出 userId+strategy 对应的缓存的 blogId 列表
    // lastScore = null 时取最新的 N 条，否则取分数 < lastScore 的 N 条
    // 返回 List<Long>（blogId），缓存不存在返回 null
    public List<Long> getCachedIds(Long userId, String strategy, Double lastScore, int count) {
        String key = RedisConstants.FEED_CACHE_KEY + userId + ":" + strategy;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            return null;
        }

        Set<String> ids;
        if (lastScore == null) {
            ids = stringRedisTemplate.opsForZSet().reverseRange(key, 0, count - 1);
        } else {
            double exclusiveMax = Math.nextAfter(lastScore, Double.NEGATIVE_INFINITY);
            ids = stringRedisTemplate.opsForZSet()
                    .reverseRangeByScore(key, Double.NEGATIVE_INFINITY, exclusiveMax, 0, count);
        }

        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return ids.stream().map(Long::valueOf).collect(Collectors.toList());
    }

    // 把排好序的博客列表存入 Redis ZSET
    // 存之前清空旧缓存，存完后设 TTL 自动过期
    // ZSET 的 member=blogId, score=rankingStrategy.score()
    public void cacheFeed(Long userId, String strategy,
                          List<Blog> blogs,
                          RankingStrategy<Blog> rankingStrategy,
                          RankingContext ctx) {
        String key = RedisConstants.FEED_CACHE_KEY + userId + ":" + strategy;
        stringRedisTemplate.delete(key);

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Blog blog : blogs) {
            double score = rankingStrategy.score(blog, ctx);
            String blogIdStr = String.valueOf(blog.getId());
            tuples.add(new ZSetOperations.TypedTuple<String>() {
                @Override
                public String getValue() { return blogIdStr; }
                @Override
                public Double getScore() { return score; }
                @Override
                public int compareTo(ZSetOperations.TypedTuple<String> o) {
                    return Double.compare(score, o.getScore());
                }
            });
        }

        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(key, tuples);
            stringRedisTemplate.expire(key, DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    // 查某个 blogId 在缓存 ZSET 中的分数
    // 用于获取分页游标 lastScore
    public Double getScore(Long userId, String strategy, Long blogId) {
        String key = RedisConstants.FEED_CACHE_KEY + userId + ":" + strategy;
        return stringRedisTemplate.opsForZSet().score(key, String.valueOf(blogId));
    }

    // 手动删除某个用户的 Feed 缓存
    // 比如用户发布了新博客后调用，下次请求时自动重建
    public void invalidate(Long userId, String strategy) {
        stringRedisTemplate.delete(RedisConstants.FEED_CACHE_KEY + userId + ":" + strategy);
    }
}
