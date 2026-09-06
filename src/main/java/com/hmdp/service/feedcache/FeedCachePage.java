package com.hmdp.service.feedcache;

/*
 * 现实业务背景：读取 Feed 快照时必须区分“缓存里确实是空页”（这一轮 Feed 已经翻完了）
 * 和“Redis 不可用或快照不存在”（需要回源重新召回）。如果两种情况都当成“没数据”处理，
 * 用户翻到末尾时会被错误地重新召回一堆看过的内容。
 * 实际触发：{@link FeedCacheService#getPage}（从 Redis 读取一页 Feed 快照）返回本对象，
 * {@link com.hmdp.service.feed.BlogFeedService}（Feed 读链路入口服务）
 * 据 available 决定直接用 entries 组装响应，还是走“召回→排序→写快照”的重建路径。
 */

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
public class FeedCachePage {

    /** true = 快照命中（entries 可能为空列表，表示本轮 Feed 已无更多数据）；false = 回源重建。 */
    private boolean available;
    /** 本次命中的快照 ID（写入时生成的 32 位无连字符 UUID），用于拼下一页游标。 */
    private String snapshotId;
    /** 按快照顺序取出的这一页条目，每条是“blogId|createTimeMillis”解析结果。 */
    private List<FeedCacheEntry> entries;

    /**
     * 创建"快照不可用"的结果：available=false、snapshotId=null、entries 为空列表。
     * 使用场景：仅被 FeedCacheService.getPage 在三种情况返回——当前指针不存在（从没生成过快照或已被 invalidate 删除）、
     * 指定快照 key 不存在（5 分钟 TTL 已过期）、读写 Redis 抛异常（降级）；
     * BlogFeedService 据 available=false 决定走"召回 → 排序 → 写快照"的回源重建路径。
     * 实现要点：纯静态工厂；entries 恒为空列表，保证调用方无需判空即可遍历。
     */
    public static FeedCachePage unavailable() {
        return new FeedCachePage(false, null, Collections.emptyList());
    }
}
