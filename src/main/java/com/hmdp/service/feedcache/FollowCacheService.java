package com.hmdp.service.feedcache;

/*
 * 现实业务背景：生成关注 Feed 时会频繁读取同一用户关注了谁，短时间内重复查 MySQL 没有必要。
 * 实际触发：FollowFeedRecall 召回候选时读取本缓存；关注或取关事务提交后由
 * {@link com.hmdp.service.follow.FollowChangedEventListener}（关注变更事件监听器）立即调用 {@link #invalidate} 失效。
 *
 * 这里的缓存是 Caffeine——JVM 进程内的本地缓存，不是 Redis：
 *     1. 缓存条目：key =（用户 id），value =（该用户关注的作者 id 列表，
 *     由 {@link #getFollowedIds} 的 loader 参数回源查询 tb_follow 表得到）。
 *     2. 容量与过期：写入后 5 分钟过期（expireAfterWrite），最多缓存 10000 个用户（maximumSize），
 *     超出后按 LRU 淘汰。
 *     3. 一致性代价：因为是各服务实例各自的本地缓存，过期前其它实例看不到关注变化，
 *     所以关注/取关后必须主动失效，否则关注流最长 5 分钟内仍按旧关注列表召回。
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
public class FollowCacheService {

    private final Cache<Long, List<Long>> followCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * 读取某用户关注的作者 ID 列表，本地缓存未命中时用 loader 回源查询并写入缓存。
     * 使用场景：生产代码中仅被 FollowFeedRecall.recall（"follow" 召回通道）调用——loader 内用
     * followService 查 tb_follow 表（user_id 条件）取出 follow_user_id 列表；测试 FollowCacheServiceTest 直接调用。
     * 实现要点：Caffeine 本地缓存 cache.get(userId, loader)；userId 为 null 直接返回空列表（不查缓存、不回源）；
     * 缓存配置：写入后 5 分钟过期（expireAfterWrite）、最多 10000 个用户（maximumSize，LRU 淘汰）；
     * 缓存命中时不产生任何 Redis/SQL 操作。
     */
    public List<Long> getFollowedIds(Long userId, Function<Long, List<Long>> loader) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return followCache.get(userId, loader::apply);
    }

    /**
     * 从本地缓存删除某用户的关注列表条目，下次读取时重新回源查询 tb_follow。
     * 使用场景：生产代码中仅被 FollowChangedEventListener.invalidateFollowCache 在关注/取关事务提交后调用，
     * 防止各服务实例的本地缓存在 5 分钟过期窗口内仍按旧关注列表召回关注流；测试 FollowCacheServiceTest 直接调用。
     * 实现要点：1 次 Caffeine cache.invalidate(userId)，纯 JVM 内存操作（非 Redis）；
     * userId 为 null 时直接忽略，不抛异常。
     */
    public void invalidate(Long userId) {
        if (userId != null) {
            followCache.invalidate(userId);
        }
    }
}
