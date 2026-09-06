package com.hmdp.service.cleanup;

/*
 * 现实业务背景：发布防重记录只需保留一段时间，长期不清理会让幂等表持续增长。
 * 实际触发：Spring 定时调度自动触发，分批删除已经过期的发布幂等记录。
 *
 * 背景：用户双击发布或成功响应丢失后重试时，tb_idempotency_record 表用
 * "用户 ID + clientRequestId" 唯一约束保证同一请求只创建一篇博客。每次发布都会写一条记录，
 * 记录创建时设了 expire_time = 创建时间 + 30 天（BlogIdempotencyService 的 RETENTION_DAYS），
 * 过了保留期就不再有防重意义，可以删掉。
 *
 * 调度节奏与删除方式：@Scheduled fixedDelay，默认 86400000 毫秒 = 每 24 小时跑一轮
 * （本轮跑完再计时下一轮），可用配置项 hmdp.idempotency.cleanup-interval-ms 覆盖。
 * 每轮调用 idempotencyService.cleanupExpired(500)，对应 SQL 为
 * DELETE FROM tb_idempotency_record WHERE expire_time <= 当前时间 LIMIT 500：
 * 即单轮最多删 500 条，防止一次删太多行锁表；如果某天过期记录超过 500 条，
 * 余下的留给下一轮（24 小时后）继续删。有清理动作时打一条 INFO 日志。
 */

import com.hmdp.service.blog.BlogIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IdempotencyCleanupJob {

    private final BlogIdempotencyService idempotencyService;

    /**
     * 构造函数：注入幂等服务（由 Spring 装配时调用一次，仅字段赋值，无业务逻辑）。
     */
    public IdempotencyCleanupJob(BlogIdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    /**
     * 定时分批删除超过保留期（30 天）的发布幂等记录，防止 tb_idempotency_record 表无限增长。
     * 使用场景：无 HTTP 入口；由 Spring @Scheduled 调度自动触发（fixedDelay 默认 86400000 毫秒即 24 小时一轮，
     * 本轮跑完再计时下一轮，可用配置项 hmdp.idempotency.cleanup-interval-ms 覆盖），项目内没有其他调用方。
     * 实现要点：调用 {@link BlogIdempotencyService}（发布博客的幂等控制服务）的 cleanupExpired(500)，
     * 对应 1 条 DELETE SQL——条件 expire_time 不晚于当前时间、LIMIT 500，即单轮最多删 500 条以防长事务锁表；
     * 本轮删不完的过期记录留给下一轮继续删，删除条数大于 0 时打一条 INFO 日志。
     */
    @Scheduled(fixedDelayString = "${hmdp.idempotency.cleanup-interval-ms:86400000}")
    public void cleanupExpiredRecords() {
        int cleaned = idempotencyService.cleanupExpired(500);
        if (cleaned > 0) {
            log.info("已清理过期幂等记录，count={}", cleaned);
        }
    }
}
