package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
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
    private RedisTemplate<String, Object> stringRedisTemplate;
    

    @Override
    public void saveBlog(){


    }
    @Override
    public Result likeBlog(Long id){
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            return Result.ok("点赞成功");
        }


    }

}
