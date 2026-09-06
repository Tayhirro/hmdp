package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 一次“创建资源请求”的处理记录，目前用于防止重复发布博客。
 *
 * 真实场景：用户点击发布后，可能因为双击、网络超时或浏览器自动重试，
 * 把同一份发布请求发送多次。服务端用 {@code userId + requestKey} 找到这张记录，
 * 从而判断应该创建博客，还是直接返回第一次创建的博客 ID。
 *
 * 它对应数据库表 {@code tb_idempotency_record}，不保存博客正文。
 * 它保存的是“这个请求处理过没有、第一次创建了什么”。
 *
 * 主要使用方：IdempotencyRecordMapper 与 BlogIdempotencyService（发布博客的幂等判定）、
 * IdempotencyCleanupJob（按保留期定时清理过期记录）。
 */
@Data
@Accessors(chain = true)
@TableName("tb_idempotency_record")
public class IdempotencyRecord {

    /** 第一次请求正在创建博客；如果事务失败，这条记录也会一起回滚。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 第一次请求已经成功，{@link #resourceId} 中保存了第一次创建的博客 ID。 */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** 表示这条记录处理的是博客发布请求，方便该表以后复用于其他创建接口。 */
    public static final String RESOURCE_BLOG = "BLOG";

    /** 幂等记录自身的数据库主键，不是博客 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 发起请求的用户 ID；不同用户可以使用相同的 requestKey。 */
    private Long userId;

    /** 前端生成的 clientRequestId，同一份发布请求重试时必须保持不变。 */
    private String requestKey;

    /**
     * 标题、正文、商户和图片 ID 计算出的 SHA-256 摘要。
     * 同一个 requestKey 携带不同内容时摘要不同，服务端会拒绝该请求，防止误覆盖第一次发布。
     */
    private String requestHash;

    /** 第一次请求创建的资源类型，目前固定为 BLOG。 */
    private String resourceType;

    /** 第一次请求成功创建的博客 ID；处理完成前为空。 */
    private Long resourceId;

    /** 第一次成功返回的数据快照；当前保存博客 ID 的字符串形式。 */
    private String responseData;

    /** 当前处理状态：PROCESSING 或 SUCCEEDED。 */
    private String status;

    /**
     * 第一次插入记录的请求生成的随机标识。
     * 多个相同请求并发到达时，只有标识与数据库一致的请求可以继续创建博客。
     */
    private String ownerToken;

    /** 该记录的保留截止时间；当前成功记录默认保留 30 天。 */
    private LocalDateTime expireTime;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录最近更新时间。 */
    private LocalDateTime updateTime;
}
