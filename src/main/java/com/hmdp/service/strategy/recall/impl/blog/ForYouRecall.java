package com.hmdp.service.strategy.recall.impl.blog;

/*
 * 现实业务背景：用户打开为你推荐时，既需要常互动作者的内容，也需要圈外发现内容，避免推荐完全等同关注流。
 * 实际触发：for_you 模式调用本召回通道，根据点赞作者交互生成熟悉作者和发现候选，并把交互信号交给排序。
 */

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.AuthorInteractionDTO;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.strategy.recall.RecallContext;
import com.hmdp.service.strategy.recall.RecallStrategy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 为 For You 推荐先挑出一批可能感兴趣的博客 ID，后续排序器再决定最终顺序。
 * 这个“先挑候选”的步骤称为召回。
 *     1. 从点赞历史判断作者偏好：用户给某位作者的博客点过越多次赞，
 *     就优先取一部分该作者的新内容；排序阶段只需要次数汇总，不需要每条历史点赞明细。
 *     2. 同时加入陌生作者：候选结果一部分来自偏好作者，另一部分来自用户没怎么互动过的作者，
 *     防止推荐页永远只有熟悉账号，让用户仍能发现新内容。
 *     3. 这是人工规则，不是机器学习概率：点赞次数和热度只是可解释的排序信号，
 *     分数表示规则下的相对先后，不能解释成“用户有 80% 概率喜欢”。
 *
 * 具体怎么分配名额（以 {@link RecallContext}（召回上下文：用户、时间边界、候选上限、共享信号）
 * 的 limit = 200 为例）：
 *     4. 先查偏好作者：调 {@code blogLikeMapper.selectAuthorInteractions} 按作者聚合当前用户的
 *     点赞记录，得到"作者 ID -> 点赞次数"列表，同时把它写进 ctx.extra 的 "authorInteractions"
 *     键，排序阶段（BlogFeedService 算作者亲和度）直接复用，不再查库。
 *     5. 熟悉作者路：名额 personalizedLimit = min(limit/2, 偏好作者数 * 10)。limit=200 时即
 *     min(100, 偏好作者数*10)：3 个偏好作者取 30 条，偏好作者达到 10 个及以上就取满 100 条；
 *     条件是 user_id IN (偏好作者) 且排除自己，按 liked 降序、create_time 降序、id 降序取前 N 条 ID。
 *     6. 发现路：用剩余名额（limit - 已取条数，如上例 200 - 30 = 170）补齐，条件是
 *     user_id NOT IN (偏好作者) 且排除自己，排序方式相同，保证推荐不只有熟人内容。
 *     7. 用户没有任何点赞历史时，偏好作者列表为空、熟悉路跳过，全部名额走发现路，
 *     退化为"全站热门+最新"。
 *     8. 两路结果放进 LinkedHashSet 去重合并；同时叠加与 FollowFeedRecall 相同的翻页边界：
 *     create_time 早于 maxTime，或等于 maxTime 且 id 小于 extra 里的 lastId。
 *     9. 通道注册名为 "for-you"。
 *
 */
@Component
public class ForYouRecall implements RecallStrategy {

    public static final String AUTHOR_INTERACTIONS = "authorInteractions";

    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;

    /**
     * 构造器：注入查博客候选与作者互动统计所需的两个 Mapper，仅字段赋值，无业务逻辑。
     * 使用场景：Spring 容器装配 {@code @Component} 本类时通过构造器注入调用一次，项目内无其他调用方。
     * 实现要点：blogMapper 负责熟悉路/发现路两段 tb_blog 候选查询；
     * blogLikeMapper 负责按作者聚合当前用户点赞记录（tb_blog_like 联表 tb_blog，
     * 按作者分组 COUNT(*)、按次数倒序取前 50 行）。
     */
    public ForYouRecall(BlogMapper blogMapper, BlogLikeMapper blogLikeMapper) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
    }

    /**
     * "for-you" 通道召回：按点赞历史找偏好作者，先取熟悉作者候选，再用发现路补满剩余名额。
     * 使用场景：仅被 {@link RecallOrchestrator}（召回编排器：按通道名调用各策略并去重合并）在通道列表
     * 含 "for-you" 时调用——BlogFeedService.rebuild 仅 for_you 模式追加本通道（与 "follow" 通道合并）；
     * ctx.limit 固定 200。
     * 实现要点：
     * 1. blogLikeMapper.selectAuthorInteractions(userId) 按作者聚合点赞记录（按次数倒序、最多 50 行，
     *    null 按空列表处理），并把结果写入 ctx.extra 的 {@link #AUTHOR_INTERACTIONS} 键，
     *    排序阶段由 BlogFeedService.rankingContext 复用（算作者亲和度），不再查库。
     * 2. 熟悉路名额 personalizedLimit = min(limit/2, 偏好作者数 * 10)：偏好作者非空且名额 > 0
     *    才执行 queryCandidates(discovery=false)。
     * 3. 发现路：queryCandidates(discovery=true)，名额 = limit - 熟悉路实际并入的条数；
     *    没有点赞历史时偏好作者为空，熟悉路跳过，全部名额走发现路，退化为"全站热门+最新"。
     * 4. 两路结果进 LinkedHashSet 去重保序，转 ArrayList 返回。
     */
    @Override
    public List<Long> recall(RecallContext ctx) {
        List<AuthorInteractionDTO> interactions = blogLikeMapper.selectAuthorInteractions(ctx.getUserId());
        if (interactions == null) {
            interactions = Collections.emptyList();
        }
        if (ctx.getExtra() != null) {
            ctx.getExtra().put(AUTHOR_INTERACTIONS, interactions);
        }

        List<Long> preferredAuthors = interactions.stream()
                .map(AuthorInteractionDTO::getAuthorId)
                .collect(Collectors.toList());
        int personalizedLimit = Math.min(ctx.getLimit() / 2, preferredAuthors.size() * 10);
        LinkedHashSet<Long> result = new LinkedHashSet<>();

        // 两路召回分别保证“熟悉作者相关性”和“圈外内容发现”，再去重合并。
        if (!preferredAuthors.isEmpty() && personalizedLimit > 0) {
            result.addAll(queryCandidates(ctx, preferredAuthors, false, personalizedLimit));
        }
        result.addAll(queryCandidates(ctx, preferredAuthors, true, ctx.getLimit() - result.size()));
        return new ArrayList<>(result);
    }

    /**
     * 按"熟悉/发现"二选一的条件查一批博客 ID，统一排除自己、叠加游标边界并按热度排序截断。
     * 使用场景：仅被本类 recall 调用两次——discovery=false 查偏好作者的熟悉路，
     * discovery=true 查非偏好作者的发现路。
     * 实现要点：SQL 条件（tb_blog，只查 id 列）：user_id != 当前用户（排除自己）；
     * discovery=true 时 user_id NOT IN (偏好作者)，false 时 user_id IN (偏好作者)；
     * 偏好作者列表为空时两个集合条件都不加（发现路查全站除自己）；再叠加 applyBoundary 的游标条件；
     * 排序 ORDER BY liked DESC, create_time DESC, id DESC，LIMIT limit；limit <= 0 直接返回空列表不查库。
     */
    private List<Long> queryCandidates(
            RecallContext ctx,
            List<Long> preferredAuthors,
            boolean discovery,
            int limit
    ) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .select(Blog::getId)
                .ne(Blog::getUserId, ctx.getUserId());
        if (!preferredAuthors.isEmpty()) {
            if (discovery) {
                wrapper.notIn(Blog::getUserId, preferredAuthors);
            } else {
                wrapper.in(Blog::getUserId, preferredAuthors);
            }
        }
        applyBoundary(wrapper, ctx);
        wrapper.orderByDesc(Blog::getLiked, Blog::getCreateTime, Blog::getId)
                .last("LIMIT " + limit);
        return blogMapper.selectList(wrapper).stream()
                .map(Blog::getId)
                .collect(Collectors.toList());
    }

    /**
     * 把游标翻页边界追加到查询条件：create_time 早于 maxTime，或等于 maxTime 且 id 小于 extra 里的 lastId。
     * 使用场景：仅被本类 queryCandidates 调用（熟悉路与发现路共用同一边界，保证两路翻页口径一致）。
     * 实现要点：maxTime 为 null 或 <= 0（首页）不加任何条件；否则把毫秒值按 UTC 转成 LocalDateTime 再比较；
     * lastId 为 null 时只加 create_time < time，有 lastId 时用 MyBatis-Plus 的条件 or(lastId != null, ...)
     * 追加 OR (create_time = time AND id < lastId)，同一时刻发布的博客靠 ID 继续切分。
     */
    private void applyBoundary(LambdaQueryWrapper<Blog> wrapper, RecallContext ctx) {
        if (ctx.getMaxTime() == null || ctx.getMaxTime() <= 0) {
            return;
        }
        LocalDateTime time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ctx.getMaxTime()), ZoneOffset.UTC);
        Long lastId = getLastId(ctx.getExtra());
        wrapper.and(query -> query.lt(Blog::getCreateTime, time)
                .or(lastId != null, nested -> nested.eq(Blog::getCreateTime, time)
                        .lt(Blog::getId, lastId)));
    }

    /**
     * 从 {@link RecallContext}（召回上下文：用户、时间边界、候选上限、共享信号）的 extra 里取翻页游标的 lastId。
     * 使用场景：仅被本类 applyBoundary 调用，用于切分"同一时刻发布的多条博客"的翻页边界。
     * 实现要点：读 extra 的 "lastId" 键（BlogFeedService.rebuild 放入上一页最后一条博客的 ID）；
     * extra 为 null 或值不是 Number 时返回 null（等价于首页边界），否则取 longValue()；
     * 判定口径与 FollowFeedRecall.getLastId 一致（Long 与其它 Number 都接受）。
     */
    private Long getLastId(Map<String, Object> extra) {
        if (extra == null || !(extra.get("lastId") instanceof Number)) {
            return null;
        }
        return ((Number) extra.get("lastId")).longValue();
    }

    /**
     * 返回召回通道注册名 "for-you"。
     * 使用场景：被 {@link RecallStrategyRegistry}（召回策略注册表）的 init 以返回值作键登记
     * （@PostConstruct 建 Map）；BlogFeedService.rebuild 仅 for_you 模式把 "for-you" 加入通道列表后
     * 经 RecallOrchestrator.multiRecall 按名查找。
     * 实现要点：返回常量字符串 "for-you"（含连字符）；通道名只在服务内部流转，不暴露给客户端。
     */
    @Override
    public String getStrategyName() {
        return "for-you";
    }
}
