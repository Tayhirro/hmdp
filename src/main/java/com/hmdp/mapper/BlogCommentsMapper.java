package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.BlogComments;

/**
 * 博客评论表 tb_blog_comments 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。使用方：
 * 1. BlogCommentsServiceImpl（继承 ServiceImpl 并注入本接口为 commentsMapper）：
 *    createComment 插入评论、queryComments 按博客和游标查询评论及回复、deleteComment 查询并删除评论。
 * 2. BlogCommandService.delete：删除博客时按 blog_id 删除该博客的全部评论。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {

}
