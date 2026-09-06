package com.hmdp.service.feed;

/*
 * 现实业务背景：产品只允许用户选择“关注”或“为你推荐”，不把内部算法类名暴露为接口参数。
 * 实际触发：BlogFeedService 收到 mode 参数时解析本枚举，并据此选择固定召回通道和排序策略。
 */

import com.hmdp.exception.BusinessException;

/**
 * Feed 的两种产品模式。API 只暴露产品语义（following / for_you），不暴露内部算法名：
 * 算法可以升级，客户端契约保持稳定。mode 参数缺省按 FOLLOWING 处理，其余值报 INVALID_FEED_MODE。
 *
 * 两个枚举值的召回与排序对比（通道名、策略名均以代码为准）：
 * 1. FOLLOWING（apiValue="following"）→ 只走 "follow" 召回通道（按关注作者查博客），
 *    排序策略 = "time"（SimpleTimeRankingStrategy：按发布时间倒序，同毫秒按 blogId 倒序）。
 * 2. FOR_YOU（apiValue="for_you"）→ 同时走 "follow" + "for-you" 两个召回通道合并去重，
 *    先用曝光服务过滤已看过的博客，再排序 = "weighted"
 *    （WeightedRankingStrategy：点赞/评论/新鲜度加权分），最后做作者多样性打散
 *    （同一作者在前 2 条窗口内最多出现 2 次）。
 *
 * 关于推模式 / 拉模式 / 混合模式：本项目的 Feed 读链路没有实现传统意义上
 * “发布时把博客 ID 推进每个粉丝收件箱”的推模式，也没有按粉丝数阈值在推/拉之间切换的混合模式
 * （实体 {@link com.hmdp.entity.FeedInbox} 已定义但未接入读链路）。
 * 两种模式都是“读时拉取”：查询时实时召回 + 排序，再用 Redis 快照（FeedCacheService）
 * 把同一轮结果缓存 5 分钟供连续翻页，避免每翻一页都重新排序。因此这里也没有粉丝数阈值，
 * 区别只在召回通道和排序策略。
 */
public enum FeedMode {
    FOLLOWING("following", "time"),
    FOR_YOU("for_you", "weighted");

    private final String apiValue;
    private final String rankingStrategy;

    /**
     * 枚举构造器：绑定产品模式的 API 取值与排序策略注册名，仅由 JVM 在类加载初始化两个枚举常量时调用。
     * 使用场景：无业务调用方——FOLLOWING("following", "time") 与 FOR_YOU("for_you", "weighted")
     * 两个常量初始化时各调用一次。
     * 实现要点：纯字段赋值；apiValue 面向客户端契约，rankingStrategy 只作为服务内查找
     * RankingStrategyRegistry（排序策略注册表）的键，不对外暴露。
     */
    FeedMode(String apiValue, String rankingStrategy) {
        this.apiValue = apiValue;
        this.rankingStrategy = rankingStrategy;
    }

    /**
     * 返回该模式对外的 API 取值（FOLLOWING → "following"，FOR_YOU → "for_you"）。
     * 使用场景：被 BlogFeedService.query（拼 Feed 快照指针/快照 Redis key、解析入参 mode）、
     * BlogFeedService.cursorType（拼游标 type "feed-{apiValue}-v2"）和
     * FollowChangedEventListener.invalidateFeedCaches（关注变更后逐模式失效 Feed 缓存）调用。
     * 实现要点：纯 getter，返回构造时绑定的不可变字符串。
     */
    public String getApiValue() {
        return apiValue;
    }

    /**
     * 返回该模式对应的排序策略注册名（FOLLOWING → "time" 时间倒序，FOR_YOU → "weighted" 加权重排）。
     * 使用场景：仅被 BlogFeedService.rebuild 调用，用该名称从 RankingStrategyRegistry（排序策略注册表）取排序实现。
     * 实现要点：纯 getter；策略名不暴露给客户端，仅作为服务内策略查找键。
     */
    public String getRankingStrategy() {
        return rankingStrategy;
    }

    /**
     * 把客户端传入的 mode 参数解析为枚举：null 按 FOLLOWING 处理，其余值先 trim 转小写再匹配。
     * 使用场景：仅被 BlogFeedService.query 入口调用，把 GET /blog/feed 的 mode 字符串转为产品模式。
     * 实现要点：遍历 values() 按 apiValue 精确匹配；无匹配抛 BusinessException.badRequest
     * （code = INVALID_FEED_MODE，提示 mode 仅支持 following 或 for_you）。
     */
    public static FeedMode from(String value) {
        String normalized = value == null ? FOLLOWING.apiValue : value.trim().toLowerCase();
        for (FeedMode mode : values()) {
            if (mode.apiValue.equals(normalized)) {
                return mode;
            }
        }
        throw BusinessException.badRequest("INVALID_FEED_MODE", "mode 仅支持 following 或 for_you");
    }
}
