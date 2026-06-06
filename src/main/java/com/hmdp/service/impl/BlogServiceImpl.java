package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import com.hmdp.service.strategy.ranking.RankingStrategyRegistry;
import com.hmdp.service.feedcache.FeedCacheService;
import com.hmdp.service.strategy.recall.RecallContext;
import com.hmdp.service.strategy.recall.RecallOrchestrator;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private static final int CANDIDATE_POOL_SIZE = 200;
    private static final int CANDIDATES_PER_CHANNEL = 100;
    private static final int PAGE_SIZE = 50;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Resource
    private BlogLikeMapper blogLikeMapper;

    @Resource
    private RankingStrategyRegistry rankingStrategyRegistry;

    @Resource
    private RecallOrchestrator recallOrchestrator;

    @Resource
    private FeedCacheService feedCacheService;

    @Override
    @Transactional
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String userIdStr = userId.toString();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userIdStr);
        boolean isLiked = score != null;

        if (!isLiked) {
            BlogLike likeRecord = queryLikeRecordFromDb(id, userId);
            if (likeRecord != null) {
                isLiked = true;
                stringRedisTemplate.opsForZSet().add(key, userIdStr, toEpochMilli(likeRecord.getCreateTime()));
            }
        }
        if (isLiked) {
            int deleted = blogLikeMapper.delete(new LambdaQueryWrapper<BlogLike>()
                    .eq(BlogLike::getBlogId, id)
                    .eq(BlogLike::getUserId, userId));
            if (deleted > 0) {
                boolean success = update().setSql("liked = IF(liked > 0, liked - 1, 0)").eq("id", id).update();
                if (!success) {
                    throw new IllegalStateException("更新点赞数量失败");
                }
            }
            stringRedisTemplate.opsForZSet().remove(key, userIdStr);
            return Result.ok("取消点赞成功");
        } else {
            LocalDateTime now = LocalDateTime.now();
            BlogLike blogLike = new BlogLike()
                    .setBlogId(id)
                    .setUserId(userId)
                    .setCreateTime(now);
            try {
                blogLikeMapper.insert(blogLike);
            } catch (DuplicateKeyException e) {
                BlogLike existed = queryLikeRecordFromDb(id, userId);
                stringRedisTemplate.opsForZSet().add(key, userIdStr, toEpochMilli(existed == null ? now : existed.getCreateTime()));
                return Result.ok("点赞成功");
            }

            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (!success) {
                throw new IllegalStateException("更新点赞数量失败");
            }
            stringRedisTemplate.opsForZSet().add(key, userIdStr, toEpochMilli(now));
            return Result.ok("点赞成功");
        }
    }

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        save(blog);
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikedFlag(blog);
        });
        return Result.ok(blogs);
    }

    private void fillBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        if (userId == null) return;
        User user = userService.getById(userId);
        if (user == null) return;
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void fillBlogLikedFlag(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            blog.setIsLike(Boolean.FALSE);
            return;
        }

        String key = BLOG_LIKED_KEY + blog.getId();
        String userIdStr = user.getId().toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, userIdStr);
        if (score != null) {
            blog.setIsLike(Boolean.TRUE);
            return;
        }

        BlogLike likeRecord = queryLikeRecordFromDb(blog.getId(), user.getId());
        blog.setIsLike(likeRecord != null);
        if (likeRecord != null) {
            stringRedisTemplate.opsForZSet().add(key, userIdStr, toEpochMilli(likeRecord.getCreateTime()));
        }
    }

    @Override
    public Result queryBlogLikes(Long id, Long max, Integer offset) {
        if (id == null) {
            return Result.fail("博客ID不能为空");
        }

        int from = (offset == null || offset < 0) ? 0 : offset;
        long maxScore = (max == null || max <= 0) ? Long.MAX_VALUE : max;
        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        String key = BLOG_LIKED_KEY + id;

        Set<ZSetOperations.TypedTuple<String>> tupleSet = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, from, pageSize);
        List<Long> userIds = new ArrayList<>(pageSize);
        List<Long> scoreList = new ArrayList<>(pageSize);

        if (tupleSet != null && !tupleSet.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : tupleSet) {
                String userIdStr = tuple.getValue();
                if (userIdStr == null) continue;
                try {
                    userIds.add(Long.valueOf(userIdStr));
                } catch (NumberFormatException ignore) {
                    continue;
                }
                scoreList.add(tuple.getScore() == null ? 0L : tuple.getScore().longValue());
            }
        } else {
            List<BlogLike> dbLikes = queryLikesFromDb(id, maxScore, from, pageSize);
            if (dbLikes.isEmpty()) {
                return Result.ok(emptyLikesResult(maxScore, from));
            }
            for (BlogLike blogLike : dbLikes) {
                if (blogLike.getUserId() == null) continue;
                long score = toEpochMilli(blogLike.getCreateTime());
                userIds.add(blogLike.getUserId());
                scoreList.add(score);
                stringRedisTemplate.opsForZSet().add(key, blogLike.getUserId().toString(), score);
            }
        }

        if (userIds.isEmpty()) {
            return Result.ok(emptyLikesResult(maxScore, from));
        }

        List<UserDTO> dtoList = hydrateLikeUsers(userIds);
        long minTime = scoreList.get(scoreList.size() - 1);
        int sameCount = 0;
        for (int i = scoreList.size() - 1; i >= 0; i--) {
            if (scoreList.get(i).equals(minTime)) {
                sameCount++;
            } else {
                break;
            }
        }
        int nextOffset = (minTime == maxScore ? from : 0) + sameCount;

        Map<String, Object> data = new HashMap<>(8);
        data.put("list", dtoList);
        data.put("minTime", minTime);
        data.put("nextOffset", nextOffset);
        data.put("hasMore", scoreList.size() == pageSize);
        return Result.ok(data);
    }

    @Override
    public Result queryBlogOfFollow(Double lastScore, Long lastId, String rankingStrategyName) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 1. Try cache first
        int fetchSize = PAGE_SIZE + 1;
        List<Long> cachedIds = feedCacheService.getCachedIds(userId, rankingStrategyName, lastScore, fetchSize);

        if (cachedIds != null) {
            boolean hasMore = cachedIds.size() > PAGE_SIZE;
            List<Long> pageIds = cachedIds.size() > PAGE_SIZE
                    ? cachedIds.subList(0, PAGE_SIZE)
                    : cachedIds;

            String idStr = pageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<Blog> blogs = query().in("id", pageIds).last("ORDER BY FIELD(id," + idStr + ")").list();

            blogs.forEach(blog -> {
                fillBlogUser(blog);
                fillBlogLikedFlag(blog);
            });

            Long newLastId = pageIds.isEmpty() ? null : pageIds.get(pageIds.size() - 1);
            Double newLastScore = newLastId != null
                    ? feedCacheService.getScore(userId, rankingStrategyName, newLastId)
                    : null;
            return Result.ok(new ScrollResult(blogs, newLastScore, newLastId, hasMore));
        }

        // 2. Cache miss: recall + rank + cache
        RankingStrategy<Blog> rankingStrategy = rankingStrategyRegistry.getStrategy(rankingStrategyName);

        Long maxTime = lastScore != null ? lastScore.longValue() : null;
        RecallContext recallCtx = RecallContext.builder()
                .userId(userId)
                .maxTime(maxTime)
                .limit(CANDIDATES_PER_CHANNEL)
                .build();
        List<Long> candidateIds = recallOrchestrator.multiRecallAll(recallCtx);
        boolean mayHaveMore = candidateIds.size() >= CANDIDATE_POOL_SIZE;
        if (candidateIds.isEmpty()) {
            return Result.ok(new ScrollResult(new ArrayList<>(), null, null, false));
        }

        int capped = Math.min(candidateIds.size(), CANDIDATE_POOL_SIZE);
        List<Long> idList = candidateIds.subList(0, capped);

        String idStr = idList.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Blog> blogs = query().in("id", idList).last("ORDER BY FIELD(id," + idStr + ")").list();

        Map<Long, Double> authorAffinity = new HashMap<>();
        for (Blog blog : blogs) {
            authorAffinity.putIfAbsent(blog.getUserId(), 0.5);
        }
        RankingContext ctx = RankingContext.builder()
                .currentUserId(userId)
                .now(LocalDateTime.now())
                .authorAffinity(authorAffinity)
                .build();

        blogs = rankingStrategy.rank(blogs, ctx);

        // Store in cache
        feedCacheService.cacheFeed(userId, rankingStrategyName, blogs, rankingStrategy, ctx);

        boolean hasMore = mayHaveMore || blogs.size() > PAGE_SIZE;
        List<Blog> pageList = blogs.size() > PAGE_SIZE
                ? blogs.subList(0, PAGE_SIZE)
                : blogs;

        pageList.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikedFlag(blog);
        });

        Double newLastScore = pageList.isEmpty() ? null
                : rankingStrategy.score(pageList.get(pageList.size() - 1), ctx);
        Long newLastId = pageList.isEmpty() ? null
                : pageList.get(pageList.size() - 1).getId();
        return Result.ok(new ScrollResult(pageList, newLastScore, newLastId, hasMore));
    }

    private BlogLike queryLikeRecordFromDb(Long blogId, Long userId) {
        return blogLikeMapper.selectOne(new LambdaQueryWrapper<BlogLike>()
                .select(BlogLike::getId, BlogLike::getBlogId, BlogLike::getUserId, BlogLike::getCreateTime)
                .eq(BlogLike::getBlogId, blogId)
                .eq(BlogLike::getUserId, userId)
                .last("LIMIT 1"));
    }

    private List<BlogLike> queryLikesFromDb(Long blogId, long maxScore, int offset, int pageSize) {
        if (maxScore == Long.MAX_VALUE) {
            return blogLikeMapper.selectList(new LambdaQueryWrapper<BlogLike>()
                    .select(BlogLike::getId, BlogLike::getUserId, BlogLike::getCreateTime)
                    .eq(BlogLike::getBlogId, blogId)
                    .orderByDesc(BlogLike::getCreateTime, BlogLike::getId)
                    .last("LIMIT " + pageSize));
        }

        LocalDateTime maxTime = toLocalDateTime(maxScore);
        List<BlogLike> result = new ArrayList<>(pageSize);

        List<BlogLike> sameTime = blogLikeMapper.selectList(new LambdaQueryWrapper<BlogLike>()
                .select(BlogLike::getId, BlogLike::getUserId, BlogLike::getCreateTime)
                .eq(BlogLike::getBlogId, blogId)
                .eq(BlogLike::getCreateTime, maxTime)
                .orderByDesc(BlogLike::getId)
                .last("LIMIT " + offset + "," + pageSize));
        result.addAll(sameTime);

        int remain = pageSize - result.size();
        if (remain > 0) {
            List<BlogLike> older = blogLikeMapper.selectList(new LambdaQueryWrapper<BlogLike>()
                    .select(BlogLike::getId, BlogLike::getUserId, BlogLike::getCreateTime)
                    .eq(BlogLike::getBlogId, blogId)
                    .lt(BlogLike::getCreateTime, maxTime)
                    .orderByDesc(BlogLike::getCreateTime, BlogLike::getId)
                    .last("LIMIT " + remain));
            result.addAll(older);
        }
        return result;
    }

    private List<UserDTO> hydrateLikeUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String idStr = StrUtil.join(",", userIds);
        List<User> users = userService.query()
                .in("id", userIds)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        return users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
    }

    private Map<String, Object> emptyLikesResult(long maxScore, int offset) {
        Map<String, Object> data = new HashMap<>(8);
        data.put("list", Collections.emptyList());
        data.put("minTime", maxScore);
        data.put("nextOffset", offset);
        data.put("offset", offset);
        data.put("hasMore", false);
        return data;
    }

    private long toEpochMilli(LocalDateTime time) {
        LocalDateTime safeTime = time == null ? LocalDateTime.now() : time;
        return safeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
