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
import com.hmdp.entity.FeedInbox;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.FeedInboxMapper;
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

    @Resource
    private FeedInboxMapper feedInboxMapper;

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
    @Transactional
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        save(blog);
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            return Result.ok(blog.getId());
        }
        long score = System.currentTimeMillis();
        // 循环推送到每个粉丝的feed：先落db inbox，再写redis热层
        follows.forEach(follow -> {
            Long recipientId = follow.getUserId();
            FeedInbox inbox = new FeedInbox()
                    .setRecipientId(recipientId)
                    .setBlogId(blog.getId())
                    .setScore(score)
                    .setCreateTime(LocalDateTime.now());
            try {
                feedInboxMapper.insert(inbox);
            } catch (DuplicateKeyException ignore) {
                // 幂等重试：唯一键(recipient_id, blog_id)保证不重复插入
            }
            addToInboxCache(recipientId, blog.getId(), score);
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
        //倒叙获取scoreList
        long minTime =  scoreList.get(scoreList.size() - 1); 
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

    // 查询我关注的博主的博客 查询inbox --->转换 blog
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        long maxScore = (max == null || max <= 0) ? Long.MAX_VALUE : max;
        int os = (offset == null || offset < 0) ? 0 : offset;
        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;

        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, os, pageSize);

        List<Long> ids = new ArrayList<>(pageSize);
        List<Long> scoreList = new ArrayList<>(pageSize);
        if (typedTuples != null && !typedTuples.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                String blogIdStr = tuple.getValue();
                if (blogIdStr == null) {
                    continue;
                }
                ids.add(Long.valueOf(blogIdStr));
                long time = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
                scoreList.add(time);
            }
        } else {
            // stage2: 读db inbox
            List<FeedInbox> inboxRows = queryInboxFromDb(userId, maxScore, os, pageSize);
            // stage3: db inbox为空时，按关注关系+博客表做冷层重建
            if (inboxRows.isEmpty()) {
                inboxRows = rebuildInboxFromFollowBlogs(userId, maxScore, os, pageSize);
                persistInboxRows(userId, inboxRows);
            }
            if (inboxRows.isEmpty()) {
                ScrollResult empty = new ScrollResult();
                empty.setList(Collections.emptyList());
                empty.setMinTime(maxScore);
                empty.setOffset(os);
                return Result.ok(empty);
            }
            for (FeedInbox row : inboxRows) {
                if (row.getBlogId() == null || row.getScore() == null) {
                    continue;
                }
                ids.add(row.getBlogId());
                scoreList.add(row.getScore());
                addToInboxCache(userId, row.getBlogId(), row.getScore());
            }
        }
        if (ids.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(maxScore);
            empty.setOffset(os);
            return Result.ok(empty);
        }
        long minTime = scoreList.get(scoreList.size() - 1);
        int sameCount = 0;
        for (int i = scoreList.size() - 1; i >= 0; i--) {
            if (scoreList.get(i).equals(minTime)) {
                sameCount++;
            } else {
                break;
            }
        }
        int nextOffset = (minTime == maxScore ? os : 0) + sameCount;

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

    private List<FeedInbox> queryInboxFromDb(Long recipientId, long maxScore, int offset, int pageSize) {
        if (maxScore == Long.MAX_VALUE) {
            return feedInboxMapper.selectList(new LambdaQueryWrapper<FeedInbox>()
                    .select(FeedInbox::getId, FeedInbox::getRecipientId, FeedInbox::getBlogId, FeedInbox::getScore)
                    .eq(FeedInbox::getRecipientId, recipientId)
                    .orderByDesc(FeedInbox::getScore, FeedInbox::getId)
                    .last("LIMIT " + pageSize));
        }

        List<FeedInbox> result = new ArrayList<>(pageSize);
        List<FeedInbox> sameScore = feedInboxMapper.selectList(new LambdaQueryWrapper<FeedInbox>()
                .select(FeedInbox::getId, FeedInbox::getRecipientId, FeedInbox::getBlogId, FeedInbox::getScore)
                .eq(FeedInbox::getRecipientId, recipientId)
                .eq(FeedInbox::getScore, maxScore)
                .orderByDesc(FeedInbox::getId)
                .last("LIMIT " + offset + "," + pageSize));
        result.addAll(sameScore);

        int remain = pageSize - result.size();
        if (remain > 0) {
            List<FeedInbox> older = feedInboxMapper.selectList(new LambdaQueryWrapper<FeedInbox>()
                    .select(FeedInbox::getId, FeedInbox::getRecipientId, FeedInbox::getBlogId, FeedInbox::getScore)
                    .eq(FeedInbox::getRecipientId, recipientId)
                    .lt(FeedInbox::getScore, maxScore)
                    .orderByDesc(FeedInbox::getScore, FeedInbox::getId)
                    .last("LIMIT " + remain));
            result.addAll(older);
        }
        return result;
    }

    private List<FeedInbox> rebuildInboxFromFollowBlogs(Long recipientId, long maxScore, int offset, int pageSize) {
        List<Long> followUserIds = followService.query()
                .eq("user_id", recipientId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        if (followUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .select(Blog::getId, Blog::getCreateTime)
                .in(Blog::getUserId, followUserIds)
                .orderByDesc(Blog::getCreateTime, Blog::getId);
        if (maxScore < Long.MAX_VALUE) {
            wrapper.le(Blog::getCreateTime, toLocalDateTime(maxScore));
        }
        if (offset > 0) {
            wrapper.last("LIMIT " + offset + "," + pageSize);
        } else {
            wrapper.last("LIMIT " + pageSize);
        }
        List<Blog> blogs = list(wrapper);
        if (blogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<FeedInbox> rows = new ArrayList<>(blogs.size());
        for (Blog item : blogs) {
            if (item.getId() == null) {
                continue;
            }
            rows.add(new FeedInbox()
                    .setRecipientId(recipientId)
                    .setBlogId(item.getId())
                    .setScore(toEpochMilli(item.getCreateTime()))
                    .setCreateTime(LocalDateTime.now()));
        }
        return rows;
    }

    private void persistInboxRows(Long recipientId, List<FeedInbox> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (FeedInbox row : rows) {
            row.setRecipientId(recipientId);
            try {
                feedInboxMapper.insert(row);
            } catch (DuplicateKeyException ignore) {
                // 幂等重建
            }
        }
    }

    private void addToInboxCache(Long recipientId, Long blogId, long score) {
        String key = FEED_KEY + recipientId;
        stringRedisTemplate.opsForZSet().add(key, blogId.toString(), score);
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size != null && size > SystemConstants.FEED_INBOX_CACHE_MAX_SIZE) {
            long removeEndRank = size - SystemConstants.FEED_INBOX_CACHE_MAX_SIZE - 1;
            stringRedisTemplate.opsForZSet().removeRange(key, 0, removeEndRank);
        }
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

        int remain = pageSize - result.size();  // 如果 remain 还有剩余 则直接 查后续的
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
