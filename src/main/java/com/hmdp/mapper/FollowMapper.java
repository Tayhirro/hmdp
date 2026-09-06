package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Follow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 用户关注关系表 tb_follow 的数据访问接口，user_id 与 follow_user_id 组合有唯一键。
 *
 * 自定义 SQL 的调用方是 FollowServiceImpl.follow；通用 CRUD 的直接使用方是
 * FollowServiceImpl（关注、取关、查询关注列表、是否已关注），
 * FollowFeedRecall 经 IFollowService.query() 间接按 user_id 读关注关系（关注 Feed 召回）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    /**
     * 向 tb_follow 插入关注关系，字段为 user_id 和 follow_user_id；
     * (user_id, follow_user_id) 命中唯一键时通过 ON DUPLICATE KEY UPDATE id = id 保持原行不变，
     * 重复关注不会插入第二行。
     *
     * 使用场景：FollowServiceImpl.follow 关注用户（isFollow 为 true）。
     */
    @Insert("INSERT INTO tb_follow (user_id, follow_user_id) " +
            "VALUES (#{userId}, #{followUserId}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    int insertIfAbsent(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

    /**
     * 从 tb_follow 删除关注关系，条件为 user_id 和 follow_user_id 同时等于参数。
     *
     * 使用场景：FollowServiceImpl.follow 取消关注（isFollow 为 false）。
     */
    @Delete("DELETE FROM tb_follow " +
            "WHERE user_id = #{userId} AND follow_user_id = #{followUserId}")
    int deleteRelation(@Param("userId") Long userId, @Param("followUserId") Long followUserId);
}
