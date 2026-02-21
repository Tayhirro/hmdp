package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.service.strategy.BlogQueryContext;
import com.hmdp.service.strategy.BlogRankStrategy;
import com.hmdp.service.strategy.BlogRankStrategyRouter;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Resource
    private BlogRankStrategyRouter strategyRouter;

    @Override
    public Result likeBlog(Long id){
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (!success) {
                return Result.fail("点赞失败");
            }
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            return Result.ok("点赞成功");
        }else{
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (!success) {
                return Result.fail("取消点赞失败");
            }
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            return Result.ok("取消点赞成功");
        }
    }
    @Override
    public Result saveBlog(Blog blog) {
        
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
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }


    @Override
    public Result queryBlogLikes(Long id, Integer offset) {
        if (id == null) {
            return Result.fail("博客ID不能为空");
        }
        int from = (offset == null || offset < 0) ? 0 : offset;
        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        String key = BLOG_LIKED_KEY + id;
        Set<String> userIdSet = stringRedisTemplate.opsForZSet().reverseRange(key, from, from + pageSize - 1);
        if (userIdSet == null || userIdSet.isEmpty()) { //userIdSet 为空情况
            Map<String, Object> data = new HashMap<>();
            data.put("list", Collections.emptyList());
            data.put("nextOffset", from);
            data.put("hasMore", false);
            return Result.ok(data);
        }

        List<Long> userIds = userIdSet.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", userIds);
        
        // 根据id 查 users
        List<User> users = userService.query()
                .in("id", userIds)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        List<UserDTO> dtoList = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", dtoList);
        data.put("nextOffset", from + userIds.size());
        data.put("hasMore", userIds.size() == pageSize);
        return Result.ok(data);
    }

    // 查询我关注的博主的博客 
    @Override 
    public Result queryBlogOfFollow(Long max, Integer offset) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.fail("用户未登录");
        }
        long maxScore = (max == null || max <= 0) ? Long.MAX_VALUE : max;
        int os = (offset == null || offset < 0) ? 0 : offset;

        String key = FEED_KEY + currentUser.getId();
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

}
