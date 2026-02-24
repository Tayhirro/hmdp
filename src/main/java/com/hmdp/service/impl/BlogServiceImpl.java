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
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.service.strategy.BlogQueryContext;
import com.hmdp.service.strategy.BlogRankStrategy;
import com.hmdp.service.strategy.BlogRankStrategyRouter;
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
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Resource
    private BlogRankStrategyRouter strategyRouter;

    @Resource
    private BlogLikeMapper blogLikeMapper;

    @Override
    @Transactional
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String userIdStr = userId.toString();
        String key = BLOG_LIKED_KEY + id;
        // 缓存---数据库
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
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            return Result.ok(blog.getId());
        }
        // 循环推送到每个粉丝的feed ZSet
        follows.forEach(follow -> {
            stringRedisTemplate.opsForZSet().add(FEED_KEY + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis());
        });
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        BlogRankStrategy hotStrategy = strategyRouter.get("hot");
        if (hotStrategy == null) {
            return Result.fail("热门策略未配置");
        }
        BlogQueryContext ctx = new BlogQueryContext();
        ctx.setScene("hot");
        ctx.setCurrent(current);
        ctx.setPageSize(SystemConstants.MAX_PAGE_SIZE);
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser != null) {
            ctx.setUserId(currentUser.getId());
        }
        // page 排序逻辑 -- 推荐系统排序
        Page<Long> idPage = hotStrategy.rank(ctx);
        List<Long> ids = idPage.getRecords();
        if (ids == null || ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikedFlag(blog);
        });
        return Result.ok(blogs);
    }

    private void fillBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        if (userId == null) {
            return;
        }
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
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
        // max 最大时间戳 offset 偏离max时间戳跳过多少 pagesize 需要数据多少
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

        if (tupleSet != null && !tupleSet.isEmpty()) {   // 如果查到
            for (ZSetOperations.TypedTuple<String> tuple : tupleSet) {
                String userIdStr = tuple.getValue();
                if (userIdStr == null) {
                    continue;
                }
                try {
                    userIds.add(Long.valueOf(userIdStr));
                } catch (NumberFormatException ignore) {
                    continue;
                }
                scoreList.add(tuple.getScore() == null ? 0L : tuple.getScore().longValue());
            }
        } else {    //db 查询
            List<BlogLike> dbLikes = queryLikesFromDb(id, maxScore, from, pageSize);
            if (dbLikes.isEmpty()) {
                return Result.ok(emptyLikesResult(maxScore, from));
            }
            for (BlogLike blogLike : dbLikes) {
                if (blogLike.getUserId() == null) {
                    continue;
                }
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
        long minTime = -1L;
        int sameCount = 0;
        for (int i = scoreList.size() - 1; i >= 0; i--) {
            if (scoreList.get(i).equals(minTime)) {
                sameCount++;
            } else {
                break;
            }
        }
        int nextOffset = (minTime == maxScore ? from : 0) + sameCount;  // min + offset 分情况

        Map<String, Object> data = new HashMap<>(8);
        data.put("list", dtoList);
        data.put("minTime", minTime);
        data.put("nextOffset", nextOffset);
        data.put("hasMore", scoreList.size() == pageSize);
        return Result.ok(data);
    }

    // 查询我关注的博主的博客
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        long maxScore = (max == null || max <= 0) ? Long.MAX_VALUE : max;
        int os = (offset == null || offset < 0) ? 0 : offset;

        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, os, SystemConstants.DEFAULT_PAGE_SIZE);
        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(maxScore);
            empty.setOffset(os);
            return Result.ok(empty);
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0L;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            String blogIdStr = tuple.getValue();
            if (blogIdStr == null) {
                continue;
            }
            ids.add(Long.valueOf(blogIdStr));
            long time = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
            if (time == minTime) {
                nextOffset++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }
        if (ids.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(maxScore);
            empty.setOffset(os);
            return Result.ok(empty);
        }

        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikedFlag(blog);
        });

        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(nextOffset);
        return Result.ok(result);
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
        }   //选择最后pagesize条数据
        LocalDateTime maxTime = toLocalDateTime(maxScore); 
        List<BlogLike> result = new ArrayList<>(pageSize);


        wrapper.orderByDesc(BlogLike::getCreateTime, BlogLike::getId)
                .last("LIMIT " + offset + "," + pageSize);
        return blogLikeMapper.selectList(wrapper);
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
