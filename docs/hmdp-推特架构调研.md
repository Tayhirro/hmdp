# X（Twitter）全功能与系统架构调研

> 调研日期：2026-09-05（X 产品仍在快速变化，本文以当日公开资料为准）
> 置信度标注：
> - 🟢 = 官方开源仓库 / 工程论文 / 官方博客与演讲
> - 🟡 = 可信媒体 / 知名技术博客 / 对官网行为的多源观察
> - ❓ = 内部命名或存疑事项（标注为什么存疑）
>
> 调查范围：X 平台**全部产品功能**（Part 1）+ **系统架构全景**（Part 2）+ 三大内容机制（Part 3，时间线/搜索/趋势）+ 与 hmdp 的对照（Part 4/5）。

---

## Part 1. 产品功能全景（截至 2026-09）

### 1.1 内容发布与编辑
| 功能 | 说明 | 置信 |
|---|---|---|
| 短推文（Tweets） | 公开展示，默认公开，可设为仅关注者可见 | 🟢 维基 |
| 长文本 | X 化后支持长文（Premium 更长） | 🟢 维基 |
| 回复/转发/引用 | 回复树、转发、引用转发（Quote） | 🟢 |
| 线程（Threads） | 同一用户多推串联 | 🟢 |
| 媒体 | 图片相册、视频（上传/直播/图库剪辑）、GIF、贴纸 | 🟢 |
| 投票（Polls, 已回归） | 选项投票 | 🟡 |
| 编辑帖子 | Premium 专属，发布后有编辑期 | 🟢 help.x.com |
| 发送前撤销（Undo Tweet） | 5 秒延迟窗口可撤回 | 🟢 维基列表 |
| 定时/草稿/自动保存 | 预约发布、草稿箱 | 🟡 |
| 置顶/固定 | 个人主页置顶推文/列表 | 🟢 |
| 回复权限 | 全员/关注者/受邀者 | 🟢 |
| 媒体标注意见 | 敏感媒体标记 | 🟢 |
| Spaces（音频/视频房间） | 实时语音/视频广播，可回放 | 🟢 |
| 直播（Live） | 视频直播 | 🟢 |

### 1.2 互动
点赞、书签（收藏）、转发/引用、回复、@提及、话题标签（#）、话题页标签（Topics）、收藏夹个人化、互动通知（关注/转发/点赞/提及/回复/新粉丝）、通知筛选（质量筛选、已验证用户筛选项）。

### 1.3 社交关系
关注/取关/解除粉丝（软块）、粉丝数与其他计数、**列表 Lists**（时间线集合，可共享与订阅）、**社区 Communities**（群组帖子，取代 Circles 方向）、互关推荐（推荐关注，个性化模板：同行/背景相似/关注者相似）、关注请求（私密账号）、屏蔽/静音/举报/审核（安全中心）、Adblock 型内容控制（「不感兴趣」）、DID/AT 保护（已加锁）。

### 1.4 发现与探索（重点：本项目对照对象）
| 功能 | 说明 | 置信 |
|---|---|---|
| 搜索 | 五个垂直 Tab：热门/最新/用户/媒体/列表；筛选器：用户来源/位置/高级搜索 | 🟢 2026-09-05 实测 |
| 趋势 Trending | Explore「当前趋势」Tab：排名 #1~#10、分类（美国趋势/生活风格/游戏）、相关词、帖子量、约 5 分钟快照 | 🟢 实测+API |
| For You / 个性化时间线 | 移动端默认：召回→排序→过滤→混合 | 🟢 官方仓库 |
| Latest / 关注时间线 | 按时间倒序（严格稳定） | 🟢 |
| 热门视频 | Explore 专属 Tab（内容域抽样） | 🟡 |
| 更多推荐 | 推荐话题、推荐关注卡、X 上的直播（Spaces 聚合） | 🟡 |

### 1.5 消息（DM）
私聊/群聊、表情回复、贴纸、语音/视频通话（2024 起全面开放）、端到端加密消息（2024 年推出，渐进覆盖）、消息搜索、长推文分享消息、企业支持消息（Business 消息入口，开放功能有限）。

### 1.6 个人资料与身份
基本信息（头像、横幅、简介、链接、位置、出生日期）、个性化（背景色/首页主题 Premium）、精选帖子置顶塔、认证体系（蓝色订阅标、金色企业标、灰色机构标、黄色政府标；验证机构管理）、隐私与安全（2FA、登录设备管理、会话动态、数据导出、账号保护/锁定）、社交画像（粉丝数、关注数、互动数）。

### 1.7 商业化与创作者经济（2026 重点变化）
| 功能 | 说明 | 置信 |
|---|---|---|
| X Premium（Basic/Plus/Business） | 蓝标、更长帖子、编辑、去广告（部分）、Grok 接入、高级分析 | 🟢 help.x.com |
| 广告与推广 | 广告主侧：Ads Manager、目标投放；平台侧：时间线广告、推荐卡、趋势推广 | 🟢 |
| 创作者收益 | **Original Content Rewards**（2026-09-07 起取代旧 Creator Revenue Sharing，要求 500+ 验证粉丝，**由 X Money 代付**） | 🟢 TechCrunch/Mashable |
| Creator Subscriptions | 付费订阅（2026-03 改版：付费线程、分享卡片、营销工具） | 🟢 TechCrunch |
| Tips / 商品橱窗 | 打赏（Tip Jar）、Shop 集成（时间变动大，以当前官网页为准） | 🟡 |
| X Money | 钱包/储蓄/转账/银行借记卡（Visa）、3% 返现、免费 ATM；2026 年向 Premium 用户开放并承接创作者代付 | 🟢 多家媒体 |

### 1.8 AI 与自动化
**Grok**（xAI 聊天机器人深度集成：时间线内联回答、搜索增强、图片生成、实时数据流），AI 自动摘要/相关推文聚合（探索页试验），蓝标用户 Grok 优先权。

### 1.9 平台与开放能力
GraphQL 客户端 API（twitter.com/web app 内部）、官方 REST v2 API（用户/推文/搜索/流式接口）、Streaming 推送、嵌入（Tweet Embeds，卡片化）、OAuth（第三方登录/读写授权）、企业级 API（Enterprise/Full Archive 指数化历史搜索）、广告 API、媒体工作室（数据分析）、Twitter 分析（互动统计、曝光/点击）。

---

## Part 2. 系统架构全景

### 2.1 一代架构史观：2012「Timelines at Scale」到 2023「the-algorithm」

| 世代 | 核心思路 | 公开来源 |
|---|---|---|
| 2012 | **写扩散（fan-out）为主**：发推时把推文 ID 扇出到所有粉丝的 Redis 时间线；读路径直读缓存，10ms 级 | 🟢 InfoQ 演讲「Real-Time Delivery Architecture」、Slides「Timelines at Scale」 |
| 2023 | **混合模式**：大 V（粉丝数超阈值，如 5 万+）不再扇出，改为读时拉取；候选从"预构建时间线"变为"候选池+重排" | 🟢 the-algorithm |

### 2.2 读写路径（当前，2023 公开版本）

```text
写入（发推）：
  客户端 → GraphQL → Tweetypie（推文读写核心服务）
     → 存储：Manhattan（分布式 KV，推文主存）/ Twemcache（Redis）
     → 媒体：Blobstore + Pelican（图片缩放）
     → 事件流：EventBus / Scribe → Earlybird 实时索引（10s 可搜）
                              → 图/特征管线（GraphJet 图、RealGraph、Hadoop 训练）

读取（Home Timeline）：
  客户端 → GraphQL → home-mixer（时间线装配主服务，基于 product-mixer）
    → Cr-mixer（出网候选协调）＋ Earlybird（入网候选，约 50%）
    → 候选池（~1,500）→ 特征水合 → Light Ranker → Heavy Ranker（~6,000 特征）
    → Visibility Filters（合规/质量/信任/收益）→ Mixer 混入广告/推荐卡 → 客户端
```

### 2.3 服务清单（公开可考部分）

| 服务 | 职责 | 数据/技术 | 置信 |
|---|---|---|---|
| Snowflake | 全局唯一 ID | 自研时钟毫秒 ID | 🟢 官方博客 |
| Tweetypie | 推文读写核心（客户端经 GraphQL 调用），聚合推文数据返回；回源 Manhattan / Twemcache | 存储层：Manhattan（自研分布式 KV）+ Twemcache（Redis） | 🟢 开源 README |
| Blobstore / Pelican | 媒体原始存储 / 图片缩放（3,000 张/秒量级） | 对象存储 + 图像流水线 | 🟢 官方博客/高扩展性 |
| Strato | 列式分布式存储（用户画像等宽表数据） | 自研列式存储 | 🟡（公开演讲与白板图提及） |
| FlockDB → SocialGraph | 关注关系图（follower/following 图） | 图数据库/单写多读 | 🟢 官方博客（2010） |
| GraphJet | 内存实时二部图（用户↔推文），时间衰减边 | 内存图，实时增边 | 🟢 VLDB 论文 |
| Earlybird | 实时检索索引（Lucene 定制）：搜索 + For You 入网候选源 | 倒排索引，10s 可搜 | 🟢 ICDE 论文 |
| Blender | 搜索服务协调（统一入口/查询分派） | 搜索聚合层 | 🟢 论文 |
| Timeline（2012）/ home-mixer（2023） | 时间线装配 | Redis 时间线缓存 → 候选池+重排 | 🟢 演讲/开源仓库 |
| heavy-ranker / light-ranker | 两段排序 | 神经网络 6k 特征 / LR 粗筛 | 🟢 开源仓库 |
| visibility-filters | 合规与质量过滤 | 规则+处理管线 | 🟢 开源仓库 |
| RealGraph | 关注/互动图离线表征 | Hadoop/Pig | 🟢 GraphJet 论文 |
| SimClusters / TwHIN | 兴趣社区聚类 / 实体嵌入 | 离线训练（128 社区基准） | 🟢 开源文档/论文 |
| FRS | 推荐你可能关注的人，出网候选 | 排序模型 | 🟢 开源文档 |
| 消息（DM）服务 | 私聊/群聊/通话 | 内部命名未公开 | ❓ |
| 推送通知 | APNs/FCM 推送 | 内部命名未公开 | ❓ |
| 广告系统 | 竞价/创意/投放目标 | 内部命名未公开（曾有 Mediabler 等旧名） | ❓ |
| Grok | xAI 模型服务（时间线内联） | 外部 xAI 侧 | 🟡 |

### 2.4 关键数值（出处标注）

- 写路径 300k tweets/s（2012 演讲）；读路径 10ms 级（缓存命中，2012 演讲）；
- 推文 10s 内可检索、搜索 50ms 平均（Earlybird 论文）；
- For You 候选池 ~1,500 条/请求，LightRanker 粗筛后重排（Sumit Kumar 博文）；
- 趋势快照约 5 分钟刷新（API 行为 + 实测）；
- 趋势点击→搜索 URL 带 `src=trend_click`（2026-09-05 实测）。

---

## Part 3. 三大内容机制（摘要）

完整版见旧文（本文合并前内容）与 `hmdp-项目架构分析.md` 4.4/2.2：

1. **个性化时间线（For You/Home）**：候选池→轻排→重排→过滤→混合；候选源含搜索索引（50% 入网）。**实时性 = 每次请求重排；稳定性 = 无**（设计如此）。
2. **Latest/关注时间线**：按时间倒序，纯游标，**严格稳定**（对应本项目作者列表/评论列表的 keyset 方案）。
3. **趋势（Trends/Explore）**：流式聚合（Storm/Heron 类，词频+速度+加速度，5 分钟窗口）→ **周期快照** → 点击即搜索（`src=trend_click`）。**快照期内不重不漏；换批仅边界重复；周边个性化组件实时**。

---

## Part 4. 与 hmdp 的功能/架构对照

### 4.1 功能覆盖对照

| X 功能 | hmdp | 备注 |
|---|---|---|
| 短博文发布/编辑/删除 | ✅ 博客发布/编辑/删除（幂等+图片资产） | |
| 点赞/取消 | ✅ | |
| 评论 | ✅ 一级+回复树 | |
| 转发/引用 | ❌ | 参考后可加「转发/引用」计数与列表 |
| 书签/收藏 | ❌ | 低成本可借鉴（表 + 游标点赞键集模式已有） |
| 关注/粉丝/共同关注 | ✅ | 关注缓存与 Feed 失效事件驱动 |
| 推荐关注 | 🧱 FollowCache + 推荐区（无完整推荐算法） | |
| 搜索（关键词+垂直域） | ✅ 店铺/博客/用户 + 统一入口 | 对标 X 搜索五 Tab 思路 |
| 热榜/趋势 | 🧱 现状 liked 排序（待 V2 快照化） | 对标 Trends（5 分钟快照） |
| 个性化 Feed | ✅ For You/Following（召回+排序+曝光） | 对标 For You（候选池 200 为步调） |
| DM/通知/社区/直播/订阅/支付 | ❌ 全部未实现 | 超出当前范围，暂缺 |
| 认证/订阅 | ❌ | 不适用当前场景 |
| 数据分析 | ❌ | 后续可选 |
| 开放 API | 🧱 仅内部 REST 接口 | 若未来开放，可参考 v2 API 合同思路 |

### 4.2 架构对照（服务级）

| X | hmdp | 差距/建议 |
|---|---|---|
| Tweetypie（推文核心） | BlogServiceImpl + BlogCommandService | 结构相近；严格读写分离已具备 |
| Blobstore/Pelican | BlogImageStorage + 封面/绑定/清理 | 媒体生命周期已具备「状态机+清理」，比 X 的更简单但方向一致 |
| Manhattan + Twemcache | MySQL + Redis 缓存 | 规模不同，原则一致：MySQL 真相 + Redis 缓存 + 空值/互斥 |
| GraphJet/RealGraph | FollowCache（Caffeine）+ tb_follow | 图召回在数据量小后用 DB 即可 |
| Earlybird（搜索+候选） | MySql 搜索适配层 | V3 升级可重建索引后承担榜单聚合 |
| home-mixer（装配） | BlogFeedService（快照+回源） | 方向一致：装配与候选分离 |
| visibility-filters | FeedExposureService（曝光过滤） | 同级能力已具备 |
| Trends（快照+点击即搜索） | `/blog/hot` | **V2 版本化快照→V3 索引聚会（见 Part 5）** |

---

## Part 5. 对 hmdp 的启示（与 4.4 规划一致）

1. **热榜 V2：Redis 版本化 ID List 快照**（游标 = snapshotVersion + offset，Redis 只存可重建排名，MySQL 保真相）——彻底解决漏/重，与 Feed 快照复用同一套「版本化 + 回源降级」模式；
2. **热榜 V3：搜索索引聚合趋势化**（对标 Trends/Earlybird）——与 `/search` 共享检索基础设施，可扩展趋势词/趋势话题；
3. **候选池收缩与窗口限制**是平台级通用做法，hmdp 已具备（召回 200 + 窗口条件），保持；
4. **榜单与个性化分发的边界**：全局榜 = 快照+全员共享；个性化流 = 逐用户候选池+曝光过滤。两类通道不要混用一个缓存策略。

---

## Part 6. 来源清单

**产品功能**：Wikipedia《List of features on X》《X (social network)》、X Help Center（X Premium/Creator Subscriptions）、TechCrunch（2026-03 Creator 订阅改版）、Mashable/Economic Times（2026-09 Original Content Rewards 变更）、X Money 相关多来源报道（2026 年中至 9 月）。
**架构**：the-algorithm 仓库及 README（home-mixer/cr-mixer/light-heavy-ranker/visibility-filters/tweetypie）、twitter-the-algorithm.mintlify.app（introduction/candidate-generation）、Earlybird（ICDE 2012）、GraphJet（VLDB 2016）、Related Query Suggestion（arXiv 1210.7350）、InfoQ「Real-Time Delivery Architecture」（2012）、Slides「Timelines at Scale」（2012，fanout 与混合模式）、trekhleb.dev（2024，Home Timeline API 逆向）、Trends API 行为调研（2009 StackOverflow + twitterapi.io）、Team Blogs（Blobstore/Pelican/Snowflake/FlockDB）。
**实测**：2026-09-05 登录态 x.com——Explore 五 Tab、趋势排名/分类/帖子量、点击趋势 `src=trend_click`、4~5 分钟刷新对比实验（详见 `hmdp-项目架构分析.md` §4.4）。
