package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户关注关系实体，对应数据库表 tb_follow，user_id 与 follow_user_id 组合有唯一键。
 *
 * user_id 是发起关注的一方，follow_user_id 是被关注的一方。
 * 主要使用方：FollowMapper（insertIfAbsent、deleteRelation）、
 * FollowServiceImpl（关注、取关、关注列表、是否已关注）、FollowFeedRecall（关注 Feed 召回）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_follow")
public class Follow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关系记录 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id（发起关注的一方）
     */
    private Long userId;

    /**
     * 关联的用户id（被关注的用户）
     */
    private Long followUserId;

    /**
     * 创建时间，由数据库默认值生成；插入语句未显式写该列
     */
    private LocalDateTime createTime;


}
