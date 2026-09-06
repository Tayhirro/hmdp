package com.hmdp.service.feed;

/*
 * 现实业务背景：已登录用户打开关注流或为你推荐、继续下拉或主动刷新时，需要生成一页稳定的内容列表。
 * 实际触发：GET /blog/feed 经 BlogServiceImpl.queryBlogFeed() 进入本类，再协调召回、排序、快照和曝光服务。
 */

import com.hmdp.dto.AuthorInteractionDTO;
import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.CursorPayload;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.blog.BlogAssembler;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.service.feedcache.FeedCacheEntry;
import com.hmdp.service.feedcache.FeedCachePage;
import com.hmdp.service.feedcache.FeedCacheService;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import com.hmdp.service.strategy.ranking.RankingStrategyRegistry;
import com.hmdp.service.strategy.recall.RecallContext;
import com.hmdp.service.strategy.recall.RecallOrchestrator;
import com.hmdp.service.strategy.recall.impl.blog.ForYouRecall;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Feed 读链路的核心边界。一个完整请求的路径是：
 * GET /blog/feed?mode=for_you&cursor=xxx → 本类 query() → 先读 Redis 快照缓存
 * {@link FeedCacheService}（把已生成的 Feed 分页结果缓存到 Redis 的服务），
 * 未命中则走"召回 → 过滤曝光 → 排序 → 写快照"重建一整轮结果。
 *
 * 协作的组件：
 * - {@link RecallOrchestrator}（召回编排器：按通道名调用各召回策略并用 LinkedHashSet 合并博客 ID）；
 * - {@link RankingStrategyRegistry}（排序策略注册表：按策略名取出对应的排序实现）；
 * - {@link FeedExposureService}（曝光记录服务：用 Redis ZSet 记录用户最近看过哪些博客，用于推荐去重）；
 * - {@link CursorCodec}（游标编解码器：把 {@link CursorPayload} 里的分页位置编成不透明字符串，
 *   或把字符串游标解码回 {@link CursorPayload}）。
 *
 * 设计约束：
 * 1. 产品模式（following / for_you，见 {@link FeedMode}）显式选择召回与排序组合，
 *    不把内部策略名（"follow"、"weighted" 等）暴露给客户端。
 * 2. 召回决定“有哪些候选”（一次最多 {@value #CANDIDATE_POOL_SIZE} 条 ID），
 *    排序/重排决定“先看谁”和作者多样性（同一作者在前 {@value #MAX_PER_AUTHOR_BEFORE_BACKFILL} 条内最多出现 2 次）。
 * 3. 不可变快照保证连续翻页稳定；刷新（refresh=true）只删除当前指针并重置游标，旧快照靠 TTL 自然过期，不破坏旧游标。
 * 4. 曝光是可降级副作用，Redis 故障不能阻断数据库回源和 Feed 返回。
 * 5. 无法严格复现 For You 跨页顺序时返回 hasMore=false，不制造虚假分页承诺。
 */
@Service
public class BlogFeedService {

    private static final int PAGE_SIZE = 50;
    private static final int CANDIDATE_POOL_SIZE = 200;
    private static final int MAX_PER_AUTHOR_BEFORE_BACKFILL = 2;

    private final BlogMapper blogMapper;
    private final BlogAssembler blogAssembler;
    private final RecallOrchestrator recallOrchestrator;
    private final RankingStrategyRegistry rankingStrategyRegistry;
    private final FeedCacheService feedCacheService;
    private final FeedExposureService exposureService;
    private final CursorCodec cursorCodec;

    /**
     * 构造函数：注入 Feed 读链路依赖的七个协作组件，由 Spring 创建本 Service 时调用一次，仅字段赋值，无业务逻辑。
     * 使用场景：Spring 容器装配 {@code @Service} 本类时通过构造器注入，项目内无其他调用方。
     * 实现要点：纯内存赋值；依赖包括 BlogMapper（按 ID 批量回读博客）、BlogAssembler（组装卡片 DTO）、
     * RecallOrchestrator（召回编排）、RankingStrategyRegistry（排序策略注册表）、
     * FeedCacheService（Redis 快照缓存）、FeedExposureService（曝光记录）和 CursorCodec（游标编解码）。
     */
    public BlogFeedService(
            BlogMapper blogMapper,
            BlogAssembler blogAssembler,
            RecallOrchestrator recallOrchestrator,
            RankingStrategyRegistry rankingStrategyRegistry,
            FeedCacheService feedCacheService,
            FeedExposureService exposureService,
            CursorCodec cursorCodec
    ) {
        this.blogMapper = blogMapper;
        this.blogAssembler = blogAssembler;
        this.recallOrchestrator = recallOrchestrator;
        this.rankingStrategyRegistry = rankingStrategyRegistry;
        this.feedCacheService = feedCacheService;
        this.exposureService = exposureService;
        this.cursorCodec = cursorCodec;
    }

    /**
     * 处理一次 Feed 分页查询：先读 Redis 快照，未命中则回源重建一轮召回与排序并写快照，返回一页博客卡片。
     * 使用场景：登录用户请求 GET /blog/feed（参数 cursor、mode、refresh），经 BlogController.queryBlogFeed()
     * 调用 BlogServiceImpl.queryBlogFeed() 后进入本方法；这是本类唯一的生产入口调用方。
     * 实现要点：
     * 1. 必须已登录（requireCurrentUserId 取不到用户 ID 抛未登录异常）；mode 经 FeedMode.from 解析，
     *    非法值报 INVALID_FEED_MODE；cursor 经 CursorCodec.decode 解码，游标类型为 "feed-" + 模式 apiValue + "-v2"。
     * 2. refresh=true 时先 feedCacheService.invalidate 删除当前指针并重置游标位置（旧快照靠 TTL 自然过期）。
     * 3. feedCacheService.getPage 一次取 PAGE_SIZE + 1 = 51 条（多取 1 条判断 hasMore），命中则经 fromSnapshot 直接返回。
     * 4. 快照不可用时回源：游标里 boundaryScore/boundaryId 缺任一项视为游标损坏抛 INVALID_CURSOR，
     *    否则调用 rebuild（"上一页最后一条博客的时间 + ID"作为召回边界，避免重复返回已翻数据）。
     */
    public Result query(String cursor, String rawMode, Boolean refresh) {
        Long userId = requireCurrentUserId();
        FeedMode mode = FeedMode.from(rawMode);
        String cursorType = cursorType(mode);
        CursorPayload position = cursorCodec.decode(cursor, cursorType);

        if (Boolean.TRUE.equals(refresh)) {
            feedCacheService.invalidate(userId, mode.getApiValue());
            position = null;
        }

        int offset = position == null || position.getOffset() == null ? 0 : position.getOffset();
        if (offset < 0) {
            throw invalidCursor();
        }
        String snapshotId = position == null ? null : position.getSnapshotId();
        FeedCachePage cached = feedCacheService.getPage(
                userId, mode.getApiValue(), snapshotId, offset, PAGE_SIZE + 1);
        if (cached.isAvailable()) {
            return Result.ok(fromSnapshot(userId, mode, cached, offset));
        }

        // 快照不可用时用游标里的边界回源重新召回：boundaryTime = 上一页最后一条博客的发布毫秒时间戳，
        // boundaryId = 该博客的 id；召回 SQL 会取 create_time < boundaryTime，
        // 或 create_time = boundaryTime 且 id < boundaryId 的数据——同一毫秒发布的博客靠 ID 继续切分，
        // 保证回源结果至少不重复返回已翻过的数据。两个字段缺任何一个都视为游标损坏，直接报 INVALID_CURSOR。
        Long boundaryTime = position == null ? null : position.getBoundaryScore();
        Long boundaryId = position == null ? null : position.getBoundaryId();
        if (position != null && (boundaryTime == null || boundaryId == null)) {
            throw invalidCursor();
        }
        return Result.ok(rebuild(userId, mode, boundaryTime, boundaryId));
    }

    /**
     * 把一页命中快照的结果组装为分页 DTO：截取前 50 条、按 ID 回读博客、记录曝光并生成下一页游标。
     * 使用场景：仅被本类 query 在 feedCacheService.getPage 返回 available=true（快照命中）时调用。
     * 实现要点：
     * 1. 快照条目数超过 PAGE_SIZE（50）视为还有下一页，只取前 50 条组装；否则整页返回且 hasMore=false。
     * 2. hydrateInOrder 按快照顺序批量查 MySQL 回读博客；exposureService.record 在返回前记录本页曝光。
     * 3. hasMore 时用 encodeCursor 生成下一页游标：snapshotId + offset（当前 offset + 本页条数）
     *    + 边界（本页最后一条的 createTime、blogId）。
     */
    private CursorPageDTO<BlogCardDTO> fromSnapshot(
            Long userId,
            FeedMode mode,
            FeedCachePage cached,
            int offset
    ) {
        List<FeedCacheEntry> entries = cached.getEntries();
        boolean hasMore = entries.size() > PAGE_SIZE;
        List<FeedCacheEntry> pageEntries = hasMore
                ? new ArrayList<>(entries.subList(0, PAGE_SIZE))
                : entries;
        List<Blog> blogs = hydrateInOrder(pageEntries.stream()
                .map(FeedCacheEntry::getBlogId)
                .collect(Collectors.toList()));
        exposureService.record(userId, blogs);

        String nextCursor = hasMore && !pageEntries.isEmpty()
                ? encodeCursor(mode, cached.getSnapshotId(), offset + pageEntries.size(),
                        pageEntries.get(pageEntries.size() - 1))
                : null;
        return new CursorPageDTO<>(blogAssembler.toCards(blogs), nextCursor, hasMore);
    }

    /**
     * 快照不可用时的重建路径：召回候选、按模式过滤与排序、写 Redis 快照，并返回本页结果。
     * 使用场景：仅被本类 query 在 feedCacheService.getPage 返回 available=false 时调用；
     * boundaryTime/boundaryId 来自游标（首页两者均为 null，续页时为上一页最后一条博客的时间与 ID）。
     * 实现要点：
     * 1. 召回：FOLLOWING 只走 "follow" 通道，FOR_YOU 走 "follow" + "for-you" 双通道合并去重；
     *    候选上限 CANDIDATE_POOL_SIZE = 200，续页边界经 RecallContext（maxTime 与 extra 的 lastId）传入。
     * 2. FOR_YOU 专属：先 exposureService.filterUnseen 过滤近 7 天已曝光博客，再按 FeedMode.getRankingStrategy
     *    从注册表取排序策略（following="time"、for_you="weighted"），排序后做 diversifyAuthors 作者打散。
     * 3. feedCacheService.cacheFeed 写快照换 snapshotId；候选数超过 50 才置 hasMore=true，
     *    并用 encodeCursor（offset=50，边界为第 50 条博客）生成下一页游标；返回前 exposureService.record 记录本页曝光。
     * 4. FOR_YOU 且 snapshotId 为 null（缓存写失败）时只返回当前页且 hasMore=false，不伪造游标。
     */
    private CursorPageDTO<BlogCardDTO> rebuild(
            Long userId,
            FeedMode mode,
            Long boundaryTime,
            Long boundaryId
    ) {
        Map<String, Object> extra = new HashMap<>();
        if (boundaryId != null) {
            extra.put("lastId", boundaryId);
        }
        RecallContext context = RecallContext.builder()
                .userId(userId)
                .maxTime(boundaryTime)
                .limit(CANDIDATE_POOL_SIZE)
                .extra(extra)
                .build();
        List<String> recallChannels = mode == FeedMode.FOLLOWING
                ? Collections.singletonList("follow")
                : Arrays.asList("follow", "for-you");
        List<Long> candidateIds = recallOrchestrator.multiRecall(recallChannels, context);
        List<Blog> candidates = hydrateInOrder(candidateIds);

        if (mode == FeedMode.FOR_YOU) {
            candidates = exposureService.filterUnseen(userId, candidates);
        }
        RankingStrategy<Blog> ranking = rankingStrategyRegistry.getStrategy(mode.getRankingStrategy());
        candidates = ranking.rank(candidates, rankingContext(userId, extra));
        if (mode == FeedMode.FOR_YOU) {
            candidates = diversifyAuthors(candidates);
        }

        String snapshotId = feedCacheService.cacheFeed(userId, mode.getApiValue(), candidates);
        boolean hasMore = candidates.size() > PAGE_SIZE;
        List<Blog> page = hasMore
                ? new ArrayList<>(candidates.subList(0, PAGE_SIZE))
                : candidates;
        exposureService.record(userId, page);

        // 推荐排序离开快照后无法严格复现（同一批候选重排一次结果就可能不同）；
        // 缓存故障（cacheFeed 返回 null snapshotId）时宁可只返回当前页并置 hasMore=false，
        // 也不伪造一个下次必然取不到数据的可继续游标。
        if (snapshotId == null && mode == FeedMode.FOR_YOU) {
            return new CursorPageDTO<>(blogAssembler.toCards(page), null, false);
        }
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Blog last = page.get(page.size() - 1);
            nextCursor = encodeCursor(mode, snapshotId, PAGE_SIZE,
                    new FeedCacheEntry(last.getId(), epochMilli(last)));
        }
        return new CursorPageDTO<>(blogAssembler.toCards(page), nextCursor, hasMore);
    }

    /**
     * 按给定 ID 顺序批量回读博客实体，查不到的 ID 直接丢弃，不改变原有顺序。
     * 使用场景：被本类 fromSnapshot（快照条目回读）和 rebuild（召回候选回读）调用。
     * 实现要点：1 条 MySQL 批量查询（MyBatis-Plus BlogMapper.selectBatchIds）；先过滤 null 并去重、
     * 截断到 CANDIDATE_POOL_SIZE + 1 = 201 个 ID，再按原顺序映射回 Blog 列表，缺失 ID 不补位。
     */
    private List<Blog> hydrateInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(CANDIDATE_POOL_SIZE + 1L)
                .collect(Collectors.toList());
        Map<Long, Blog> byId = blogMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Blog::getId, blog -> blog, (left, right) -> left));
        return distinctIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 从召回阶段放入上下文的附加数据中提取作者互动统计，构建带作者亲和度的排序上下文。
     * 使用场景：仅被本类 rebuild 在调用排序策略 rank() 前构建 RankingContext 时调用。
     * 实现要点：读 RecallContext.extra 里 ForYouRecall.AUTHOR_INTERACTIONS 键（作者互动行列表，
     * 非 List 类型按空处理）；每行累计该作者互动次数 counts，并算亲和度 affinity = min(1, 次数 / 5)；
     * 附带当前用户 ID 与 LocalDateTime.now() 作为排序基准时间。
     */
    @SuppressWarnings("unchecked")
    private RankingContext rankingContext(Long userId, Map<String, Object> extra) {
        List<AuthorInteractionDTO> rows = extra.get(ForYouRecall.AUTHOR_INTERACTIONS) instanceof List
                ? (List<AuthorInteractionDTO>) extra.get(ForYouRecall.AUTHOR_INTERACTIONS)
                : Collections.emptyList();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Double> affinity = new HashMap<>();
        for (AuthorInteractionDTO row : rows) {
            int count = row.getInteractionCount() == null ? 0 : row.getInteractionCount();
            counts.put(row.getAuthorId(), count);
            affinity.put(row.getAuthorId(), Math.min(1D, count / 5D));
        }
        return RankingContext.builder()
                .currentUserId(userId)
                .now(LocalDateTime.now())
                .authorAffinity(affinity)
                .authorInteractionCount(counts)
                .build();
    }

    /**
     * 对排序后的候选做作者多样性打散：同一作者在未被后移的主列表窗口内最多出现 2 次，超出的依次挪到列表末尾。
     * 使用场景：仅被本类 rebuild 在 FOR_YOU 模式排序完成后调用（FOLLOWING 模式按时间序，不打散）。
     * 实现要点：纯内存操作；按顺序扫描，同一 userId 计数达到 MAX_PER_AUTHOR_BEFORE_BACKFILL = 2 后，
     * 该作者的后续博客进入 deferred 列表，最后 primary.addAll(deferred) 拼回，保持各组内部相对顺序稳定。
     */
    private List<Blog> diversifyAuthors(List<Blog> ranked) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<Blog> primary = new ArrayList<>(ranked.size());
        List<Blog> deferred = new ArrayList<>();
        for (Blog blog : ranked) {
            int count = counts.getOrDefault(blog.getUserId(), 0);
            if (count < MAX_PER_AUTHOR_BEFORE_BACKFILL) {
                primary.add(blog);
                counts.put(blog.getUserId(), count + 1);
            } else {
                deferred.add(blog);
            }
        }
        primary.addAll(deferred);
        return primary;
    }

    /**
     * 把翻页位置编码为不透明游标字符串：快照 ID + 偏移量 + 边界（最后一条博客的时间与 ID）。
     * 使用场景：被本类 fromSnapshot（快照翻页，offset 为累计已取条数）和 rebuild（重建首页，offset=50）调用。
     * 实现要点：组装 CursorPayload（type = "feed-" + 模式 apiValue + "-v2"），
     * boundaryScore = 边界条目的 createTime 毫秒时间戳、boundaryId = 其 blogId，
     * 交给 CursorCodec.encode 生成字符串返回给客户端。
     */
    private String encodeCursor(
            FeedMode mode,
            String snapshotId,
            int offset,
            FeedCacheEntry boundary
    ) {
        CursorPayload payload = new CursorPayload();
        payload.setType(cursorType(mode));
        payload.setSnapshotId(snapshotId);
        payload.setOffset(offset);
        payload.setBoundaryScore(boundary.getCreateTime());
        payload.setBoundaryId(boundary.getBlogId());
        return cursorCodec.encode(payload);
    }

    /**
     * 把博客发布时间转成 UTC 毫秒时间戳，时间为 null 时按 0 处理。
     * 使用场景：仅被本类 rebuild 生成下一页游标时调用，为边界条目构造 FeedCacheEntry 的 createTime 字段。
     * 实现要点：纯内存转换（LocalDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()），
     * 空时间按 0 的规则与 FeedCacheService.cacheFeed 写快照时保持一致。
     */
    private long epochMilli(Blog blog) {
        return blog.getCreateTime() == null
                ? 0L
                : blog.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 从 ThreadLocal 登录态（UserHolder）取当前用户 ID，未登录或 ID 为空抛"请先登录"业务异常。
     * 使用场景：仅被本类 query 入口调用，保证 Feed 接口只对登录用户开放。
     * 实现要点：纯内存读取，无 Redis/SQL 操作；未登录抛 BusinessException.unauthorized("请先登录")。
     */
    private Long requireCurrentUserId() {
        if (UserHolder.getUser() == null || UserHolder.getUser().getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return UserHolder.getUser().getId();
    }

    /**
     * 生成当前模式的游标类型标识，编解码游标时用于区分模式、防止游标跨模式混用。
     * 使用场景：被本类 query（解码入参 cursor 前确定类型）和 encodeCursor（编码下一页游标时写入类型）调用。
     * 实现要点：格式固定为 "feed-" + 模式 apiValue + "-v2"，即 following → "feed-following-v2"、
     * for_you → "feed-for_you-v2"；纯字符串拼接。
     */
    private String cursorType(FeedMode mode) {
        return "feed-" + mode.getApiValue() + "-v2";
    }

    /**
     * 构造"游标缺少必要位置"的 400 业务异常（code = INVALID_CURSOR）。
     * 使用场景：被本类 query 在两处调用——解码后的游标 offset 为负数，或回源重建时
     * 游标里 boundaryTime/boundaryId 缺任一项（复合条件：position 非空且两者任一为 null）。
     * 实现要点：纯内存构造 BusinessException.badRequest("INVALID_CURSOR", "Feed 分页游标缺少必要位置")。
     */
    private BusinessException invalidCursor() {
        return BusinessException.badRequest("INVALID_CURSOR", "Feed 分页游标缺少必要位置");
    }
}
