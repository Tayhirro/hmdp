package com.hmdp.service.follow;

/*
 * 现实业务背景：关注关系已经在 MySQL 中改变后，Redis 关注集合、本地关注缓存和 Feed 快照也需要跟着失效。
 * 实际触发：{@link com.hmdp.service.impl.FollowServiceImpl#follow}（关注/取关接口的实现）在
 * @Transactional 事务内通过 ApplicationEventPublisher 发布本事件，
 * {@link FollowChangedEventListener}（关注变更事件监听器）在事务提交后才消费它。
 * 事件只携带三个事实字段，不携带博客或缓存数据。
 */

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowChangedEvent {

    /** 发起关注/取关操作的用户 id（“我”）。 */
    private final Long userId;
    /** 被关注或被取关的目标用户 id。 */
    private final Long followUserId;
    /** true = 本次是关注（写入集合），false = 本次是取关（移出集合）。 */
    private final boolean followed;
}
