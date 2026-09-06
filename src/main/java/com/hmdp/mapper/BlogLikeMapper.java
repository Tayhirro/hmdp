package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.dto.AuthorInteractionDTO;
import com.hmdp.entity.BlogLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 博客点赞记录表 tb_blog_like 的数据访问接口，blog_id 与 user_id 组合有唯一键。
 *
 * 自定义 SQL 方法的调用方集中在 BlogLikeService（点赞、取消点赞）和 ForYouRecall（推荐召回）；
 * 通用 CRUD 的使用方还有 BlogAssembler（组装详情时查询点赞状态）、BlogCommandService.delete
 * （删除博客时按 blog_id 清理点赞记录）和 BlogLikeService.queryUsers（点赞用户榜游标查询）。
 */
public interface BlogLikeMapper extends BaseMapper<BlogLike> {

    /**
     * 向 tb_blog_like 插入一条点赞记录，字段为 blog_id、user_id、create_time，取值全部来自参数。
     * 同一 (blog_id, user_id) 组合命中唯一键时插入失败并抛 DuplicateKeyException，
     * 调用方捕获该异常并视为重复点赞。
     *
     * 使用场景：BlogLikeService.like，点赞博客时写入点赞记录。
     */
    @Insert("INSERT INTO tb_blog_like (blog_id, user_id, create_time) " +
            "VALUES (#{blogId}, #{userId}, #{createTime})")
    int insertRelation(
            @Param("blogId") Long blogId,
            @Param("userId") Long userId,
            @Param("createTime") LocalDateTime createTime
    );

    /**
     * 从 tb_blog_like 删除点赞记录，条件为 blog_id 和 user_id 同时等于参数。
     *
     * 使用场景：BlogLikeService.unlike，取消点赞时删除点赞记录，返回值是实际删除的行数。
     */
    @Delete("DELETE FROM tb_blog_like " +
            "WHERE blog_id = #{blogId} AND user_id = #{userId}")
    int deleteRelation(@Param("blogId") Long blogId, @Param("userId") Long userId);

    /**
     * 统计当前用户分别给每位作者点过多少次赞，推荐服务用这个次数判断用户更常看哪些作者。
     * 这里只返回按作者汇总后的次数，不把每一条历史点赞记录都传给后续排序代码。
     * SQL：tb_blog_like 按 user_id 等于参数过滤，联表 tb_blog（b.id = bl.blog_id）取作者，
     * 按 b.user_id 分组 COUNT(*) 作为 interactionCount，按次数倒序取前 50 行。
     *
     * 使用场景：ForYouRecall.recall，为个性化 Feed 的加权排序提供偏好作者数据。
     */
    @Select("SELECT b.user_id AS authorId, COUNT(*) AS interactionCount " +
            "FROM tb_blog_like bl JOIN tb_blog b ON b.id = bl.blog_id " +
            "WHERE bl.user_id = #{userId} GROUP BY b.user_id " +
            "ORDER BY interactionCount DESC LIMIT 50")
    List<AuthorInteractionDTO> selectAuthorInteractions(@Param("userId") Long userId);
}
