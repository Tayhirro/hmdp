package com.hmdp.service.blog;

/*
 * 现实业务背景：幂等记录检查后，发布流程必须明确选择“继续创建”还是“返回第一次创建结果”。
 * 实际触发：BlogIdempotencyService.begin() 创建该决定，BlogCommandService.publish() 根据它选择后续动作。
 */

import lombok.Getter;

/**
 * 查询幂等记录后的处理决定（纯值对象，不可变），调用方只会得到两种结果：
 *     1. 继续创建：这是第一次请求，当前请求取得了创建资格。
 *     2. 返回旧结果：相同请求以前已经成功，不再创建博客，直接返回第一次的博客 ID。
 * 
 */
@Getter
public class IdempotencyDecision {

    /** 是否应直接返回第一次的结果，而不是再次创建博客。 */
    private final boolean usePreviousResult;

    /** 本次对应的幂等记录 ID（tb_idempotency_record 表主键，两种结果都有值）。 */
    private final Long recordId;

    /** 当前请求取得创建资格时写入 owner_token 列的一次性随机标识（去横线的 32 位 UUID）；返回旧结果时为 null。 */
    private final String ownerToken;

    /** 第一次创建的博客 ID；返回旧结果时有值，继续创建时为 null。 */
    private final Long resourceId;

    /**
     * 私有构造函数：强制调用方走 createBlog 或 returnPreviousResult 工厂方法创建实例，
     * 保证各字段的取值组合合法（由这两个工厂在构造决定对象时各调用一次）。
     */
    private IdempotencyDecision(
            boolean usePreviousResult,
            Long recordId,
            String ownerToken,
            Long resourceId
    ) {
        this.usePreviousResult = usePreviousResult;
        this.recordId = recordId;
        this.ownerToken = ownerToken;
        this.resourceId = resourceId;
    }

    /**
     * 创建“继续创建博客”的决定：usePreviousResult = false，ownerToken 有值，resourceId 为 null。
     * 使用场景：仅被 {@link BlogIdempotencyService}（本包幂等服务）的 begin() 在当前请求首次取得创建资格时调用；
     * {@link BlogCommandService}（本包博客写命令服务）的 publish() 据此继续执行插入博客流程。
     */
    public static IdempotencyDecision createBlog(Long recordId, String ownerToken) {
        return new IdempotencyDecision(false, recordId, ownerToken, null);
    }

    /**
     * 创建“返回旧结果”的决定：usePreviousResult = true，ownerToken 为 null，resourceId 为第一次创建的博客 ID。
     * 使用场景：仅被 {@link BlogIdempotencyService} 的 begin() 在幂等记录已 SUCCEEDED 时调用；
     * {@link BlogCommandService} 的 publish() 据此跳过创建，直接把 resourceId 返回给前端。
     */
    public static IdempotencyDecision returnPreviousResult(Long recordId, Long resourceId) {
        return new IdempotencyDecision(true, recordId, null, resourceId);
    }

    /**
     * 判断本次是否应直接返回第一次的结果，而不是再次创建博客。
     * 使用场景：仅被 {@link BlogCommandService} 的 publish() 在调用 begin() 之后调用，作为“复用旧结果还是继续创建”的分支条件。
     * 实现要点：返回值与字段 usePreviousResult 相同（Lombok 会另生成 isUsePreviousResult()），
     * 单独命名是为了让调用方代码直接表达要执行的动作；纯内存判断，无 SQL。
     */
    public boolean shouldUsePreviousResult() {
        return usePreviousResult;
    }
}
