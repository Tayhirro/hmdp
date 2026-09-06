# HMDP 当前有效修复说明

本文件保存项目各问题经过多次改进后，当前最终生效的修复方案。它不是按时间追加的历史流水账。

## 记录约定

1. 不同问题使用独立编号：`FIX-YYYYMMDD-NN`。
2. 同一问题的后续改进必须并入已有记录，不重复创建多条相互覆盖的记录。
3. 合并时删除已经被替代的实现，只保留当前代码实际采用的最终方案。
4. 新旧方案发生冲突时，以最新实现为准。
5. 更新记录时同步维护“最新更新日期、修改文件、回归测试和验证结果”。
6. 状态统一使用：`处理中`、`已修复`、`已验证`、`已回退`。
7. 验证结果只记录实际执行过的命令，不把计划执行的检查写成已通过。
8. 每次代码改动完成后必须同时分析本文件与 `docs/hmdp-项目架构分析.md`：Fix 记录采用“旧有效内容 ∪ 本次有效内容”，冲突部分保留最新实现；架构文档必须与当前接口、数据结构和调用流程一致。
9. 架构文档不能只记录“改了什么”；必须在对应功能位置补充“实现机制、设计目的、解决的问题和可观察的改进效果”，例如安全边界、数据一致性、并发行为、可维护性或用户体验发生了什么变化。
10. 本约定已同步到项目级 `AGENTS.md`，后续代码或数据库变更必须执行，不依赖单次会话记忆。

## 修复索引

| 编号 | 日期 | 模块 | 摘要 | 状态 |
|---|---|---|---|---|
| [FIX-20260723-01](#fix-20260723-01-feed-排序与游标分页) | 2026-08-04 | Blog Feed | 产品模式、稳定排序、版本化快照、opaque 游标与曝光去重 | 已验证 |
| [FIX-20260724-01](#fix-20260724-01-关注关系唯一约束) | 2026-07-24 | Follow | 清理重复关注并增加数据库联合唯一约束 | 已验证 |
| [FIX-20260724-02](#fix-20260724-02-点赞-mysql-唯一数据源) | 2026-08-04 | Blog Like | 移除点赞 Redis ZSet，拆分幂等点赞/取消接口 | 已验证 |
| [FIX-20260724-03](#fix-20260724-03-上传删除接口安全加固) | 2026-08-05 | Upload / Blog / Auth | 图片资产、发布恢复、编辑删除事务与提交后清理 | 已验证 |
| [FIX-20260804-01](#fix-20260804-01-博客列表批量装配与游标分页) | 2026-08-04 | Blog Read | 消除 1+2N SQL，统一 opaque keyset cursor | 已验证 |
| [FIX-20260804-02](#fix-20260804-02-http-错误语义限流与配置外置) | 2026-08-04 | Web / Config | HTTP 状态、错误码、traceId、写接口限流与敏感配置外置 | 已验证 |
| [FIX-20260805-01](#fix-20260805-01-博客-api-隔离独立幂等与图片删除补偿) | 2026-08-05 | Blog / Upload | API 隔离、独立幂等记录与图片删除补偿 | 已验证 |
| [FIX-20260805-02](#fix-20260805-02-dto-用途与字段边界注释补全) | 2026-08-05 | DTO | 请求、响应和内部投影的用途与信任边界说明 | 已验证 |
| [FIX-20260805-03](#fix-20260805-03-核心设计注释白话化与幂等命名重构) | 2026-08-20 | Documentation | 核心设计与全部 Service Impl 接口流程注释白话化 | 已验证 |
| [FIX-20260806-01](#fix-20260806-01-基础功能与故障增强分阶段文档) | 2026-08-06 | Architecture / Reliability | 拆分学习顺序，并建立带示例的全域方法目录 | 已验证 |
| [FIX-20260809-01](#fix-20260809-01-搜索能力从店铺域独立) | 2026-08-09 | Search / Shop | 店铺名称搜索迁出店铺域并建立稳定分页合同 | 已验证 |
| [FIX-20260809-02](#fix-20260809-02-统一搜索与三个垂直域第一阶段落地) | 2026-08-11 | Search | 店铺、笔记、用户垂直搜索与 Nuxt 统一结果页 | 已验证 |
| [FIX-20260811-01](#fix-20260811-01-除下单外基础业务与-nuxt-闭环) | 2026-08-11 | Comment / Nuxt / Blog / Social | 评论后端与除订单外的 Nuxt 基础业务入口闭环 | 已验证 |

## 详细记录

### FIX-20260723-01 Feed 排序与游标分页

- 首次记录：2026-07-22
- 最新更新：2026-08-05
- 状态：已验证
- 模块：博客 Feed、召回/排序/重排、Redis 快照、曝光去重、前端
- 影响接口：`GET /blog/feed`

#### 问题现象

- `simple` 策略计算了分数但没有实际排序，首次请求可能仍保持召回顺序。
- 缓存与首次请求采用不同的顺序规则时，连续分页可能重复或遗漏博客。
- Feed 的最终顺序已经由 Java 排序策略确定，Redis 不应再次根据 score 改变顺序。
- 旧游标由多个公开参数组成，客户端容易混用时间、博客 ID、快照位置和内部排序策略。
- 缓存曾整表读取 List 后线性寻找博客 ID，候选池扩大后读取开销随快照长度增长。
- 无游标的第一页请求无法表达“强制刷新”，新帖子发布后可能继续命中旧快照。
- Service 使用“候选数是否达到 200”推测召回源是否还有数据，混淆了召回容量和前端快照分页；同时每通道只召回 100 条，导致当前唯一有效的 Follow 通道无法填满 200 条候选池。
- 空快照没有实体元素，若不写 marker 会被误判为缓存未命中并重复回源。
- For You 缺少真实个性化召回、已读过滤和作者打散，内部算法名还泄漏为 API 参数。

#### 根因

1. `SimpleRankingStrategy.rank()` 原来直接返回召回结果。
2. Redis ZSet 会按 score 排序，并在同分时按 member 字符串排序，不能原样保存 Java 指定顺序。
3. 缓存位置游标、业务时间边界和排序分数的职责没有完全分离。
4. 前端仍使用旧版 `minTime + offset` 分页数据结构。
5. API 缺少明确的刷新信号，后端无法区分“读取当前快照第一页”和“丢弃旧快照重新拉取最新内容”。
6. `hasMore` 的职责边界不清晰：它只应描述当前快照是否还有下一页，而不是推测召回源是否还有数据。
7. 召回通道由 Bean 枚举顺序隐式决定，产品模式、排序算法和缓存 key 没有稳定边界。

#### 修复内容

##### 1. 统一稳定排序规则

所有排序策略统一执行：

1. 按业务分数降序排列。
2. 业务分数相同时，按博客 ID 数值降序排列。

这样可以保证排序结果是确定的全序，`simple`、`time` 和 `weighted` 不会因同分而漂移。

##### 2. 版本化 Redis List 快照

每个用户、每种产品模式使用一个“当前快照指针”和带唯一 ID 的不可变 List：

```text
feed:cache:{userId}:{mode}:v2:current                 -> snapshotId
feed:cache:{userId}:{mode}:v2:snapshot:{snapshotId}  -> [marker, blogId|createTime, ...]
```

Lua 一次性写入 marker、全部条目、TTL 和 current 指针，避免读到半成品；marker 使“没有候选”也能成为有效缓存。刷新只删除 current 指针，已返回给客户端的旧快照在 TTL 内仍可继续翻页。

##### 3. 使用 opaque `snapshotId + offset` 游标续页

响应只暴露 Base64URL 编码的 `nextCursor`。服务端校验游标类型后解出 `snapshotId + offset`，直接执行 `LRANGE offset+1 ...`，不再整表读取或线性查找博客 ID。缓存不可用时，游标还携带 `boundaryTime + boundaryId` 供 Following 按 `(create_time,id)` 稳定回源。

##### 4. 对外只暴露产品模式

接口使用 `mode=following|for_you`；客户端不再选择 `time/weighted` 等内部算法。Following 明确组合 `follow + time`，For You 明确组合 `follow + for-you + weighted`，召回顺序不依赖 Spring Bean 枚举。

##### 5. `refresh=true` 强制创建新快照

```http
GET /blog/feed?mode=following&refresh=true
```

Service 删除当前模式的指针、忽略旧 cursor，从最新内容重新召回、排序和写入新快照。旧 snapshot key 不立即删除，因此刷新与正在进行的连续翻页互不干扰。

##### 6. 统一候选池与 `hasMore` 语义

Feed 采用固定 Top-K 快照，不对快照之外的数据做连续召回：

1. 每条召回通道最多返回 200 个博客 ID。
2. 多通道合并去重后，最终候选池仍截断为最多 200 条。
3. 当前只有 Follow 通道有效时，该通道可以独立填满 200 条快照，不再因另一条空通道浪费候选额度。
4. 首次构建快照时，`hasMore` 只按“排序后候选数是否大于 `PAGE_SIZE`”计算。
5. 命中缓存时读取 `PAGE_SIZE + 1` 条，判断当前 Redis List 是否还有下一页。
6. 删除 `candidateIds.size() >= CANDIDATE_POOL_SIZE` 这种对召回源剩余数据的推测。

因此 `hasMore` 只是返回给前端的快照分页信号。最多 200 条的快照读完后返回 `false`；用户主动刷新时再从最新数据生成新的 Top 200 快照。

##### 7. For You 个性化与曝光控制

1. 从当前用户点赞历史按作者聚合交互信号，召回熟悉作者内容。
2. 同时召回非熟悉作者的发现内容，与 Following 候选合并去重。
3. `WeightedRankingStrategy` 使用亲和、热度和新鲜度做可解释规则排序，不把分数伪装成 ML 概率。
4. `feed:exposure:{userId}` 保存近 7 天最多 5000 条曝光；For You 排序前过滤已读，排序后限制同作者连续占位。
5. Redis 故障统一 fail-open；Following 可按游标时间边界回源，For You 无法复现跨页排序时只返回本页并诚实设置 `hasMore=false`。

#### 修改文件

- `src/main/java/com/hmdp/service/feedcache/FeedCacheService.java`
- `src/main/java/com/hmdp/service/feed/BlogFeedService.java`
- `src/main/java/com/hmdp/service/feed/FeedMode.java`
- `src/main/java/com/hmdp/service/feed/FeedExposureService.java`
- `src/main/java/com/hmdp/controller/BlogController.java`
- `src/main/java/com/hmdp/service/IBlogService.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/service/strategy/ranking/impl/SimpleRankingStrategy.java`
- `src/main/java/com/hmdp/service/strategy/ranking/impl/SimpleTimeRankingStrategy.java`
- `src/main/java/com/hmdp/service/strategy/ranking/impl/WeightedRankingStrategy.java`
- `src/main/java/com/hmdp/service/strategy/recall/impl/blog/ForYouRecall.java`
- `src/main/java/com/hmdp/mapper/BlogLikeMapper.java`
- `src/main/resources/nginx-1.18.0/html/hmdp/info.html`
- `src/test/java/com/hmdp/service/feedcache/FeedCacheServiceTest.java`
- `src/test/java/com/hmdp/service/impl/BlogServiceImplFeedTest.java`
- `src/test/java/com/hmdp/service/strategy/ranking/impl/RankingStrategyOrderTest.java`
- `docs/hmdp-项目架构分析.md`
- `fix.md`

#### 兼容性影响

- 旧 `GET /blog/of/follow` 与多参数游标被 `GET /blog/feed`、`mode` 和单个 opaque `cursor` 替代，前端已同步。
- 响应统一为 `{list,nextCursor,hasMore}`；客户端必须原样回传 `nextCursor`，不能解析或拼装内部字段。
- Feed 缓存只使用版本化 Redis List 快照，不包含 ZSet 或旧 key 的兼容分支。
- `hasMore` 严格表示当前最多 200 条快照是否还有下一页，不承诺全量历史无限滚动。

#### 回归测试

- 验证 List 按调用方提供的顺序写入。
- 验证使用 `snapshotId + offset` 直接续页，空结果也能命中 marker 快照。
- 验证刷新只切换 current 指针，不破坏旧游标对应的不可变快照。
- 验证 Lua 原子发布快照，缓存异常时 fail-open。
- 验证 `simple` 策略真正按业务分数降序排列。
- 验证所有排序策略在同分时按数字博客 ID 稳定排序。
- 验证 `refresh=true` 会删除当前指针、跳过缓存并从最新内容重新召回。
- 验证刷新重建时每条召回通道的候选上限为 200。
- 验证 Service 不再根据候选池是否填满推测召回源是否还有数据。
- 验证 Following/For You 的固定召回组合、曝光过滤和作者打散。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
inline JavaScript node --check: PASS
```

测试日志中的 `sms failed` 是现有测试主动模拟短信发送失败的预期场景，不是测试失败。

#### 文档一致性分析

- Fix：当前只保留产品模式、稳定排序、版本化 List 快照、opaque 游标、强制刷新和曝光去重方案；旧多参数游标不再生效。
- 架构分析：同步记录召回、排序、重排、快照、缓存降级和曝光副作用的完整边界。
- 当前一致结论：普通下拉复用不可变 Top 200 快照；刷新只切换 current 指针；For You 是可解释规则基线。

#### 后续事项

- 使用真实 Redis 验证 List 写入、读取和连续分页。
- 验证关注、取消关注后的 Feed 缓存失效。
- 用离线指标与 A/B 实验校准 For You 的规则权重，再决定是否升级为学习排序。

---

### FIX-20260724-01 关注关系唯一约束

- 首次记录：2026-07-24
- 最新更新：2026-07-24
- 状态：已验证
- 模块：关注关系、Flyway、MySQL
- 影响接口：`PUT /follow/{id}/{isFollow}`

#### 问题现象

- 旧版 `FollowServiceImpl.follow()` 通过“先查询、后写入”判断是否需要关注或取关，并依赖 `DuplicateKeyException` 把并发重复关注处理为幂等成功。
- `tb_follow` 原来只有主键，没有 `(user_id, follow_user_id)` 唯一约束，并发请求可能同时插入相同关注关系，异常兜底无法生效。
- 参数校验、存在性查询、数据库写入、并发异常处理、Redis 同步和 Feed 缓存失效全部集中在一个方法中，职责边界不清晰。

#### 根因

业务层的“先查询、后插入”不是原子操作。只有数据库联合唯一约束才能在多个线程或多个应用实例之间可靠地阻止重复关系。

#### 修复内容

1. 新增 `V5__add_follow_unique_constraint.sql`，按 `(user_id, follow_user_id)` 清理历史重复记录，每组保留 `id` 最小的一条，再添加联合唯一索引 `uk_follow_user_follow_user`。
2. `FollowMapper.insertIfAbsent()` 使用 `INSERT ... ON DUPLICATE KEY UPDATE id = id`，首次关注执行插入，重复关注由数据库直接转成无副作用成功，不再使用异常作为正常控制流。
3. `FollowMapper.deleteRelation()` 直接按用户和目标用户删除；删除 0 行也表示当前已经处于“未关注”状态，按幂等成功处理。
4. `FollowServiceImpl.follow()` 只负责业务校验、选择关注或取关命令、发布 `FollowChangedEvent`，删除关注状态预查询和 `DuplicateKeyException` 分支。
5. `FollowChangedEventListener` 在数据库事务提交后更新 Redis Follow Set，并失效 Caffeine 关注缓存和各排序策略的 Redis Feed 快照；事务回滚时不会执行缓存同步。

没有修改可能已经执行过的 `V1__init.sql`，避免破坏 Flyway 历史迁移校验；新库会依次执行 V1 至 V5，同样能获得唯一约束。

#### 修改文件

- `src/main/java/com/hmdp/mapper/FollowMapper.java`
- `src/main/java/com/hmdp/service/impl/FollowServiceImpl.java`
- `src/main/java/com/hmdp/service/follow/FollowChangedEvent.java`
- `src/main/java/com/hmdp/service/follow/FollowChangedEventListener.java`
- `src/main/resources/db/migration/V5__add_follow_unique_constraint.sql`
- `src/test/java/com/hmdp/service/impl/FollowServiceImplTest.java`
- `src/test/java/com/hmdp/service/follow/FollowChangedEventListenerTest.java`
- `docs/hmdp-项目架构分析.md`
- `fix.md`

#### 兼容性影响

- Flyway 启动迁移时会删除历史重复关注关系，只保留每组最早的一条。
- 接口请求和响应结构不变；重复关注和重复取关都返回幂等成功。
- Redis 与 Feed 缓存同步从数据库事务内部调整为事务提交后事件处理。

#### 回归测试

- 验证首次关注调用 Mapper 幂等插入并发布关注变更事件。
- 验证 Mapper 返回“关系已存在”时重复关注仍成功。
- 验证删除 0 行时重复取关仍成功。
- 验证关注自己会在写数据库前被拒绝。
- 验证关注和取关事件分别添加、移除 Redis Set 成员。
- 验证事件会失效关注缓存和各排序策略的 Feed 缓存。
- 验证完整项目可在新增迁移脚本存在时正常编译和执行单元测试。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
```

#### 文档一致性分析

- Fix：保留数据库唯一约束与历史清理方案，并用 Mapper 幂等命令替代 Service 预查询和异常控制流。
- 架构分析：同步更新 `tb_follow` 约束、Mapper 幂等 SQL、事务提交后缓存事件和 V5 Flyway 迁移说明。

#### 后续事项

- 在连接真实 MySQL 的集成环境执行 V5，核对历史重复记录清理数量。

---

### FIX-20260724-02 点赞 MySQL 唯一数据源

- 首次记录：2026-07-24
- 最新更新：2026-08-04
- 状态：已验证
- 模块：博客点赞、MySQL
- 影响接口：`PUT /blog/{id}/like`、`DELETE /blog/{id}/like`、`GET /blog/{id}`、`GET /blog/likes/{id}`

#### 问题现象

- 博客详情和点赞操作会把单个点赞用户回填到 `blog:liked:{blogId}` ZSet。
- 点赞榜原来只要发现 ZSet 非空就直接分页返回，可能把只包含一个或少量用户的部分缓存误认为完整榜单，导致用户遗漏。
- 旧 `PUT /blog/like/{id}` 同时表示“点赞”和“取消点赞”，服务端需要先查当前状态再决定动作。客户端超时重试、快速双击或并发请求时，同一个请求的重试可能反向改变状态，不具备幂等语义。
- 点赞命令仅返回“点赞成功/取消点赞成功”字符串，Nuxt 前端根据本地旧值自行 `+1/-1`；并发状态变化或历史计数不一致时，客户端无法以服务端最终结果校准。

#### 根因

同一个 ZSet 同时承担“单用户点赞状态缓存”和“完整点赞榜缓存”，但没有任何完整性标记。仅凭 key 非空无法判断其中是否包含全部点赞关系。同时，“先读状态、再反转状态”把用户意图隐藏在服务函数内，使同一 HTTP 请求重放后产生不同结果。命令响应又没有携带服务端最终状态，迫使前端从旧本地状态推导新状态。

#### 修复内容

1. 删除 `BlogServiceImpl` 中全部 `blog:liked:{blogId}` ZSet 读取、写入和榜单回填逻辑。
2. 把旧切换接口拆成 `PUT /blog/{id}/like` 明确点赞和 `DELETE /blog/{id}/like` 明确取消点赞；前端根据当前 `isLike` 选择 HTTP 方法。
3. `BlogLikeMapper.insertIfAbsent()` 使用 `INSERT IGNORE`，由 `UNIQUE(blog_id, user_id)` 在数据库内原子判断是否已点赞；不再在 Service 中先查再插，也不再依赖 `DuplicateKeyException` 控制正常分支。
4. 只有 `INSERT IGNORE` 真正插入 1 行时才使 `tb_blog.liked + 1`；重复点赞影响 0 行，直接幂等成功。
5. `BlogLikeMapper.deleteRelation()` 直接删除指定关系；只有真正删除 1 行时才使 `tb_blog.liked - 1`，重复取消影响 0 行并幂等成功。
6. 关系行增删和计数更新继续位于同一 MySQL 事务；计数更新失败时抛出异常，整个事务回滚，不留“有关系没计数”或“没关系却扣数”。
7. 新增 `BlogLikeStateDTO`，点赞和取消命令在事务内重新读取真实点赞关系和博客最终计数，统一返回 `{liked, likeCount}`；笔记不存在时明确失败。
8. Nuxt 和静态页面改为使用响应值覆盖 `isLike/liked`，不再在客户端自行加减。
9. 前端按 blogId 记录进行中请求：同一篇博客未完成前拒绝第二次点击，防止同页面响应乱序；网络错误时回源博客详情重新对齐，回源也失败则保留原状态。
10. `fillBlogLikedFlag()` 直接根据 `(blog_id, user_id)` 查询 MySQL，博客详情和列表的 `isLike` 不再依赖 Redis。
11. `queryBlogLikes()` 始终从 `tb_blog_like` 按 `create_time DESC, id DESC` 做滚动分页。
12. 删除 `RedisConstants.BLOG_LIKED_KEY` 和 `BlogServiceImpl` 的点赞 Redis 依赖。
13. 在后端写链路和前端调用处补充精简设计注释，固定“目标状态、数据库裁决、同事务、权威回读、失败回源”五条约束。
14. 在 `BlogServiceImpl` 类注释集中列出已验证、应继续保留的设计：显式点赞语义、唯一约束与事务、权威响应、图片状态机、稳定召回顺序和 Feed 分层。

#### 规范与大型平台实践依据

- [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html#name-idempotent-methods) 定义 `PUT` 和 `DELETE` 为幂等方法，并明确说明客户端在连接中断、未读到响应时可重试幂等请求。
- [GitHub Starring REST API](https://docs.github.com/en/rest/activity/starring) 对同一用户的标星关系使用无请求体的 `PUT`，取消使用 `DELETE`，与本项目“确保关系存在/不存在”的语义直接对应。
- [X Like Post API](https://docs.x.com/x-api/users/like-post) 将点赞和取消点赞拆分为不同命令，点赞响应返回 `data.liked`，客户端可以服务端状态对齐。
- [Stripe Idempotent Requests](https://docs.stripe.com/api/idempotent_requests) 说明连接错误后安全重试的核心是重复命令不重复产生副作用；当方法本身不幂等时才需额外幂等键机制。

#### 修改文件

- `AGENTS.md`
- `frontend/app/pages/index.vue`
- `frontend/app/types/api.ts`
- `src/main/java/com/hmdp/controller/BlogController.java`
- `src/main/java/com/hmdp/dto/BlogLikeStateDTO.java`
- `src/main/java/com/hmdp/mapper/BlogLikeMapper.java`
- `src/main/java/com/hmdp/service/IBlogService.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/utils/RedisConstants.java`
- `src/main/resources/nginx-1.18.0/html/hmdp/blog-detail.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/index.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/info.html`
- `src/test/java/com/hmdp/controller/BlogControllerLikeMappingTest.java`
- `src/test/java/com/hmdp/service/impl/BlogServiceImplLikesTest.java`
- `docs/hmdp-项目架构分析.md`
- `fix.md`

#### 兼容性影响

- 旧 `PUT /blog/like/{id}` 已移除，这是有意的接口语义调整；Nuxt 页面和三个静态页面已同步迁移。
- 新 `PUT/DELETE /blog/{id}/like` 的 `Result.data` 由成功提示字符串改为 `{liked: boolean, likeCount: number}`；所有仓库内客户端已同步更新。
- 博客详情响应结构不变；点赞榜已统一为 `{list,nextCursor,hasMore}` 游标页。
- 点赞状态与榜单全部增加 MySQL 读取，不再产生点赞 Redis 请求。
- 已经存在于 Redis 的旧 `blog:liked:*` key 不再被代码读取或更新，也不会由应用自动删除；部署后可按运维窗口单独清理。

#### 回归测试

- 验证点赞榜以数据库查询结果为权威数据。
- 验证数据库无点赞记录时返回结构稳定的空榜单。
- 验证博客详情从数据库读取当前用户点赞状态。
- 验证已存在点赞关系时，重复 `PUT` 仍幂等成功并返回 `{liked: true, likeCount}`。
- 验证点赞关系不存在时，重复 `DELETE` 仍幂等成功并返回 `{liked: false, likeCount}`。
- 验证 Controller 只暴露新 `PUT/DELETE /blog/{id}/like` 映射。
- 验证生产代码和 Redis 常量中不再存在 `BLOG_LIKED_KEY` 或 `blog:liked:`。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
corepack pnpm typecheck: SUCCESS
corepack pnpm exec eslint app/pages/index.vue: SUCCESS（0 errors、11 warnings）
静态页 index.html / info.html / blog-detail.html 内联 JavaScript 语法检查: SUCCESS
corepack pnpm lint: FAILED（项目现有 lint 基线共 221 个问题：111 errors、110 warnings，分布在多个既有前端文件）
```

#### 文档一致性分析

- Fix：保留 MySQL 唯一数据源方案，并把同一修复项下后续的显式幂等 API 改造合并记录。
- 架构分析：同步更新 PUT/DELETE 语义、Mapper 原子写入、事务边界、权威状态响应、客户端串行化与失败回源机制。

#### 后续事项

- 列表点赞状态已由 `BlogAssembler` 批量查询；只有压测证明 MySQL 点赞查询成为瓶颈后，才重新设计带 TTL 和完整性标记的独立缓存。

---

### FIX-20260724-03 上传删除接口安全加固

- 首次记录：2026-07-24
- 最新更新：2026-08-04
- 状态：已验证
- 模块：文件上传、博客发布、鉴权、静态页面、Flyway
- 影响接口：`POST /upload/blog`、`DELETE /upload/blog/{imageId}`、`POST /blog`、`PUT /blog/{id}`、`DELETE /blog/{id}`

#### 问题现象

- `/upload/**` 原来被排除在登录校验之外，匿名请求可以上传或删除文件。
- 删除操作使用 GET，不符合 HTTP 安全语义，也容易被预取、链接访问或跨站请求意外触发。
- 删除路径直接由上传根目录和请求参数拼接，没有确认归一化与真实路径仍位于上传目录内。
- 旧页面仍调用 `GET /upload/blog/delete`。
- 图片上传后只有磁盘路径，没有上传者、临时/已绑定状态和关联博客；知道 URL 的登录用户可能删除他人或已发布图片。
- 上传与发布是两个独立请求，用户离开页面或发布失败时会产生无法追踪和回收的孤儿文件。
- `POST /blog` 直接接收 `Blog` 实体和任意图片 URL，没有校验图片所有权、状态、数量、商户和文字内容。
- 上传端只信任文件扩展名，没有校验真实图片格式、大小和尺寸。
- 发布页原来只在 payload 未变化时复用 `clientRequestId`；若首次 POST 已提交但响应丢失，用户改稿后再次点击会生成新 key，从而把同一发布意图创建成两篇博客。

#### 根因

图片被当作无状态的公开路径，而不是有所有权和生命周期的业务资产。鉴权、HTTP 方法和路径检查只能阻止匿名访问与目录穿越，不能保证“谁上传、是否发布、能否删除、何时清理”。此外，前端把“payload 是否变化”误当成“是否属于同一次用户发布意图”：网络结果未知时，内容变化既不能直接复用旧 key，也不能立即换 key 创建新资源，必须先找回首次创建结果再更新同一博客。

#### 修复内容

1. 保留登录保护：`/upload/**` 不在公共路径列表中，上传、临时删除和发布都要求登录。
2. 新增 `V6__create_blog_image_asset.sql` 和 `tb_blog_image`，记录 `user_id`、`blog_id`、内部存储路径、公开 URL、类型、大小、尺寸、顺序及 `TEMP/BOUND/DELETING` 状态。
3. `POST /upload/blog` 校验图片并写入磁盘，再登记当前用户的 `TEMP` 资产，返回 `{id,url}`；数据库不保存图片二进制。
4. 删除接口改为 `DELETE /upload/blog/{imageId}`，不再接受客户端文件路径。Service 只允许上传者删除 `TEMP` 且未绑定博客的资产。
5. 删除和定时清理先使用条件更新抢占 `TEMP → DELETING`，避免与发布绑定竞争；失败时恢复 `TEMP`。
6. `POST /blog` 改用 `BlogPublishRequest`，只接受 `imageIds`。Service 校验图片存在、无重复、属于当前用户且未绑定。
7. 博客插入和 `TEMP → BOUND` 图片绑定位于同一个 MySQL 事务中；绑定时记录 `blog_id`、`sort_order` 和 `bind_time`。
8. `tb_blog.images` 由后端按已校验图片顺序生成，继续作为旧页面的 URL 读模型，前端不能直接指定图片路径。
9. 新增定时清理任务，默认每小时分批清理超过 24 小时仍为 `TEMP` 的孤儿图片。
10. 上传存储根目录和限制改为 `hmdp.upload` 外部配置；上传限制 5MB，只允许真实内容可识别且扩展名匹配的 JPG、PNG、GIF，并限制宽高和总像素数。
11. 存储路径完全由服务端生成；保存和删除均校验归一化路径、真实路径、普通文件和符号链接边界。
12. 发布端校验标题、正文、商户存在性和 1～9 张图片；正文写库前 HTML 转义并保留换行，旧页面标题统一改为文本插值。
13. 前端 `fileList` 改为 `{id,url}`，预览使用 URL，删除和发布使用图片资产 ID；移除手工 multipart boundary，并防止重复点击发布。
14. `PUT /blog/{id}` 使用独立 `BlogUpdateRequest` 和完整替换语义；服务端计算图片保留/新增/移除集合，不接受客户端指定删除对象。
15. 编辑和删除先以 `SELECT ... FOR UPDATE` 锁定博客，再校验作者；同一博客的并发写串行，图片状态更新同时带所有者、博客和原状态条件。
16. 编辑在一个 MySQL 事务内完成博客字段更新、`TEMP → BOUND`、保留图片重排和移除图片 `BOUND → DELETING`。
17. 删除在一个事务内将全部图片转为 `DELETING`，清理点赞、评论和博客；事务提交后才删除物理文件，失败保留元数据供清理任务重试。
18. `POST /blog` 增加 `clientRequestId`；V7 以 `(user_id, client_request_id)` 唯一约束和 `INSERT ... ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)` 原子收敛响应丢失、双击和并发重试。
19. 服务端保存规范化请求的 SHA-256 `request_hash`：相同 key、相同内容返回原博客 ID；相同 key、不同内容返回 409，防止误复用幂等键。
20. 发布页第一次点击时深拷贝不可变 payload A 并生成 `clientRequestId`；超时、断网、408 或 5xx 时保留该快照，下一次点击始终先原样重放 A，依靠数据库幂等插入取得首次 `blogId`。
21. 若用户在结果未知期间把表单改成 B，前端拿到首次 `blogId` 后调用 `PUT /blog/{id}` 更新同一博客；若 PUT 结果未知，保留 `confirmedBlogId` 并重放目标状态，不再执行第二次 POST。
22. 发布页使用独立 Axios 写实例保留 HTTP 状态和错误码：除幂等冲突 409 外，明确 4xx 清除失败快照；无响应、408 和 5xx 保守视为提交结果未知；未收敛期间移除图片只修改期望列表，避免把可能已 `BOUND` 的首次图片误当 `TEMP` 删除。
23. `BlogServiceImpl.saveBlog()` 注释拆分为“核心约束”和“严格同编号的真实场景”：分别解释首次快照、requestHash、数据库唯一约束、图片事务和 pull Feed，避免把多个机制揉进一点或让约束与例子错位。

#### 修改文件

- `src/main/java/com/hmdp/config/AuthMvcConfig.java`
- `src/main/java/com/hmdp/config/BlogImageProperties.java`
- `src/main/java/com/hmdp/config/WebExceptionAdvice.java`
- `src/main/java/com/hmdp/HmDianPingApplication.java`
- `src/main/java/com/hmdp/controller/UploadController.java`
- `src/main/java/com/hmdp/controller/BlogController.java`
- `src/main/java/com/hmdp/dto/BlogImageUploadDTO.java`
- `src/main/java/com/hmdp/dto/BlogPublishRequest.java`
- `src/main/java/com/hmdp/dto/BlogUpdateRequest.java`
- `src/main/java/com/hmdp/entity/BlogImage.java`
- `src/main/java/com/hmdp/exception/BusinessException.java`
- `src/main/java/com/hmdp/mapper/BlogImageMapper.java`
- `src/main/java/com/hmdp/service/IBlogImageService.java`
- `src/main/java/com/hmdp/service/IBlogService.java`
- `src/main/java/com/hmdp/service/impl/BlogImageServiceImpl.java`
- `src/main/java/com/hmdp/service/storage/BlogImageStorage.java`
- `src/main/java/com/hmdp/service/storage/StoredBlogImage.java`
- `src/main/java/com/hmdp/service/cleanup/BlogImageCleanupJob.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/utils/SystemConstants.java`
- `src/main/resources/application.yaml`
- `src/main/resources/db/migration/V6__create_blog_image_asset.sql`
- `src/main/resources/db/migration/V7__harden_blog_queries_and_publish.sql`
- `src/main/resources/nginx-1.18.0/html/hmdp/blog-edit.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/other-info.html`
- `src/test/java/com/hmdp/controller/UploadControllerTest.java`
- `src/test/java/com/hmdp/service/storage/BlogImageStorageTest.java`
- `src/test/java/com/hmdp/service/impl/BlogImageServiceImplTest.java`
- `src/test/java/com/hmdp/service/impl/BlogServiceImplPublishTest.java`
- `src/test/java/com/hmdp/service/impl/BlogServiceImplWriteTest.java`
- `docs/hmdp-项目架构分析.md`
- `fix.md`

#### 兼容性影响

- 原 `GET /upload/blog/delete` 和 `DELETE /upload/blog?name=...` 不再可用，客户端必须改用 `DELETE /upload/blog/{imageId}`。
- 上传响应从路径字符串改为 `{id,url}`。
- `POST /blog` 请求字段从 `images` URL 字符串改为 `imageIds` 数组。
- 上传与删除请求必须携带有效登录凭证。
- 已发布的 `BOUND` 图片不能通过临时图片接口删除。
- 博客编辑和删除端点已生效；非作者返回 403，不存在返回 404，客户端需按真实 HTTP 状态处理。
- 发布请求必须携带 1～64 位 `clientRequestId`；旧客户端需要同步升级。
- 发布页发生网络结果未知后，再次点击可能先重放原 POST、再自动调用 PUT；对用户仍表现为同一篇博客最终保存最新内容。
- 启动应用时 Flyway 必须执行 V6；未迁移数据库无法使用新上传和发布流程。

#### 回归测试

- 验证上传接口为当前用户登记 `TEMP` 图片资产并返回 ID 和 URL。
- 验证 PNG 内容识别、生成路径、物理删除、扩展名不匹配拒绝和目录穿越拒绝。
- 验证其他用户不能删除图片，发布加载保持客户端图片顺序，绑定逐张执行。
- 验证删除控制器只接受 `DELETE /upload/blog/{imageId}`。
- 验证发布时生成可信 URL 快照、转义正文并绑定图片资产。
- 验证发布相同 key/相同内容返回同一博客 ID，相同 key/不同内容返回冲突。
- 验证“首次 A 响应丢失 → 原样重放取回同一 ID → 显式 PUT 为 B”只插入一次并更新原博客。
- 验证编辑图片集合差异、作者权限、博客行锁和删除后的提交后文件清理。
- 验证完整项目原有 Feed、关注、点赞、用户和正则测试均未回归。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
```

#### 文档一致性分析

- Fix：只保留图片资产 ID、所有权、完整生命周期、不可变创建快照、未知结果恢复、编辑删除和提交后清理这一当前实现。
- 架构分析：同步更新发布/编辑/删除时序、幂等键、A→确认首次 ID→PUT B 的恢复流程、博客行锁、事务边界和文件补偿机制。

#### 后续事项

- 生产环境把 `BlogImageStorage` 替换为对象存储 + 预签名直传，并增加存储清单对账。
- 当前 `tb_blog.images` 是兼容旧页面的 URL 快照；新页面改为查询 `tb_blog_image` 后可移除重复存储。
- 如果启用 Session 认证，补充 CSRF 防护策略。

---

### FIX-20260804-01 博客列表批量装配与游标分页

- 首次记录：2026-08-04
- 最新更新：2026-08-04
- 状态：已验证
- 模块：热门博客、用户博客、点赞榜、Feed 读模型
- 影响接口：`GET /blog/hot`、`GET /blog/of/me`、`GET /blog/of/user`、`GET /blog/likes/{id}`、`GET /blog/feed`

#### 问题现象

- 列表原来对每篇博客各查一次作者、再查一次当前用户点赞关系；一页 50 篇约产生 `1 + 50 + 50 = 101` 次 SQL。
- 页码/offset 分页在点赞数或时间发生变化时容易重复、遗漏，深翻页还需要数据库扫描并丢弃前面大量记录。
- 多个接口自行回填作者和点赞状态，容易出现行为和性能不一致。

#### 根因

1. 单条详情装配函数被直接循环用于列表，没有先整页收集关联 ID。
2. 排序字段没有和唯一 ID 组成稳定全序，分页边界也没有封装为服务端可校验的游标。

#### 修复内容

1. 新增 `BlogAssembler`：整页收集 `authorId/blogId`，一次 `user_id IN (...)` 查询作者，一次 `blog_id IN (...)` 查询当前用户点赞关系，再在内存按原顺序回填。
2. 热榜的核心 SQL 从约 101 次收敛为 3 次：博客列表 1 次、作者批量 1 次、点赞状态批量 1 次；Feed 快照水合也复用同一装配入口。
3. 新增 `CursorCodec`，将游标类型、排序边界和缓存位置编码成 Base64URL opaque cursor；客户端只原样回传，服务端负责类型与范围校验。
4. 热榜按 `(liked,id)`、用户博客按 `(create_time,id)`、点赞榜按 `(create_time,id)` 做 keyset pagination，并统一返回 `{list,nextCursor,hasMore}`。
5. V7 增加 `idx_blog_hot_cursor`、`idx_blog_user_cursor` 和点赞榜覆盖索引，使游标条件与索引顺序一致。
6. 所有列表限制 `limit=1..50`，多取一条只用于判断 `hasMore`，不会把探测记录返回给客户端。

#### 修改文件

- `src/main/java/com/hmdp/service/blog/BlogAssembler.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/controller/BlogController.java`
- `src/main/java/com/hmdp/dto/CursorPageDTO.java`
- `src/main/java/com/hmdp/dto/cursor/CursorCodec.java`
- `src/main/java/com/hmdp/dto/cursor/CursorPayload.java`
- `src/main/java/com/hmdp/mapper/BlogLikeMapper.java`
- `src/main/resources/db/migration/V7__harden_blog_queries_and_publish.sql`
- `frontend/app/pages/index.vue`
- `frontend/app/types/common.ts`
- `src/main/resources/nginx-1.18.0/html/hmdp/index.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/info.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/other-info.html`
- `src/main/resources/nginx-1.18.0/html/hmdp/blog-detail.html`

#### 兼容性影响

- 上述列表接口不再返回裸数组或页码结果，统一返回 `CursorPageDTO`。
- 客户端不能解析、修改或跨接口复用 cursor；游标类型不匹配返回 400。

#### 回归测试

- 验证批量装配保持博客顺序并正确回填作者、头像和当前用户点赞状态。
- 验证热榜、用户博客和点赞榜在同排序值下使用 ID 形成稳定边界。
- 验证 cursor 类型隔离、非法游标拒绝、末页 `hasMore=false`。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
corepack pnpm typecheck: SUCCESS
静态页内联 JavaScript 语法检查: SUCCESS（5 files）
```

#### 后续事项

- 使用真实数据执行 `EXPLAIN ANALYZE` 并记录 p95/DB QPS 基线，防止后续装配逻辑重新退化为 N+1。

---

### FIX-20260804-02 HTTP 错误语义、限流与配置外置

- 首次记录：2026-08-04
- 最新更新：2026-08-04
- 状态：已验证
- 模块：Web 异常、认证、可观测性、Redis 限流、运行配置
- 影响接口：所有 HTTP 接口，重点为博客发布/编辑/删除/点赞/Feed

#### 问题现象

- 业务失败曾统一包在 HTTP 200 中，客户端、网关和监控无法区分参数错误、未登录、无权限、冲突、限流和服务端异常。
- 错误响应没有稳定业务码和 traceId，线上日志与用户请求难以关联。
- 高频写接口没有应用层频率保护；数据库、图片处理和 Feed 召回容易被重复请求放大。
- 数据库、Redis、JWT 和上传目录依赖固定本地配置，不适合不同环境部署。

#### 根因

1. `Result` 只表达业务成功/失败，没有与 Spring HTTP 状态建立映射。
2. 鉴权、参数绑定和未捕获异常分散处理，错误响应格式不统一。
3. 缺少按用户和动作划分的轻量限流边界。

#### 修复内容

1. `BusinessException` 携带 HTTP 状态与稳定错误码；统一异常处理分别返回 400、401、403、404、405、409、413、429 和 500。
2. `Result` 增加 `errorCode`、`traceId`；`TraceIdFilter` 接受合法 `X-Trace-Id` 或生成新值，并写入 MDC、响应头和错误体。
3. 登录拦截器直接返回结构化 401 JSON，不再用 HTTP 200 表示未登录。
4. `BlogRateLimitInterceptor` 使用 Redis Lua 原子执行 `INCR + 首次 EXPIRE`，按 `userId + action + minute` 限制发布 10、点赞 60、编辑/删除 20、Feed 120 次/分钟；超限返回 429 与 `Retry-After`。
5. 限流 Redis 故障采用 fail-open：记录告警但不把缓存故障升级为全站不可用；数据库唯一约束和事务仍负责最终正确性。
6. `application.yaml` 使用环境变量覆盖数据库、Redis、JWT secret 和上传根目录，移除固定密码。

#### 修改文件

- `src/main/java/com/hmdp/exception/BusinessException.java`
- `src/main/java/com/hmdp/dto/Result.java`
- `src/main/java/com/hmdp/config/WebExceptionAdvice.java`
- `src/main/java/com/hmdp/config/TraceIdFilter.java`
- `src/main/java/com/hmdp/interceptor/BlogRateLimitInterceptor.java`
- `src/main/java/com/hmdp/config/AuthMvcConfig.java`
- `src/main/java/com/hmdp/utils/RedisConstants.java`
- `src/main/resources/application.yaml`

#### 兼容性影响

- 失败响应现在使用真实 HTTP 状态；客户端仍可读取 `success/errorMsg`，并应优先使用 `errorCode` 分支处理。
- 部署环境必须提供真实的数据库密码和 JWT secret；开发默认值不再包含凭据。

#### 回归测试

- 验证参数绑定、业务异常、上传过大、未支持方法和未知异常的状态码与统一错误结构。
- 验证认证先于限流执行，限流 key 不跨用户/动作污染，Redis 故障时请求可继续进入业务层。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
```

#### 后续事项

- 在生产网关再增加 IP/设备维度的粗粒度限流，并对 429、5xx、限流 Redis 降级率和 traceId 关联日志建立告警。

---

### FIX-20260805-01 博客 API 隔离、独立幂等与图片删除补偿

- 首次记录：2026-08-05
- 最新更新：2026-08-05
- 状态：已验证（热榜版本化快照待新增 Redis Key 授权）
- 模块：博客发布/编辑/删除、点赞、查询、Feed、图片资产、数据库迁移
- 影响接口：`POST/PUT/DELETE /blog`、`GET /blog/{id}`、博客列表/Feed、`PUT/DELETE /blog/{id}/like`

#### 问题现象

- 修复前，查询接口直接返回 `Blog` Entity；当时 Entity 中的发布请求 ID、内容指纹等内部字段也可能进入响应，数据库加列还会在没有设计 API 的情况下改变返回内容。
- 修复前，防重复发布记录跟着博客一起保存。用户删除博客后，网络中迟到的旧 POST 可能被当成新请求，再创建一篇相同博客；重复请求还可能因为图片已经绑定或商户后来删除而得到不同结果。
- 提交后图片物理删除失败只留下 `DELETING`，旧定时任务只扫描 `TEMP`，进程崩溃或删除异常会形成永久残留。
- 编辑在普通参数校验前锁定博客，并通过 `updateById(entity)` 回写整行，扩大锁持有时间和误覆盖系统字段风险。
- `BlogServiceImpl` 同时承担命令、点赞、查询、Feed 并继承 `IService<Blog>`，其他代码可绕过权限、图片和幂等规则调用通用 CRUD。
- 正文以转义 HTML 和 `<br/>` 入库，详情回显后再次编辑会二次转义；游标依赖实例默认时区；点赞写入使用 `INSERT IGNORE` 会吞掉非重复键数据问题。

#### 根因

1. API 读模型、数据库实体和写入模型未隔离，持久化便利性侵入接口契约。
2. “这个发布请求以前处理过没有”的记录和博客存放在一起；博客删除后，服务端也忘记了这个请求已经成功过。
3. `afterCommit` 只提供时序边界，不是可靠任务；缺少可扫描、可领取、可退避的补偿状态。
4. 服务职责与事务边界没有按用例拆开，更新又缺少 SQL 字段白名单。

#### 修复内容

1. 新增 `BlogDetailDTO` 和 `BlogCardDTO`：详情返回完整正文，热榜、作者列表与 Feed 只返回卡片字段；`BlogAssembler` 批量查询作者/点赞后直接构造 DTO，不再修改或返回 Entity。
2. `Blog` 移除 API 临时字段和发布幂等字段；V8 新建 `tb_idempotency_record`，回填 V7 历史数据后删除 `tb_blog.client_request_id/request_hash`。
3. 发布顺序改为“确认登录 → 规范化标题/正文并计算内容指纹 → 查询请求记录 → 以前成功过就返回原博客 ID → 第一次收到才校验商户和图片 → 创建博客并绑定图片 → 保存成功结果”。博客删除时不删除请求记录，记录默认保留 30 天。
4. 两个相同请求并发到达时，数据库唯一约束让它们指向同一条记录；每个请求都有随机 `ownerToken`，只有随机号码与数据库一致的第一个请求可以继续创建博客，另一个不能重复创建。
5. 请求处理中记录、博客、图片绑定和请求成功状态处于同一个 MySQL 事务；中间任何一步失败都会全部撤销，成功记录到期后由定时任务分批清理。
6. V9 为图片增加 `retry_count/last_error/next_retry_time`。编辑/删除把解绑资产标成可领取的 `DELETING`；定时任务同时扫描 `TEMP` 和到期 `DELETING`，条件 claim 后幂等删文件与元数据，失败记录错误并退避。
7. 编辑先完成 request、ID、标题、正文、图片 ID 和商户无锁校验，再执行 `SELECT ... FOR UPDATE`；锁内只做作者校验、图片状态转换和字段白名单 UPDATE。
8. `BlogServiceImpl` 改为薄 Facade，分别委托 `BlogCommandService`、`BlogLikeService`、`BlogQueryService` 和既有 `BlogFeedService`；`IBlogService` 不再继承 `IService<Blog>`。
9. 点赞由普通 INSERT + 唯一键异常收敛重复关系；只捕获 `DuplicateKeyException`，其他数据库错误继续失败，不再用 `INSERT IGNORE` 降级。
10. 点赞用户通过一次 `IN` 查询后在内存按关系顺序恢复，移除动态 `ORDER BY FIELD(...)` 拼接。
11. V10 将历史 `<br/>`/HTML 实体内容还原为纯文本；新写链路只规范化换行，静态详情页使用 `v-text + white-space: pre-wrap` 安全展示。
12. 博客、Feed、召回与时间排序的 cursor epoch 统一使用 UTC，避免多实例系统时区不同造成分页边界不一致。
13. 参数错误统一为稳定业务码；发布未登录统一返回 `401/AUTH_REQUIRED`。
14. 热榜旧注释已修正为真实语义：当前实时 `(liked,id)` 游标仅近似稳定。版本化 Redis List 快照需要新增 Key，按项目约束等待授权后单独实现。

#### 设计说明（白话版）

- **为什么不直接返回数据库 Entity**：DTO 明确列出前端可以提交和看到的字段。数据库以后增加内部列，不会自动泄露到接口；编辑请求也不能改作者、点赞数或系统状态。
- **防重复发布记录是什么**：它是一张“这个请求处理过没有、第一次创建了哪个博客”的流水，不是博客内容。博客后来编辑或删除，不会改写这段请求处理历史。
- **为什么先查请求记录再查图片和商户**：相同请求以前成功过时，图片可能已经从临时状态变成已绑定，商户也可能后来被删除。此时应直接返回第一次的博客 ID，不能重新走一遍发布校验。
- **为什么一定需要数据库唯一约束**：两个请求可能在同一毫秒到达，都在前置查询中看到“不存在”。只有数据库能在真正写入时保证最终只有一条关系或请求记录。
- **行锁是干什么的**：编辑或删除时先锁住这一篇博客，使另一个并发修改暂时等待。标题、正文等普通参数先校验，避免明知请求无效还长时间占着锁。
- **字段白名单是干什么的**：UPDATE SQL 明确只修改商户、标题、正文和图片；请求对象里即使混入其他值，也不能覆盖作者、点赞数和评论数。
- **为什么删除图片需要定时重试**：数据库提交后进程仍可能立刻崩溃，导致文件没有删掉。数据库保存 `DELETING` 状态、失败次数和下次重试时间，定时任务以后还能继续删除。
- **为什么正文存纯文本**：数据库不保存可执行 HTML，页面也按普通文字显示；这样正文中的脚本不会运行，反复编辑也不会越来越多地转义。
- **热榜和点赞关系谁是真实数据**：MySQL 点赞关系决定用户到底有没有点赞；热榜只是根据点赞数生成的可重建排名，以后可以用 Redis 快照加速，但不能反过来用缓存决定真实关系。

#### 主要修改文件

- `src/main/java/com/hmdp/dto/BlogCardDTO.java`
- `src/main/java/com/hmdp/dto/BlogDetailDTO.java`
- `src/main/java/com/hmdp/service/blog/BlogCommandService.java`
- `src/main/java/com/hmdp/service/blog/BlogLikeService.java`
- `src/main/java/com/hmdp/service/blog/BlogQueryService.java`
- `src/main/java/com/hmdp/service/blog/BlogIdempotencyService.java`
- `src/main/java/com/hmdp/service/blog/BlogAssembler.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/BlogImageServiceImpl.java`
- `src/main/resources/db/migration/V8__separate_blog_idempotency.sql`
- `src/main/resources/db/migration/V9__add_blog_image_deletion_retry.sql`
- `src/main/resources/db/migration/V10__store_blog_content_as_plain_text.sql`
- `frontend/app/types/api.ts`
- `src/main/resources/nginx-1.18.0/html/hmdp/blog-detail.html`
- `docs/hmdp-项目架构分析.md`

#### 兼容性影响

- 热榜、作者博客和 Feed 的列表元素不再包含 `content/updateTime`；详情接口仍返回这两个字段。客户端类型已拆成 `BlogCard/BlogDetail`。
- `user-blog` 和点赞榜 cursor 版本升级，旧 opaque cursor 会被拒绝，客户端应重新从首屏开始。
- `IBlogService` 不再提供 `save/updateById/removeById` 等通用方法；内部调用方必须使用明确业务用例。
- 部署必须按顺序执行 V8～V10；V8 会迁移并删除 `tb_blog` 的旧幂等列，V10 会把历史正文统一为纯文本。

#### 回归测试

- 验证详情/Card DTO 均不含幂等内部字段，Card 不返回完整正文。
- 验证相同发布请求以前成功过时，服务端在校验商户和图片之前直接返回第一次的博客 ID；未登录返回 401。
- 验证防重复发布记录能区分“当前请求取得创建资格”和“另一个相同请求已经成功”，并拒绝同一请求 ID 携带不同内容。
- 验证编辑的商户校验早于行锁，且只调用字段白名单 UPDATE。
- 验证 `DELETING` 定时任务领取资产、文件不存在视为成功并条件删除元数据。
- 验证重复点赞只由唯一键异常收敛，最终响应回读真实关系与计数。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
mvn test:          BUILD SUCCESS
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
corepack pnpm typecheck: SUCCESS
```

#### 后续事项

- 经用户授权后新增热榜 Redis 版本指针和 List 快照，使游标使用 `snapshotVersion + offset`。
- 大规模对象存储环境把 `DELETING` 定时补偿升级为事务 Outbox + 异步消费者，并增加存储清单对账。

---

### FIX-20260805-02 DTO 用途与字段边界注释补全

- 首次记录：2026-08-05
- 最新更新：2026-08-05
- 状态：已验证
- 模块：DTO / API 契约说明
- 影响接口：无（仅补充代码注释，不改变运行逻辑或 JSON 结构）

#### 问题现象

- `dto` 目录同时包含请求模型、响应模型、认证上下文、Mapper 内部投影和分页协议，但多数类只有字段名或一句笼统说明。
- 阅读者无法直接判断 DTO 由谁创建、被谁消费、字段来自前端还是服务端，以及哪些字段属于安全边界。

#### 根因

1. DTO 按业务迭代逐步增加，缺少统一的注释格式和分类说明。
2. 既有字段注释偏重“字段叫什么”，没有解释“为什么存在、能否信任、客户端应如何使用”。

#### 修复内容

- 为 `dto` 目录全部 13 个 Java 文件补充类级 Javadoc，统一说明类别、调用方向、实际用途和边界。
- 为所有字段补充业务语义，重点标明作者信息和点赞状态由服务端装配，发布/编辑接口不接受前端伪造内部字段。
- 说明图片上传响应中资产 ID 与预览 URL 的不同职责，以及发布/编辑只信任经归属校验的图片资产 ID。
- 说明点赞写接口返回数据库权威状态，前端应覆盖本地状态，不能自行猜测 `+1/-1`。
- 说明普通键集游标、Feed 快照游标和旧 `ScrollResult` 的差异，客户端只能原样回传 opaque cursor。
- 说明 `Result.success` 与 HTTP 状态码职责不同，以及 `UserDTO` 不携带账号、手机号和密码摘要等敏感字段。

#### 修改文件

- `src/main/java/com/hmdp/dto/AuthorInteractionDTO.java`
- `src/main/java/com/hmdp/dto/BlogCardDTO.java`
- `src/main/java/com/hmdp/dto/BlogDetailDTO.java`
- `src/main/java/com/hmdp/dto/BlogImageUploadDTO.java`
- `src/main/java/com/hmdp/dto/BlogLikeStateDTO.java`
- `src/main/java/com/hmdp/dto/BlogPublishRequest.java`
- `src/main/java/com/hmdp/dto/BlogUpdateRequest.java`
- `src/main/java/com/hmdp/dto/CursorPageDTO.java`
- `src/main/java/com/hmdp/dto/CursorPayload.java`
- `src/main/java/com/hmdp/dto/LoginFormDTO.java`
- `src/main/java/com/hmdp/dto/Result.java`
- `src/main/java/com/hmdp/dto/ScrollResult.java`
- `src/main/java/com/hmdp/dto/UserDTO.java`
- `fix.md`

#### 兼容性影响

- 无。未调整字段、序列化结构、接口行为或数据库结构，因此无需同步修改架构文档。

#### 回归测试

- 编译全部 Java 源码，确认新增 Javadoc 的类型引用和标签合法。
- 人工核对 13 个 DTO 均具有类级用途说明和逐字段语义说明。

#### 验证结果

```text
mvn clean compile: BUILD SUCCESS
DTO 注释覆盖检查: 13/13
```

#### 后续事项

- 新增 DTO 时继续沿用“类别 / 用途 / 来源或去向 / 信任边界”的类注释格式。

---

### FIX-20260805-03 核心设计注释白话化与幂等命名重构

- 首次记录：2026-08-05
- 最新更新：2026-08-25
- 状态：已验证
- 模块：全部 Service Impl、博客发布幂等、点赞、查询、Feed、限流、异常响应、游标、架构说明
- 影响接口：无（接口和数据库行为不变，仅优化内部命名、注释和文档）

#### 问题现象

- 代码中的“重放首次响应、生命周期解耦、原子裁决、ownerToken、fail-open、opaque cursor”等术语没有先解释真实场景，读者即使看完要点也不知道为什么需要这些设计。
- 多个类的总结只写实现结论，没有说明“不这样做会发生什么”，无法从注释理解双击、网络超时、并发请求和 Redis 故障下的行为。
- 架构文档的点赞章节仍描述已移除的 `INSERT IGNORE`，与当前普通 INSERT + 仅捕获唯一键异常的实现不一致。
- `service/impl` 中的方法注释覆盖不均：例如创建评论只列出安全与事务“要点”，没有从请求、登录用户、关系校验、写库到返回值的完整执行顺序，也没有具体数据例子；门面的一行委托更看不到下层真实流程。
- `BlogServiceImpl` 虽然已经说明 11 个方法的内部执行流程和数据例子，但没有直接写出由哪个页面动作、HTTP 接口和 Controller 入口触发，读者仍难以判断“这个方法什么时候会被调用”；尤其 `queryMyBlogs()` 当前并未被 Nuxt 页面实际使用，容易把可用接口误认为现有页面调用链。
- 补完调用场景后，每个门面方法仍缺少参数来源、有效取值、隐式登录用户、首次/翻页游标传法，以及成功响应中 `Result.data` 的具体 DTO 和字段说明；仅看到返回类型 `Result` 无法判断前端最终能拿到什么。
- 只用文字列出 `comments、list、author、replies` 等字段时，没有直观标出它们分别是数字、对象还是数组；尤其 `BlogDetailDTO.comments` 容易被误解成内嵌评论 JSON，无法看出详情接口与独立评论分页接口的边界。

#### 根因

1. 注释按熟悉分布式系统术语的读者编写，没有把术语翻译为输入、处理和结果。
2. 幂等判断对象继续使用 `replay/acquired` 等抽象名称，调用处不能直接看出应该创建博客还是返回旧结果。
3. 点赞实现变更后，架构文档对应段落没有同步更新。
4. 早期注释侧重结论和局部代码步骤，没有把每个 Service 接口当作一条完整业务用例说明；继承通用 CRUD 的空实现类也没有解释“空类实际提供了什么、尚未提供什么”。
5. 门面方法注释从 Service 内部视角编写，没有继续向上追踪到 Controller 和前端页面，也没有区分“后端已提供入口”与“当前前端已经接入”。
6. 统一响应外壳把不同业务数据都声明成 `Object data`，如果方法 Javadoc 不继续写明实际 DTO，Java 方法签名本身无法表达 `BlogDetailDTO`、`CursorPageDTO`、博客 ID 或空数据之间的区别。
7. 字段清单只能说明“有哪些名称”，不能呈现 JSON 的嵌套层级和基础类型；同名的博客 `comments` 计数与评论接口 `data.list` 对读者尤其不直观。

#### 修复内容

- 把全部现有“要点/精华”类注释统一改成“这个类解决什么问题 → 程序怎么做 → 不这样做会怎样”的白话结构。
- 用双击发布和响应丢失的完整例子解释防重复发布：相同请求再次到达时不创建新博客，只返回第一次创建的博客 ID。
- 为 `IdempotencyRecord` 的每个字段补充用途，明确它保存的是请求处理流水，不是博客内容；解释 `requestHash` 是内容指纹、`ownerToken` 是并发请求随机号码。
- 为 `IdempotencyRecordMapper` 每条 SQL 说明第一次请求、重复请求、行锁、完成条件和过期清理分别发生什么。
- 内部工厂方法由 `acquired/replay` 改为 `createBlog/returnPreviousResult`，判断方法改为 `shouldUsePreviousResult`，让调用代码直接表达动作。
- 重写异常响应、限流、游标、批量装配、点赞、查询、Feed 曝光和 For You 召回总结，第一次出现专业词时立即给出中文解释和真实后果。
- 更新架构文档中的发布流程和术语；同时把点赞章节从旧 `INSERT IGNORE` 修正为当前普通 INSERT、数据库唯一约束和只捕获 `DuplicateKeyException`。
- 将 FIX-20260805-01 的设计总结改为白话版，避免修复记录继续依赖未解释术语。
- 逐个检查 `service/impl` 下 11 个 `*Impl.java`，为 42 个服务接口实现方法统一补充“入口数据 → 身份/参数校验 → 查询或写入 → 一致性/缓存处理 → 返回结果”的完整流程，并为每个方法给出带具体用户、博客、图片或店铺 ID 的例子。
- 把评论创建从三条“精华要点”展开为完整链路，明确一级评论与回复的参数含义、跨博客/跨评论串校验、作者取自登录上下文，以及评论行和博客评论数为何必须同事务更新。
- 对 `BlogServiceImpl` 的 11 个门面方法继续追踪到 Command、Like、Query、Feed 专用服务，注释描述真实下层流程，不停留在“委托某服务”。
- 为 `BlogServiceImpl` 的 11 个方法逐一增加“调用场景”，明确用户页面动作、HTTP 方法与路径、对应 Controller 方法；同时注明 `queryMyBlogs()` 目前只有 `GET /blog/of/me` 后端入口，当前 Nuxt 自己主页实际复用的是 `queryBlogsByUserId()` 对应的公开主页接口，避免把预留能力误读成已发生的调用。
- 为 11 个方法逐一补齐标准 `@param`、`@return` 和 `@throws`：参数说明覆盖来源、是否必填、取值范围、登录上下文和游标传法；返回说明明确 `Result` 成功字段、`data` 的实际 DTO、DTO 内部字段、游标分页为何不使用外层 `total`，以及删除成功为何 `data=null`；异常说明区分 Service 抛出业务异常与 HTTP 层转换后的失败 `Result`。
- 为 `BlogServiceImpl` 全部 11 个方法增加可直接阅读的请求/成功响应 JSON 示例，明确 `data` 是对象、数组容器、数字还是 `null`。在 `queryBlogById()` 上额外说明 `comments: 8` 只是整数总数，不内嵌评论；同时展示独立评论接口的 `list → comment → author/replies` 嵌套结构，并注明评论 `liked` 只是累计数量，当前没有评论 `isLike` 或评论点赞写接口。
- 为 `SeckillVoucherServiceImpl`、`ShopTypeServiceImpl`、`UserInfoServiceImpl`、`VoucherOrderServiceImpl` 四个无自定义方法的实现类补充继承 CRUD 的真实调用流程和例子，并明确订单空壳不等于秒杀下单已经实现。
- 仅补充解释性 Javadoc，没有修改接口、数据结构、安全边界或核心流程；复核 `docs/hmdp-项目架构分析.md` 后无需改写既有架构内容。

#### 修改文件

- `src/main/java/com/hmdp/entity/IdempotencyRecord.java`
- `src/main/java/com/hmdp/service/blog/IdempotencyDecision.java`
- `src/main/java/com/hmdp/service/blog/BlogIdempotencyService.java`
- `src/main/java/com/hmdp/service/blog/BlogCommandService.java`
- `src/main/java/com/hmdp/mapper/IdempotencyRecordMapper.java`
- `src/main/java/com/hmdp/config/WebExceptionAdvice.java`
- `src/main/java/com/hmdp/interceptor/BlogRateLimitInterceptor.java`
- `src/main/java/com/hmdp/service/cursor/CursorCodec.java`
- `src/main/java/com/hmdp/service/blog/BlogAssembler.java`
- `src/main/java/com/hmdp/service/blog/BlogLikeService.java`
- `src/main/java/com/hmdp/service/blog/BlogQueryService.java`
- `src/main/java/com/hmdp/service/feed/FeedExposureService.java`
- `src/main/java/com/hmdp/service/strategy/recall/impl/blog/ForYouRecall.java`
- `src/main/java/com/hmdp/mapper/BlogLikeMapper.java`
- `src/main/java/com/hmdp/dto/BlogDetailDTO.java`
- `src/main/java/com/hmdp/dto/BlogLikeStateDTO.java`
- `src/main/java/com/hmdp/dto/BlogPublishRequest.java`
- `src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/BlogImageServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/FollowServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/SeckillVoucherServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/ShopServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/ShopTypeServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/UserInfoServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/UserServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java`
- `src/test/java/com/hmdp/service/blog/BlogIdempotencyServiceTest.java`
- `src/test/java/com/hmdp/service/impl/BlogServiceImplPublishTest.java`
- `docs/hmdp-项目架构分析.md`
- `fix.md`

#### 兼容性影响

- 对外 API、JSON 字段、数据库结构和业务行为均不变。
- 仅重命名包内幂等判断方法；生产代码和测试调用已同步更新。
- 2026-08-20 的改动仅新增/扩写注释，不改变可执行 Java 代码，也不新增依赖、数据库迁移或 Redis Key。
- 2026-08-25 的改动仍只补充调用场景、输入输出契约、JSON 结构 Javadoc 和修复记录；对外 API、前端行为、事务和数据读写均未改变。

#### 回归测试

- 验证第一次发布仍取得创建资格，相同成功请求仍返回第一次博客 ID，同一请求 ID 携带不同内容仍返回冲突。
- 运行全部单元测试，覆盖发布、点赞、图片、Feed、关注、用户和 Controller 映射。
- 静态核对 11 个 Impl 文件：42 个服务接口实现方法均同时包含完整流程与具体例子；4 个空实现类说明继承能力和未实现边界。
- 重新执行 Maven clean compile，确认新增 Javadoc 不影响 154 个 Java 源文件编译。
- 生成整站 Javadoc，验证新增的链接、内联代码和文档结构可以被 Javadoc 工具正确解析。
- 2026-08-25 再次执行 Maven clean compile，并检查 11 个门面方法的调用场景与 Controller 映射、当前 Nuxt 请求路径一致。
- 静态检查 11 个门面方法均具有与方法形参一一对应的 `@param`、明确 `Result.data` 类型和字段的 `@return`，并用 Javadoc 构建校验泛型 DTO、链接和内联代码标记。
- 静态检查 11 个门面方法均包含成功响应 JSON；重点核对详情 JSON 的 `comments` 为数字、评论分页 JSON 的 `list/replies` 为数组、`author` 为对象，且示例字段与实际 DTO 一致。

#### 验证结果

```text
mvn clean test: BUILD SUCCESS
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0

2026-08-20 mvn clean compile: BUILD SUCCESS
Compiling 154 source files

2026-08-20 mvn javadoc:javadoc -DskipTests: BUILD SUCCESS

2026-08-25 mvn clean compile: BUILD SUCCESS
Compiling 154 source files

2026-08-25 mvn javadoc:javadoc -DskipTests: BUILD SUCCESS
```

#### 后续事项

- 后续新增设计注释继续优先使用真实场景和最终效果；必须使用专业词时，在第一次出现处直接解释。

---

### FIX-20260806-01 基础功能与故障增强分阶段文档

- 首次记录：2026-08-06
- 最新更新：2026-08-11
- 状态：已验证
- 模块：全项目架构、数据库一致性、Redis/短信/文件与前端网络异常
- 影响接口：无（仅新增分析文档，不改变运行逻辑）

#### 问题现象

- 当前代码把正常业务、数据库事务、并发控制、缓存降级和跨资源补偿同时呈现，第一次学习时不容易看出基础调用链。
- “正常流程”和“数据库失败/并发保护”的讲解边界不清，事务、唯一约束、行锁和字段白名单曾被提前塞进第一轮，导致第一轮仍然看不清 Happy Path。
- 原架构文档按业务域完整描述现状，但缺少一条“先做基础版、再补故障增强”的学习和实现路线。
- 原架构文档没有按统一粒度列出全部对外方法；尤其名称分页、店铺分类等直接写在 Controller 的逻辑容易被只看 Service 的盘点遗漏，也缺少可以直接照着调用的示例。
- 方法说明偏向 `queryById()`、Cache Aside、召回、排序等后端动作，没有先回答“现实中是谁在什么页面做了什么才触发”，容易让读者误以为用户会手工输入数据库 ID 或主动操作缓存。
- 优惠券、秒杀和评论曾只保留空白位置，没有说明当前 `hmdp` 的真实完成度，也没有区分 Plus 已完成的秒杀能力、Plus 同样未完成的博客评论以及前端写死的店铺评价。

#### 根因

1. 项目经过多轮增强后，博客、关注、Feed 和图片链路已经包含生产化思路，而用户、店铺、秒杀等域仍保留基础实现或未完成状态。
2. 数据库内部原子性、并发重复写、Redis 缓存一致性、外部文件副作用和客户端响应丢失属于不同问题，原先没有统一分层。
3. `hmdp-plus` 名称容易让人直接假设所有领域都已完成；实际代码中它重点扩展了秒杀，评论 Controller 和 Service 仍为空，店铺与博客评论页面仍是静态模板。

#### 修复内容

- 新增 `docs/hmdp-分阶段实现与故障处理.md`，先按领域描述正常依赖条件下的基础业务主线。
- 重新定义两轮边界：第一轮明确假设依赖全部成功且没有双击、重试和并发，只讲正常业务顺序；第二轮再讲事务回滚、唯一约束、锁、幂等、影响行数、缓存一致性和补偿。该分层只改变学习顺序，不删除当前代码已有保护。
- 逐包盘点当前数据库保护，包括事务、唯一键、行锁、条件更新、请求幂等和图片状态机。
- 单列当前仍存在的数据库缺口，包括注册半完成、共同关注冷缓存、店铺写结果、临时图片删除和秒杀订单约束。
- 将 Redis 按安全关键依赖、可重建缓存和辅助体验分类，分别定义 fail-closed、回源和 fail-open 策略。
- 盘点短信、Token、文件存储和前端响应丢失的处理现状，给出第二轮改造和故障测试顺序。
- 根据阅读反馈精简第一轮章节，只保留正常功能以及不依赖故障也会出现的必填参数、登录身份、对象归属和资源不存在等业务判断。
- 补齐完整架构文档的店铺域：逐项记录按 ID、按类型、按名称、GEO 附近查询、分类查询以及新增/更新/删除的实际入口、调用链、分页方式、数据源和当前缺口；同时说明名称和分类查询因直接写在 Controller，才在旧版 Service 章节中被遗漏。
- 在完整架构文档新增“全域业务方法说明”：最初统一记录 38 个 Controller 接口方法；评论完成后目录已在 `FIX-20260811-01` 更新为 45 个接口、44 个已实现和 1 个下单空白位。
- 逐项对照 Controller、Service 门面和实际实现类，校正博客查询调用链、Feed 入口、扁平作者字段、图片上传响应字段以及店铺坐标缺省行为，避免示例与代码不一致。
- 根据阅读反馈把示例进一步放回各领域的详细实现章节，而不是只集中在文档开头：用户 11 个接口逐项给出输入和结果；博客按图片/发布/编辑/删除、Feed、点赞和查询分组举例；店铺每个查询分支直接在对应 `3.2.x` 小节解释具体数据如何流转；关注和优惠券同样逐方法举例。
- 全域现状目录继续遵守“不把规划写成已实现”：当时秒杀下单和评论规划位均标记未实现；评论在 `FIX-20260811-01` 完成后已改为真实接口说明，下单仍保持空白。
- 全域方法模板由六项扩展为七项，为 37 个已实现接口逐一增加“现实触发”；例如店铺 `queryById()` 被还原为“点击店铺卡片、打开分享链接或从博客查看关联商户”，ID 由页面数据和路由自动传递，不是用户手工查询。
- 在店铺详情章节单独说明不存在 ID 的现实来源：店铺删除后的旧分享/收藏/历史链接、前端过期数据、爬虫和人为构造 URL；空值缓存保护的是这些异常请求反复穿透 MySQL，不是正常产品流程主动查询不存在店铺。
- 为 `src/main/java/com/hmdp/service` 下全部 54 个 Java 文件增加“现实业务背景 + 实际触发”文件导读。Controller 门面、业务实现、缓存、事件、定时清理、存储、游标、召回、排序和内部数据载体均说明自己在真实用户链路中的位置；秒杀订单继续标注尚无真实触发入口，评论导读已随实现更新。
- 重新拆分分阶段文档的店铺查询：第一轮明确为“点击卡片/关联商户 → 页面自动传 shopId → MySQL 查询 → 存在返回详情、不存在返回 404”；第二轮新增独立的 4.5 章节，集中说明正常值缓存、短 TTL 空值缓存、有界互斥重建、Redis 故障回源、随机 ID 限流以及写入后的详情缓存/GEO 同步。
- 明确附近店铺 GEO 本身直接构成“按距离找店”的产品功能，因此保留在第一轮正常业务；店铺 JSON 缓存只负责加速，放入第二轮。不存在是第一轮业务结果，缓存不存在结果则是第二轮性能防护。
- 重新归类博客、关注、Feed 和优惠券：博客多表事务、点赞/关注联合唯一索引、字段白名单、目标状态幂等、Feed 快照与曝光、新增秒杀券两表事务、库存条件更新和一人一单全部进入第二轮；第一轮只描述无并发且无失败时的顺序。
- 记录创建时秒杀下单与评论均未实现；评论已在 `FIX-20260811-01` 完成基础闭环，秒杀下单继续按需求保留未实现。
- 重写分阶段文档整个第二章：全域规则、用户、店铺、博客、关注、Feed、优惠券、秒杀、博客评论和店铺评价统一使用“第一轮保留 / 第二轮增加”两个正向清单；删除“第一轮不引入什么、而是做什么”式转折说明。
- 逐项对照本地 `Database/hmdp-plus`：确认其秒杀域已包含访问令牌、Lua、Kafka 异步落库、订单轮询、取消恢复、订阅提醒和对账；确认博客评论后端仍为空，店铺评价仍为静态模板。
- 在分阶段文档新增完成度矩阵；矩阵已随 `FIX-20260811-01` 更新为“优惠券展示/创建和博客评论已完成基础闭环，秒杀订单与店铺评价尚未形成真实链路”。
- 为优惠券管理、秒杀下单、博客评论和店铺评价分别定义第一轮业务闭环与第二轮可靠性增强；店铺评价使用独立模型，不复用只有 `blog_id` 的 `tb_blog_comments`。
- 在完整架构文档新增博客评论第一轮接口合同、第二轮数据增强、店铺评价独立章节、Plus 对照表、秒杀第一轮接口合同和第二轮高并发链路。
- 识别评论状态模型问题：DDL 使用 `0/1/2` 三态；该字段已在 `FIX-20260811-01` 从 `Boolean` 修正为 `Integer`。
- 在完整架构文档顶部增加分阶段文档入口，避免两份文档互相替代或产生冲突。
- 补齐修复索引中已存在但此前未列出的 2026-08-05 三条记录。

#### 修改文件

- `docs/hmdp-分阶段实现与故障处理.md`
- `docs/hmdp-项目架构分析.md`
- `src/main/java/com/hmdp/service/**/*.java`（54 个文件，仅补充现实背景和触发注释）
- `fix.md`

#### 兼容性影响

- 无运行时影响。Java 仅增加注释；未修改 SQL、配置、接口结构、数据库行为或前端逻辑。

#### 回归测试

- 检查新文档标题层级、表格分隔符、Obsidian 链接和核心章节均存在。
- 检查店铺域包含全部查询方法、前端调用和验证状态，不再只描述按 ID 缓存与 GEO 两条主流程。
- 从 Controller 注解提取 HTTP 方法、路径和 Java 方法名，与全域方法目录逐项比对，确认没有漏项或多写不存在的接口。
- 检查全部 41 个方法/规划块都具有七个固定字段；37 个已实现块内容、现实触发和示例非空，4 个未实现块除状态外保持空白。
- 检查详细领域章节不再只有抽象流程：用户、博客、店铺、关注和优惠券均包含逐方法具体例子，店铺 `3.2.2`～`3.2.7` 的例子与各自代码分支相邻。
- 检查 41 个方法/规划块全部具有“现实触发”字段；37 个已实现方法非空，4 个未实现位置保持空白。
- 扫描 `service` 目录全部 Java 文件，确认每个文件同时包含“现实业务背景”和“实际触发”，且未把未实现服务描述成已完成。
- 检查分阶段文档的店铺域具备现实触发、MySQL 第一轮基线和第二轮入口，并确认第二轮 4.5 完整覆盖正常缓存、空值缓存、锁、Redis 回源及缓存/GEO 一致性。
- 检查第一轮不再把事务、唯一约束、锁、幂等、字段白名单、影响行数和失败补偿列为保留规则，并确认这些机制在第二轮有对应问题场景。
- 检查第二章 10 个主题均同时具有“第一轮保留”和“第二轮增加”，并确认优惠券、秒杀、博客评论和店铺评价各自有独立清单。
- 对照当前与 Plus 的 Controller、Service、Lua、Kafka 调用和前端页面，验证完成度表没有把空壳、静态模板或仅有按钮的页面判定为已完成。
- 检查架构文档同时保留“当前状态”和“规划合同”：未实现接口不标记为完成，第一轮正常流程与第二轮故障机制不混写。
- 检查完整架构文档已提供新文档入口，Fix 索引能定位新增记录。
- 执行 Maven 编译，确认工作区原有 Java 项目仍可正常编译。

#### 验证结果

```text
Markdown 结构检查：SUCCESS（分阶段文档 570 行、8 个一级主题；架构文档 2348 行；代码围栏均成对）
店铺架构覆盖检查：SUCCESS（7 个实现入口，按 ID/类型/名称/GEO/分类查询及写操作均已记录）
全域接口覆盖检查：SUCCESS（Controller 38 个接口，文档 38 个，缺失 0，多写 0）
统一格式检查：SUCCESS（41 个方法/规划块，37 个已实现七项说明，4 个未实现空白块，格式错误 0）
详细章节示例检查：SUCCESS（37 个已实现接口均在所属领域章节提供具体请求与结果说明；未实现方法保持空白）
现实触发检查：SUCCESS（37 个已实现接口均说明实际用户动作；4 个未实现位置为空）
Service 注释覆盖检查：SUCCESS（54/54 个 Java 文件包含现实业务背景与实际触发）
店铺分阶段检查：SUCCESS（第一轮 MySQL 正确性与 GEO 功能；第二轮缓存、防穿透、故障回源和派生数据一致性已明确拆分）
分轮机制归类检查：SUCCESS（原博客五条第一轮保留规则已全部移除；事务、点赞/关注唯一约束和字段白名单均只在第二轮机制中说明）
两轮清单格式检查：SUCCESS（第二章 10/10 个主题具有成对清单；优惠券、秒杀、博客评论、店铺评价已分别列出）
hmdp-plus 对照检查：SUCCESS（秒杀进阶链路已定位；博客评论空壳和店铺评价静态模板已明确记录）
营销与评论规划检查：SUCCESS（第一轮基础接口合同与第二轮数据库、Redis、MQ、审核和对账机制已分层）
mvn clean compile：BUILD SUCCESS（编译 132 个 Java 源文件）
```

#### 后续事项

- 优惠券管理、博客评论与除订单外的 Nuxt 基础入口已在后续记录中闭环；秒杀订单按当前需求暂不实现，店铺评价因需要新增独立表和接口，可在确认数据模型后接入。
- 第二轮按文档顺序补注册原子性、Redis 故障语义、跨资源补偿和真实 MySQL/Redis 集成测试。

---

### FIX-20260809-01 搜索能力从店铺域独立

- 首次记录：2026-08-09
- 最新更新：2026-08-09
- 状态：已验证
- 模块：搜索、店铺、鉴权、Nuxt 前端、架构文档
- 影响接口：新增 `GET /search/shops`；兼容保留 `GET /shop/of/name`

#### 问题现象

- 店铺名称搜索直接写在 `ShopController.queryShopByName()`，Controller 同时承担店铺详情、管理、分类、GEO 和关键词 SQL。
- 后续若增加博客、用户、中文分词或 Elasticsearch，检索逻辑会继续侵入各业务 Controller，无法形成稳定搜索合同。
- 原接口直接返回 `Shop` Entity；没有 `total/hasMore`，前端只能请求到空页后猜测已经到底。
- 空关键词会移除 `LIKE` 条件并分页查询全部店铺；用户输入 `%`、`_` 时会被 SQL `LIKE` 当成通配符。

#### 根因

1. 把“已知店铺 ID 后查询详情”和“用户输入文本后检索内容”当成同一类店铺查询，没有划分搜索边界。
2. Controller 直接调用 MyBatis-Plus Query Chain，缺少可替换的检索 Service。
3. 搜索响应沿用数据库实体和旧式数组分页，没有为搜索结果建立专用 DTO 与分页协议。

#### 修复内容

- 新增独立 `SearchController`，主入口为 `/search/shops?keyword=&current=`；`ShopController` 删除名称查询和相关 SQL。
- 新增 `ShopSearchService` 检索端口和 `MySqlShopSearchService` 当前实现。未来可以增加博客/用户 Search Service，或替换为搜索引擎适配器，不改变前端主合同。
- 关键词统一去除首尾空白、限制 64 字符、转义 `LIKE` 的 `%/_/反斜杠`；空关键词直接返回空页，结果固定按 `id ASC` 分页。
- 新增 `ShopSearchItemDTO`，不暴露经纬度和数据库时间字段；新增 `PageResultDTO` 返回 `list/current/pageSize/total/hasMore`。
- Nuxt 店铺页迁移到 `/search/shops`，使用服务端 `hasMore` 停止加载；搜索接口加入匿名只读白名单。
- 旧 `/shop/of/name` 由 `SearchController` 作为 `@Deprecated` 兼容入口继续提供原数组结构，共用同一个 Search Service；空关键词不再查询全部店铺。
- Java 注释精炼记录搜索域边界、真相源、DTO 隔离、稳定分页和后续搜索引擎替换原则。
- 架构文档新增独立搜索域，并在分阶段文档中拆开 MySQL 基线与搜索引擎、混合检索、同步和故障增强。

#### 修改文件

- `src/main/java/com/hmdp/controller/SearchController.java`
- `src/main/java/com/hmdp/controller/ShopController.java`
- `src/main/java/com/hmdp/service/search/ShopSearchService.java`
- `src/main/java/com/hmdp/service/search/impl/MySqlShopSearchService.java`
- `src/main/java/com/hmdp/dto/PageResultDTO.java`
- `src/main/java/com/hmdp/dto/ShopSearchItemDTO.java`
- `src/main/java/com/hmdp/service/IShopService.java`
- `src/main/java/com/hmdp/config/AuthMvcConfig.java`
- `src/test/java/com/hmdp/controller/SearchControllerTest.java`
- `src/test/java/com/hmdp/service/search/impl/MySqlShopSearchServiceTest.java`
- `frontend/app/pages/shops/index.vue`
- `frontend/app/types/api.ts`
- `frontend/README.md`
- `docs/hmdp-项目架构分析.md`
- `docs/hmdp-分阶段实现与故障处理.md`
- `fix.md`

#### 兼容性影响

- 新 Nuxt 页面使用 `/search/shops`，响应 `data` 从数组升级为带分页元数据的对象。
- 旧 `/shop/of/name` 路径和数组响应仍保留，静态发布页无需修改解析代码；但空名称由“列出全部店铺”改为返回空数组，用户需要先输入店名关键词。
- 没有新增 Maven/NPM 依赖，没有数据库迁移，也没有新增 Redis Key。

#### 回归测试

- 搜索 Controller：验证新接口返回分页 DTO，旧接口保持数组和 `total`，两个路径均由 `SearchController` 声明。
- MySQL 搜索 Service：验证空关键词不访问数据库、Entity 转 DTO、分页元数据、稳定排序、LIKE 通配符转义、非法页码和超长关键词错误码。
- Nuxt TypeScript：验证店铺列表能同时处理搜索分页对象和分类查询数组。

#### 验证结果

```text
mvn "-Dtest=MySqlShopSearchServiceTest,SearchControllerTest" test
BUILD SUCCESS；Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

mvn clean compile
BUILD SUCCESS；编译 137 个 Java 源文件

mvn test
BUILD SUCCESS；Tests run: 68, Failures: 0, Errors: 0, Skipped: 0

npm run typecheck
SUCCESS（nuxt typecheck，退出码 0）
```

#### 后续事项

- 迁移旧静态博客发布页后删除 `/shop/of/name` 兼容入口。
- 博客、用户与统一搜索后端已在 `FIX-20260809-02` 落地；Nuxt 统一结果页已在 `FIX-20260811-01` 接入，下一步建立查询样本及相关度基线，当前不引入 Elasticsearch 或向量模型。

---

### FIX-20260809-02 统一搜索与三个垂直域第一阶段落地

- 首次记录：2026-08-09
- 最新更新：2026-08-11
- 状态：已验证
- 模块：搜索架构、店铺/笔记/用户垂直搜索、统一搜索 API、Nuxt 结果页、架构文档
- 影响接口：新增 `GET /search`、`GET /search/blogs`、`GET /search/users`；原接口保持兼容

#### 问题现象

- `ShopSearchService` 的名称和职责只能表达店铺检索，不能作为博客、用户等全部搜索类型的父接口。
- 用户看到的“大搜索框”可能同时召回多个业务，但若直接在一个 Service 中堆积店铺、博客、用户 SQL，又会形成新的巨型搜索函数。
- 输入提示和正式搜索的延迟、数量与结果合同不同，不应混用一个方法。

#### 根因

1. 上一轮只完成了店铺垂直搜索与统一合同，笔记、用户仍没有可运行实现，`UnifiedSearchService` 也没有 Spring Bean 和端点。
2. “一个搜索框”容易被误解成把所有 SQL 写进一个万能 Service，或者依赖未实现的 AI 自动路由。
3. 用户搜索若直接复用 `User Entity` 或匹配账号/手机号，会扩大敏感数据读取和暴露面。

#### 修复内容

- 保留并落实 `VerticalSearchService<T>`：`ShopSearchService`、`BlogSearchService`、`UserSearchService` 是三个平级垂直域，不存在博客或用户继承店铺搜索的关系。
- 新增 `MySqlBlogSearchService`：标题和正文共同参与 `LIKE`，按 `create_time DESC, id DESC` 稳定分页；只取卡片列，并通过 `BlogAssembler` 批量补齐作者和点赞状态。
- 新增 `MySqlUserSearchService`：只匹配 `nick_name`，数据库只选择 `id/nick_name/icon`，返回 `UserDTO`，账号、手机号和密码不参与检索或响应。
- 新增 `MySqlSearchSupport`，统一三个 MySQL 实现的关键词标准化、64 字符上限、页码/页大小校验和 LIKE 通配符转义，避免各域规则漂移。
- 新增 `DefaultUnifiedSearchService`：scope 为空时按 `SHOP/BLOG/USER` 召回全部域，明确 scope 时只调用所选域；按域分组返回，不做虚假的自动意图识别或跨域统一打分。
- 新增统一及垂直端点：`GET /search`、`GET /search/blogs`、`GET /search/users`；`GET /search/shops` 和旧 `/shop/of/name` 保持兼容。
- `BlogCardDTO` 与 `UserDTO` 接入 `SearchResultItemDTO`，统一层复用现有安全卡片，不创建包含所有业务字段的万能 DTO。
- `/search` 与 `/search/**` 均明确作为匿名可读接口；认证上下文仍会尝试解析登录用户，以便笔记卡片补齐当前用户点赞状态。
- 继续保留 `SearchSuggestionService` 空合同；输入联想未冒充完成。Nuxt 已新增“综合｜店铺｜笔记｜用户”结果页：综合调用 `/search`，垂直 Tab 调用各自端点独立分页。
- 架构说明补充小红书的明确搜索目标、点评的 POI 多字段搜索边界；公开资料只用于架构映射，不声称获得其内部源码。

#### 修改文件

- `src/main/java/com/hmdp/service/search/SearchScope.java`
- `src/main/java/com/hmdp/service/search/VerticalSearchService.java`
- `src/main/java/com/hmdp/service/search/UnifiedSearchService.java`
- `src/main/java/com/hmdp/service/search/SearchSuggestionService.java`
- `src/main/java/com/hmdp/service/search/ShopSearchService.java`
- `src/main/java/com/hmdp/service/search/BlogSearchService.java`
- `src/main/java/com/hmdp/service/search/UserSearchService.java`
- `src/main/java/com/hmdp/service/search/impl/MySqlSearchSupport.java`
- `src/main/java/com/hmdp/service/search/impl/MySqlBlogSearchService.java`
- `src/main/java/com/hmdp/service/search/impl/MySqlUserSearchService.java`
- `src/main/java/com/hmdp/service/search/impl/DefaultUnifiedSearchService.java`
- `src/main/java/com/hmdp/dto/SearchQuery.java`
- `src/main/java/com/hmdp/dto/SearchResultItemDTO.java`
- `src/main/java/com/hmdp/dto/SearchSectionDTO.java`
- `src/main/java/com/hmdp/dto/UnifiedSearchResultDTO.java`
- `src/main/java/com/hmdp/dto/SearchSuggestionDTO.java`
- `src/main/java/com/hmdp/dto/ShopSearchItemDTO.java`
- `src/main/java/com/hmdp/dto/BlogCardDTO.java`
- `src/main/java/com/hmdp/dto/UserDTO.java`
- `src/main/java/com/hmdp/controller/SearchController.java`
- `src/main/java/com/hmdp/config/AuthMvcConfig.java`
- `src/test/java/com/hmdp/service/search/SearchContractTest.java`
- `src/test/java/com/hmdp/service/search/impl/MySqlSearchSupportTest.java`
- `src/test/java/com/hmdp/service/search/impl/MySqlBlogSearchServiceTest.java`
- `src/test/java/com/hmdp/service/search/impl/MySqlUserSearchServiceTest.java`
- `src/test/java/com/hmdp/service/search/impl/DefaultUnifiedSearchServiceTest.java`
- `src/test/java/com/hmdp/controller/SearchControllerTest.java`
- `docs/hmdp-项目架构分析.md`
- `docs/hmdp-分阶段实现与故障处理.md`
- `fix.md`

#### 兼容性影响

- 现有 `/search/shops`、`/shop/of/name`、请求参数和响应 JSON 均未改变。
- 新增 `/search`、`/search/blogs`、`/search/users`，均为匿名可读查询接口；`scope` 使用 `SHOP/BLOG/USER`，为空表示综合。
- 统一接口默认每域 5 条，垂直笔记/用户接口默认每页 10 条，服务端最大 10 条；非法页码、页大小或超长关键词返回稳定业务错误码。
- Nuxt 新增 `/search` 页面并调用统一及三个垂直接口；原店铺页继续调用 `/search/shops`，旧兼容路径不变。
- 没有新增依赖、数据库迁移或 Redis Key。

#### 回归测试

- 验证三个 Service 均为平级 `VerticalSearchService`，scope 分别为 `SHOP/BLOG/USER`。
- 验证空关键词不访问数据库，统一的页码、页大小、关键词长度与 LIKE 转义规则生效。
- 验证笔记标题/正文查询返回卡片并使用批量装配，用户搜索 DTO 不包含账号、手机号和密码。
- 验证综合模式调用三个域且分组顺序稳定，指定 scope 时只调用对应域，重复 scope 实现会在启动注册阶段失败。
- 验证四个搜索端点映射、旧店铺兼容响应以及完整项目回归测试。

#### 验证结果

```text
mvn "-Dtest=SearchContractTest,MySqlSearchSupportTest,MySqlShopSearchServiceTest,MySqlBlogSearchServiceTest,MySqlUserSearchServiceTest,DefaultUnifiedSearchServiceTest,SearchControllerTest" test
BUILD SUCCESS；Tests run: 19, Failures: 0, Errors: 0, Skipped: 0

mvn clean compile
BUILD SUCCESS；编译 152 个 Java 源文件

mvn test
BUILD SUCCESS；Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
```

#### 后续事项

- 保存真实查询词和期望结果样本，建立零结果率、点击率、延迟与相关度基线；数据和效果证明确有需要后再引入全文或向量检索。
- 输入提示需要单独确定最小触发字符数、防抖、返回数量、热门词与历史词数据来源后再实现。

---

### FIX-20260811-01 除下单外基础业务与 Nuxt 闭环

- 首次记录：2026-08-11
- 最新更新：2026-08-11
- 状态：已验证
- 模块：博客评论、博客详情与图片、Nuxt 用户/店铺/博客/社交/Feed/搜索/管理页面、前后端代理、架构文档
- 影响接口：新增 `POST/GET /blog-comments`、`DELETE /blog-comments/{id}`；`GET /blog/{id}` 增加 `imageIds`；其余均接入已有接口

#### 问题现象

- 后端已有资料编辑、GEO、博客生命周期、关注、Feed、统一搜索和优惠券管理能力，但 Nuxt 只覆盖登录、热门列表、店铺名称/分类和签到，用户无法从当前前端触发大部分基础功能。
- `BlogCommentsController`、`IBlogCommentsService` 和实现原来是空壳，博客详情没有真实评论与回复链路。
- Nuxt 券按钮会调用尚未实现的秒杀订单接口，给用户造成“功能可用但请求必然失败”的假象。
- `useAuth()` 每次调用各自创建局部 `ref`，页面更新登录用户后，布局和其他组件可能继续显示旧状态。
- 博客详情只返回图片 URL 字符串，编辑器无法区分“展示路径”和“当前作者可保留的已绑定图片资产”。
- Spring Boot 默认端口是 `9090`，Nuxt 默认代理却指向 `8081`，未配置环境变量时基础联调会请求错误端口。

#### 根因

1. 后端能力和 Nuxt 页面迁移分别推进，没有按现实用户操作建立端到端完成度清单。
2. 评论只有表和 MyBatis-Plus 基础类，没有定义请求 DTO、响应 DTO、权限、评论树、游标和计数事务。
3. 未完成的订单按钮与已完成的优惠券展示共用页面，没有在 UI 合同上明确隔离。
4. 认证 composable 没有使用 Nuxt 跨组件共享状态。
5. `tb_blog.images` 是兼容展示快照，不足以充当图片资产所有权凭证；编辑需要 `tb_blog_image.id`。
6. 前端 README、环境示例和代理配置没有与 `application.yaml` 的实际端口同步。

#### 修复内容

##### 1. 评论基础闭环

- 新增 `BlogCommentCreateRequest` 与 `BlogCommentDTO`，接口不直接接收或返回评论 Entity。
- 新增创建评论/回复、分页查询和删除三个端点；评论作者只取登录上下文。
- 回复必须同时携带一级评论和被回复评论 ID，并校验二者属于同一博客、同一评论串。
- 一级评论使用 `(create_time DESC,id DESC)` opaque 游标；当前页回复、作者和被回复用户批量读取，避免逐评论 N+1 SQL。
- 创建评论与 `tb_blog.comments + 1` 同事务；删除一级评论时连同回复删除，并按实际删除行数在同一事务扣减计数。
- `BlogComments.status` 从 `Boolean` 修正为 `Integer`，与 DDL 的 `0/1/2` 三态一致。

##### 2. 博客生命周期与互动页面

- 新增可复用博客卡片和编辑器；完成博客发布、编辑、删除、图片上传、临时图片删除、详情、点赞/取消、点赞用户列表、评论和回复页面。
- `BlogDetailDTO` 增加有序 `imageIds`，详情查询从 `tb_blog_image` 读取已绑定资产，作者编辑时只提交希望保留的资产 ID。
- 点赞按钮请求期间禁用；成功后使用服务端最终 `liked/likeCount` 覆盖本地状态，失败时回读详情校准。
- 早期只有 URL、没有图片资产记录的博客不能伪造 asset ID；编辑器明确提示重新上传。

##### 3. 用户、社交、Feed 与搜索页面

- 个人页支持展示和编辑资料、签到、进入自己的博客与管理页。
- 用户主页支持关注/取关、关注列表、共同关注和作者博客游标分页。
- Feed 页面提供 Following/For You 切换、强制刷新与 opaque cursor 连续翻页。
- 搜索页提供综合、店铺、笔记、用户四个 Tab；综合调用统一分组接口，垂直 Tab 使用各自分页接口。
- 热门博客、作者头像和搜索结果补齐详情/用户路由，不再只展示不可进入的卡片。

##### 4. 店铺、GEO、管理与未完成订单隔离

- 店铺列表可请求浏览器定位，把经纬度作为 `x/y` 传给分类查询，触发 Redis GEO 附近店铺并展示距离。
- 新增学习版管理页，接入店铺新增/更新、普通券和秒杀券创建。
- 店铺详情继续展示优惠券，但下单按钮改为明确禁用并显示“下单暂未开放”，不调用 `/voucher-order/**`。
- 下单、订单查询和订单列表没有伪装成完成，也没有在本轮增加交易逻辑。

##### 5. 前端基础设施与文档

- `useAuth()` 改用 `useState('auth_user')`，让布局、菜单和页面共享同一用户状态。
- 桌面与移动导航补齐 Discover、Feed、Search、Shops、Me 和管理入口。
- Nuxt 默认代理、`.env.example` 与 README 统一改为后端实际端口 `9090`。
- README、分阶段说明和完整架构文档同步记录现实触发、接口机制、完成状态、历史图片边界及下单禁用状态。

#### 主要修改文件

- `src/main/java/com/hmdp/controller/BlogCommentsController.java`
- `src/main/java/com/hmdp/service/IBlogCommentsService.java`
- `src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java`
- `src/main/java/com/hmdp/entity/BlogComments.java`
- `src/main/java/com/hmdp/dto/BlogCommentCreateRequest.java`
- `src/main/java/com/hmdp/dto/BlogCommentDTO.java`
- `src/main/java/com/hmdp/dto/BlogDetailDTO.java`
- `src/main/java/com/hmdp/mapper/BlogMapper.java`
- `src/main/java/com/hmdp/service/blog/BlogQueryService.java`
- `src/test/java/com/hmdp/service/impl/BlogCommentsServiceImplTest.java`
- `frontend/app/components/BlogCardItem.vue`
- `frontend/app/components/BlogEditor.vue`
- `frontend/app/composables/useAuth.ts`
- `frontend/app/pages/blogs/**`
- `frontend/app/pages/users/[id].vue`
- `frontend/app/pages/feed.vue`
- `frontend/app/pages/search.vue`
- `frontend/app/pages/shops/**`
- `frontend/app/pages/me.vue`
- `frontend/app/pages/manage.vue`
- `frontend/app/types/api.ts`
- `frontend/app/utils/api-error.ts`
- `frontend/nuxt.config.ts`
- `frontend/.env.example`
- `frontend/README.md`
- `docs/hmdp-项目架构分析.md`
- `docs/hmdp-分阶段实现与故障处理.md`
- `fix.md`

#### 兼容性影响

- 评论增加 3 个新接口，不改变旧接口。
- `GET /blog/{id}` 只新增 `imageIds` 字段，属于向后兼容的响应扩展；原 `images` 仍保留。
- 评论 `status` 的 Java 类型变化只是与既有数据库 `tinyint` 三态对齐，无需新增 Flyway 迁移。
- 没有新增 Maven/NPM 依赖、数据库表、索引或 Redis Key。
- Nuxt 默认代理从错误的 `8081` 修正为 `9090`；显式设置 `NUXT_DEV_PROXY_TARGET` 的环境仍按其配置运行。
- 秒杀下单和订单仍未实现；唯一变化是前端不再错误触发未完成接口。

#### 回归测试

- 评论 Service 覆盖创建一级评论及计数、跨博客回复拒绝、删除一级评论及回复并按实际行数扣减计数。
- Maven 全量测试覆盖已有博客、搜索、关注、上传等回归。
- Nuxt 类型检查覆盖新增页面、DTO 和路由参数；生产构建验证客户端与 Nitro 服务端产物。
- ESLint 只对本次新增和修改的前端文件做零错误检查；项目全量 lint 仍有本次改动前已经存在的历史基线问题，不把它记录成已通过。

#### 验证结果

```text
mvn clean compile
BUILD SUCCESS；编译 154 个 Java 源文件

mvn test
BUILD SUCCESS；Tests run: 83, Failures: 0, Errors: 0, Skipped: 0

npm run typecheck
SUCCESS（nuxt typecheck，退出码 0）

npx eslint --max-warnings=0 <本次新增和修改的前端文件>
SUCCESS（退出码 0，0 error / 0 warning）

npm run build
SUCCESS（Nuxt 4 / Nitro node-server 生产构建完成，退出码 0）
```

构建仍提示 caniuse-lite 数据较旧、Tailwind sourcemap 和部分依赖导出规则弃用警告；这些警告没有阻止产物生成，也不是本次业务改动引入的编译错误。

#### 后续事项

- 当前需求明确不做下单；进入交易轮次后单独设计订单状态机、查询合同、库存条件扣减和一人一单约束。
- 为评论补真实 MySQL 集成测试、匹配查询顺序的复合索引、软删除/审核和频率限制。
- 迁移只有旧图片 URL 的历史博客资产，使这些博客也能无损编辑图片。
- 继续修复共同关注冷缓存正确性，并补完整浏览器 E2E 联调。

---

## 新问题记录模板

只有问题主题与现有记录无关时，才复制以下模板创建新编号。同一问题的继续改进应直接合并进已有记录，并删除被替代的旧描述。

````markdown
### FIX-YYYYMMDD-NN 修复标题

- 首次记录：YYYY-MM-DD
- 最新更新：YYYY-MM-DD
- 状态：处理中 / 已修复 / 已验证 / 已回退
- 模块：
- 影响接口：无 / `METHOD /path`

#### 问题现象

- （填写）

#### 根因

1. （填写）

#### 修复内容

- （填写）

#### 修改文件

- `path/to/file`

#### 兼容性影响

- 无 / 说明接口、数据或配置变化。

#### 回归测试

- （填写）

#### 验证结果

```text
实际执行的命令与结果
```

#### 后续事项

- 无 / （填写）
````
