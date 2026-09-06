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
 * 博客点赞记录实体，对应数据库表 tb_blog_like，blog_id 与 user_id 组合有唯一键。
 *
 * 主要使用方：BlogLikeMapper（insertRelation、deleteRelation、selectAuthorInteractions）、
 * BlogLikeService（点赞、取消点赞、点赞用户榜）、BlogAssembler（详情点赞状态与点赞列表）、
 * BlogCommandService.delete（删除博客时按 blog_id 清理点赞）、ForYouRecall（作者互动统计）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_like")
public class BlogLike implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 点赞记录 ID，对应数据库列 id，自增主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被点赞的博客 ID，对应数据库列 blog_id。
     */
    private Long blogId;

    /**
     * 点赞用户 ID，对应数据库列 user_id。
     */
    private Long userId;

    /**
     * 点赞时间，对应数据库列 create_time；点赞用户榜按它和 id 倒序做游标分页。
     */
    private LocalDateTime createTime;
}
