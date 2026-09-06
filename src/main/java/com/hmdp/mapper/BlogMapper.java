package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Blog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 博客（探店笔记）表 tb_blog 的数据访问接口。
 *
 * 自定义 SQL 的使用方：BlogCommandService（发布、编辑、删除）、BlogLikeService（点赞计数）、
 * BlogCommentsServiceImpl（评论计数）；通用 CRUD 的使用方还包括 BlogQueryService（列表与详情）、
 * BlogFeedService（Feed 候选按 ID 批量回读）、FollowFeedRecall 与 ForYouRecall（召回查询）、
 * MySqlBlogSearchService（按标题、正文搜索）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {

    /**
     * 写操作先锁定博客行：同一博客的编辑/删除串行执行，后到请求基于最新状态校验，
     * 避免两个事务各自按旧图片集合计算差异。
     * SQL：SELECT tb_blog 整行，WHERE id 等于参数，并附带 FOR UPDATE 行锁，事务结束时释放。
     *
     * 使用场景：BlogCommandService.loadOwnedBlogForWrite，被 update 和 delete 共用，
     * 用于锁定博客并校验存在性与作者所有权。
    */
    @Select("SELECT * FROM tb_blog WHERE id = #{id} FOR UPDATE")
    Blog selectByIdForUpdate(@Param("id") Long id);

    /**
     * 点赞数自增：UPDATE tb_blog SET liked = liked + 1 WHERE id 等于参数。
     *
     * 使用场景：BlogLikeService.like 插入点赞记录成功后同步博客计数。
     */
    @Update("UPDATE tb_blog SET liked = liked + 1 WHERE id = #{id}")
    int incrementLiked(@Param("id") Long id);

    /**
     * 点赞数自减且不为负：UPDATE tb_blog SET liked = IF(liked > 0, liked - 1, 0)
     * WHERE id 等于参数。
     *
     * 使用场景：BlogLikeService.unlike 删除点赞记录成功后同步博客计数。
     */
    @Update("UPDATE tb_blog SET liked = IF(liked > 0, liked - 1, 0) WHERE id = #{id}")
    int decrementLiked(@Param("id") Long id);

    /**
     * 评论数自增：UPDATE tb_blog SET comments = comments + 1 WHERE id 等于参数。
     *
     * 使用场景：BlogCommentsServiceImpl.createComment 插入评论成功后同步博客计数。
     */
    @Update("UPDATE tb_blog SET comments = comments + 1 WHERE id = #{id}")
    int incrementComments(@Param("id") Long id);

    /**
     * 评论数按数量递减且不为负：UPDATE tb_blog SET comments = GREATEST(comments - 参数 amount, 0)
     * WHERE id 等于参数；amount 是本次实际删除的评论行数（删一级评论时含其全部回复）。
     *
     * 使用场景：BlogCommentsServiceImpl.deleteComment 删除评论后同步博客计数。
     */
    @Update("UPDATE tb_blog SET comments = GREATEST(comments - #{amount}, 0) WHERE id = #{id}")
    int decrementComments(@Param("id") Long id, @Param("amount") int amount);

    /** 编辑字段白名单：所有权同时进入 WHERE，系统字段永远不会被实体回写覆盖。
     * SQL：UPDATE tb_blog SET shop_id、title、content、images 为参数值，
     * WHERE id 等于参数 id 且 user_id 等于参数 userId；影响行数为 0 即博客不存在或不属于当前用户。
     *
     * 使用场景：BlogCommandService.update 编辑博客。
     */
    @Update("UPDATE tb_blog SET shop_id = #{shopId}, title = #{title}, " +
            "content = #{content}, images = #{images} " +
            "WHERE id = #{id} AND user_id = #{userId}")
    int updateEditableFields(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("images") String images
    );
}
