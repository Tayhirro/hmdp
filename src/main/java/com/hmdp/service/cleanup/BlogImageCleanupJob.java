package com.hmdp.service.cleanup;

/*
 * 现实业务背景：用户上传图片后可能关闭页面，或博客删除后的文件第一次清理失败，服务器会留下待回收资产。
 * 实际触发：Spring 定时调度自动触发，不由用户直接调用；它清理过期 TEMP 并重试 DELETING 图片。
 *
 * 调度节奏：@Scheduled fixedDelay，默认 3600000 毫秒 = 每 1 小时跑一轮（本轮跑完再计时下一轮），
 * 可用配置项 hmdp.upload.cleanup-interval-ms 覆盖。每轮做两件事（实现在 BlogImageServiceImpl）：
 * 1. cleanupExpiredTemporaryImages：清理过期"临时图"（TEMP 状态，即已上传但从未被博客绑定）。
 *    保留 24 小时（配置 hmdp.upload.temp-retention-hours，默认 24），按上传时间从旧到新扫描，
 *    每轮最多处理 100 条（hmdp.upload.cleanup-batch-size）。例：图片 A 昨天上传、今天已过 24 小时
 *    仍未发布，本轮会被删掉文件和记录；刚上传 10 分钟的图片 B 不会被碰。
 *    单张删除失败会把状态还原回 TEMP，下一轮继续尝试，不会中断整批。
 * 2. cleanupDeletingImages：重试"待删除"图片（DELETING 状态，即删除中途失败留下的记录）。
 *    扫描 nextRetryTime 已到期（含为空）的记录，同样每轮最多 100 条；删除成功就清掉文件和记录，
 *    失败则把下次重试时间推后 5 分钟（hmdp.upload.deleting-retry-delay-minutes），等下一轮再试。
 * 两项清理分别返回各自处理成功的条数，有任何清理动作时打一条 INFO 日志，便于排查堆积。
 */

import com.hmdp.service.IBlogImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlogImageCleanupJob {

    private final IBlogImageService blogImageService;

    /**
     * 构造函数：注入图片服务（由 Spring 装配时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogImageCleanupJob(IBlogImageService blogImageService) {
        this.blogImageService = blogImageService;
    }

    /**
     * 定时补偿清理图片资产：清理过期临时图并重试删除失败的图片，有清理动作时打一条 INFO 日志。
     * 使用场景：无 HTTP 入口；由 Spring @Scheduled 调度自动触发（fixedDelay 默认 3600000 毫秒即 1 小时一轮，
     * 本轮跑完再计时下一轮，可用配置项 hmdp.upload.cleanup-interval-ms 覆盖），项目内没有其他调用方。
     * 实现要点：依次委托 {@link IBlogImageService}（图片服务接口，实现为 BlogImageServiceImpl）的两个方法——
     * cleanupExpiredTemporaryImages() 删除创建时间超过保留期（默认 24 小时）的 TEMP 图片（每批最多 100 条，
     * 单张失败恢复 TEMP 状态留待下轮）和 cleanupDeletingImages() 重试 DELETING 状态且 nextRetryTime
     * 已到期的图片（每批最多 100 条，失败把下次重试时间推后 5 分钟）；两者分别返回本批成功条数，
     * 任一大于 0 时记录一条汇总日志。
     */
    @Scheduled(fixedDelayString = "${hmdp.upload.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTemporaryImages() {
        int temporaryCleaned = blogImageService.cleanupExpiredTemporaryImages();
        int deletingCleaned = blogImageService.cleanupDeletingImages();
        if (temporaryCleaned > 0 || deletingCleaned > 0) {
            log.info("博客图片补偿清理完成，temporary={}, deleting={}",
                    temporaryCleaned, deletingCleaned);
        }
    }
}
