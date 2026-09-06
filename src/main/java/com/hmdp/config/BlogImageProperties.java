package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 博客图片上传配置项，绑定 application.yaml 中前缀 hmdp.upload 的配置。
 * 读取方：{@link com.hmdp.service.impl.BlogImageServiceImpl} 与 {@link com.hmdp.service.storage.BlogImageStorage}。
 * 本类无显式方法，getter/setter 由 Lombok 的 {@code @Data} 生成。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hmdp.upload")
public class BlogImageProperties {

    /** 上传文件的根目录。配置项 hmdp.upload.root，yaml 当前为环境变量 HMDP_UPLOAD_ROOT，缺省 ./data/uploads；字段本身无代码默认值。 */
    private String root;

    /** 图片对外访问的 URL 前缀。配置项 hmdp.upload.public-prefix，默认 /imgs/。 */
    private String publicPrefix = "/imgs/";

    /** 单文件最大字节数。配置项 hmdp.upload.max-bytes，默认 5 * 1024 * 1024 = 5242880（5MB）。 */
    private long maxBytes = 5 * 1024 * 1024L;

    /** 图片最大宽度（像素），超出拒绝上传。配置项 hmdp.upload.max-width，默认 10000。 */
    private int maxWidth = 10000;

    /** 图片最大高度（像素），超出拒绝上传。配置项 hmdp.upload.max-height，默认 10000。 */
    private int maxHeight = 10000;

    /** 图片总像素上限，防解压炸弹。配置项 hmdp.upload.max-pixels，默认 40000000（4000 万）。 */
    private long maxPixels = 40_000_000L;

    /** 临时文件保留时长（小时），过期由清理任务删除。配置项 hmdp.upload.temp-retention-hours，默认 24。 */
    private long tempRetentionHours = 24L;

    /** 清理任务每批最多删除的文件数。配置项 hmdp.upload.cleanup-batch-size，默认 100。 */
    private int cleanupBatchSize = 100;

    /**
     * DELETING 资产失败后的固定退避时间；后续可替换为 Outbox 消费者的指数退避。
     * 使用场景：图片删除重试调度（清理任务）按该间隔跳过仍处 DELETING 的资产。
     * 配置项 hmdp.upload.deleting-retry-delay-minutes，默认 5。
     */
    private long deletingRetryDelayMinutes = 5L;
}
