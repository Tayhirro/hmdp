package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

public interface IBlogService extends IService<Blog> {
    Result likeBlog(Long id);
    Result queryBlogLikes(Long id, Long max, Integer offset);
    Result queryBlogOfFollow(Double lastScore, Long lastId, String rankingStrategy);
    Result queryHotBlog(Integer current);
    Result saveBlog(Blog blog);
}
