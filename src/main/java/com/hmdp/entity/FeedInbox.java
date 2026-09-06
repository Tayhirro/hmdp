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
 * Feed 收件箱实体，对应数据库表 tb_feed_inbox。
 *
 * 为“发布时把博客 ID 推进每个粉丝收件箱”的推模式预留：recipient_id 与 blog_id 组合有唯一键，
 * 按 (recipient_id, score) 索引可按时间读取收件箱。
 * 当前无调用方：本项目 Feed 采用读时拉取加 Redis 快照，本实体与 FeedInboxMapper 均未接入读写链路。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_feed_inbox")
public class FeedInbox implements Serializable {

    /**
     * 序列化版本号。该实体当前无调用方，实际不会参与 Java 序列化，值固定为 1L。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 收件箱记录 ID，对应数据库列 id，自增主键。
     * 该实体当前无调用方，本字段不会被任何业务代码写入。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 收件人（粉丝）用户 ID，对应数据库列 recipient_id，与 blog_id 组成唯一键。
     * 该实体当前无调用方：只有推模式投递（发布时写入每个粉丝的收件箱）才会填本字段，而推模式尚未实现。
     */
    private Long recipientId;

    /**
     * 投递进收件箱的博客 ID，对应数据库列 blog_id。
     * 该实体当前无调用方：推模式投递逻辑尚未实现，本字段不会被任何业务代码写入或读取。
     */
    private Long blogId;

    /**
     * 排序分值（时间戳），对应数据库列 score；配合 (recipient_id, score) 索引按时间倒序读取收件箱。
     * 该实体当前无调用方：收件箱按时间读取的链路尚未实现，本字段预留。
     */
    private Long score;

    /**
     * 入箱时间，对应数据库列 create_time。
     * 该实体当前无调用方，本字段不会被任何业务代码写入。
     */
    private LocalDateTime createTime;
}
