package com.hmdp.service.feedcache;

/*
 * 现实业务背景：Feed 快照不仅要记住博客 ID，还要保留发布时间，以便 Redis 快照失效后按稳定边界回源
 * （游标里携带“上一页最后一条的时间 + ID”，回源 SQL 用它们切分同一毫秒发布的博客）。
 * 实际触发：{@link FeedCacheService}（把已生成的 Feed 分页结果缓存到 Redis 的服务）
 * 写入或读取 Redis List 元素时，对该轻量条目执行序列化和解析。
 *
 * 序列化格式是一条字符串：“博客 id” + "|" + “发布时间的 UTC 毫秒时间戳”，
 * 例如 blogId=42、createTime=2026-01-01 00:00:00 UTC 时就是 "42|1767225600000"。
 * 它作为 Redis List（快照 key 下的一个元素）存储；parse() 遇到 null、
 * 分段数不是 2、或数字解析失败时返回 null，由调用方跳过这条坏数据。
 */

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeedCacheEntry {

    /** 快照里的博客 id，对应 tb_blog.id。 */
    private Long blogId;
    /** 博客发布时间的 UTC 毫秒时间戳；上游写入快照时把 null 时间按 0 处理。 */
    private Long createTime;

    /**
     * 把本条目序列化为一条 Redis List 元素字符串："blogId|createTimeMillis"，如 "42|1767225600000"。
     * 使用场景：仅被 FeedCacheService.cacheFeed（写 Feed 快照）在把每条博客 RPUSH 进快照 List 前调用。
     * 实现要点：纯字符串拼接，两个字段均为 Long 的十进制形式；博客 id 与时间戳都是数字，不会引入 "|" 歧义。
     */
    public String serialize() {
        return blogId + "|" + createTime;
    }

    /**
     * 反序列化一条 "blogId|createTimeMillis" 字符串为条目，与 serialize 互为逆操作；格式非法返回 null。
     * 使用场景：仅被 FeedCacheService.getPage（读 Feed 快照）通过方法引用对 LRANGE 取出的每个元素调用，
     * 解析失败（返回 null）的坏数据由调用方直接跳过。
     * 实现要点：按 "|" 切分且必须恰好 2 段；两段都要能解析为 Long，NumberFormatException 捕获后返回 null；
     * 入参为 null 直接返回 null。纯内存解析，无 Redis/SQL 操作。
     */
    public static FeedCacheEntry parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new FeedCacheEntry(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
