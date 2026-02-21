package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
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
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
        // page 排序逻辑
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

}
