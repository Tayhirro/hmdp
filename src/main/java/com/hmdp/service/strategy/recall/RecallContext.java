package com.hmdp.service.strategy.recall;

/*
 * 现实业务背景：一次 Feed 召回需要知道为谁召回、时间边界、候选上限以及通道间共享信号。
 * 实际触发：{@link com.hmdp.service.feed.BlogFeedService}（Feed 读链路入口服务）创建该上下文，
 * {@link FollowFeedRecall}（关注作者召回通道）与 {@link ForYouRecall}（为你推荐召回通道）
 * 在同一次请求中读取或补充 extra。
 *
 * 四个字段的含义：
 * 1. userId：为哪个用户召回候选博客。
 * 2. maxTime：游标分页的时间边界，取上一页最后一条博客的发布时间（毫秒时间戳）。
 *    首次翻页为 null（不限时间）；继续翻页时各召回通道只取 createTime 早于该时间的博客，
 *    配合 extra 里的 lastId 处理"同一时刻多条博客"的边界（见 FollowFeedRecall / ForYouRecall）。
 * 3. limit：本次最多召回多少条候选。BlogFeedService 固定传 200（候选池大小 CANDIDATE_POOL_SIZE）。
 * 4. extra：通道间共享信号的口袋，键值对。
 *    例：翻页时 BlogFeedService 放入 "lastId"（上一页最后一条的博客 ID）；
 *    ForYouRecall 会放入 "authorInteractions"（当前用户按作者聚合的点赞次数列表），
 *    供排序阶段计算作者亲和度时复用，避免重复查库。
 */

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RecallContext {
    private Long userId;
    private Long maxTime;
    private int limit;
    private Map<String, Object> extra;
}
