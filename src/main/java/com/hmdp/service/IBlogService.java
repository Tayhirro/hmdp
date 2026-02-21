package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    Result likeBlog(Long id);   // user: 点赞 blog
    Result queryBlogLikes(Long id); // user: 查询 blog的 likes数量
    Result queryBlogOfFollow(Long max, Integer offset); // user: 查询关注的blog
    Result queryHotBlog(Integer current); // user: 查询热门blog
    
    Result saveBlog(Blog blog); // author: 保存 blog


}
