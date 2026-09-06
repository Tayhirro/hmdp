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
 * 博客图片资产实体，对应数据库表 tb_blog_image。
 *
 * 记录一次上传产生的图片资产及其生命周期：上传后为 TEMP，绑定博客后为 BOUND，
 * 解绑待删除为 DELETING（物理删除失败可按重试时间重试）。
 * 主要使用方：BlogImageMapper、BlogImageServiceImpl（上传、绑定、替换、解绑、定时清理）、
 * BlogQueryService.detail（按 blog_id 读取已绑定图片顺序）、BlogCommandService（发布、编辑时校验图片归属）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_image")
public class BlogImage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 图片状态常量：已上传但尚未绑定博客，可被定时清理任务删除。 */
    public static final String STATUS_TEMP = "TEMP";
    /** 图片状态常量：已绑定到博客，随博客展示。 */
    public static final String STATUS_BOUND = "BOUND";
    /** 图片状态常量：已解绑待物理删除，删除失败按 nextRetryTime 重试。 */
    public static final String STATUS_DELETING = "DELETING";

    /**
     * 图片资产 ID，对应数据库列 id，自增主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传用户 ID，对应数据库列 user_id；服务端校验图片归属时使用。
     */
    private Long userId;

    /**
     * 绑定的博客 ID，对应数据库列 blog_id；未绑定时为空。
     */
    private Long blogId;

    /**
     * 存储层内部路径，对应数据库列 storage_key，有唯一索引；物理删除时按它定位文件。
     */
    private String storageKey;

    /**
     * 公开访问地址，对应数据库列 public_url；仅用于展示，不作为发布接口的可信入参。
     */
    private String publicUrl;

    /**
     * 服务端识别的图片 MIME 类型，对应数据库列 content_type。
     */
    private String contentType;

    /**
     * 文件大小，单位字节，对应数据库列 file_size。
     */
    private Long fileSize;

    /**
     * 图片宽度，单位像素，对应数据库列 width。
     */
    private Integer width;

    /**
     * 图片高度，单位像素，对应数据库列 height。
     */
    private Integer height;

    /**
     * 图片状态：TEMP、BOUND 或 DELETING，对应数据库列 status。
     */
    private String status;

    /**
     * 博客内展示顺序，对应数据库列 sort_order；绑定博客时按提交顺序从 0 开始编号。
     */
    private Integer sortOrder;

    /**
     * 绑定博客的时间，对应数据库列 bind_time；未绑定时为空。
     */
    private LocalDateTime bindTime;

    /**
     * 物理删除失败次数，对应数据库列 retry_count；每次重试失败加一。
     */
    private Integer retryCount;

    /**
     * 最后一次物理删除失败的错误摘要，对应数据库列 last_error；用于排查删除失败原因。
     */
    private String lastError;

    /**
     * 下次允许重试物理删除的时间，对应数据库列 next_retry_time；也用于清理任务的条件更新抢占。
     */
    private LocalDateTime nextRetryTime;

    /**
     * 记录创建时间，对应数据库列 create_time。
     */
    private LocalDateTime createTime;

    /**
     * 记录最近更新时间，对应数据库列 update_time。
     */
    private LocalDateTime updateTime;
}
