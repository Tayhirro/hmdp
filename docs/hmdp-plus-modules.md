# hmdp-plus 模块分析

hmdp-plus 是黑马点评的模块化重构版。原版是一个单体 Spring Boot 项目（`hmdp`），升级版拆成了多个 Maven 模块，但运行起来仍然是一个应用——**模块化单体**，不是微服务。

## 一句话概括

**把原来一个项目里的代码按功能拆成了独立模块，互相通过 Maven 依赖组合，最后在 core-service 里打包成可运行的应用。**

## 模块全景

```
hmdp-plus (根 POM)
├── hmdp-common                          # 公共基础 — 所有模块都依赖它
├── hmdp-sharding                        # 数据库分片（ShardingSphere）
├── hmdp-core-service                    # 主应用 — 最终启动入口
├── hmdp-id-generator-framework          # 分布式 ID（雪花算法）
├── hmdp-parameter                       # DTO/VO 参数定义
├── hmdp-mq-framework                    # 消息队列（Kafka）
│   ├── hmdp-mq-common-framework
│   ├── hmdp-mq-producer-framework
│   └── hmdp-mq-consumer-framework
├── hmdp-redis-tool-framework            # Redis 缓存 + 限流
│   ├── hmdp-redis-common-framework
│   ├── hmdp-redis-framework
│   └── hmdp-redis-rate-limit-framework
└── hmdp-redisson-framework              # 分布式协调（Redisson）
    ├── hmdp-redisson-service-framework
    │   ├── hmdp-redisson-common-framework
    │   ├── hmdp-service-lock-framework
    │   ├── hmdp-repeat-execute-limit-framework
    │   └── hmdp-bloom-filter-framework
    └── hmdp-service-delay-queue-framework
```

---

## hmdp-common — 公共基础

### 做什么
所有模块共享的东西：异常类、枚举、常量、工具方法。

### 关键内容
```
exception/    HmdpFrameException, BaseException
enums/        OrderStatus, VoucherStatus 等状态枚举
constant/     锁常量、防重复常量
utils/        DateUtils
threadlocal/  BaseParameterHolder（请求上下文）
```

### 解释
每个项目都需要一个存放"哪里都用得上但又没地方放"的模块。这里就是。没有业务逻辑，纯工具。

### 可简化点
- `BaseParameterHolder` 用 ThreadLocal 存请求上下文——如果不用多线程处理请求，其实可以直接从请求参数传。
- 枚举定义了很多状态值，但有些可能只有一两个地方用到，可以考虑合并。

---

## hmdp-core-service — 主应用

### 做什么
最终可启动的 Spring Boot 应用。所有业务代码（Controller、Service、Entity、Mapper）都在这里。

### 入口
```
HmDianPingApplication.java   ← 标准的 @SpringBootApplication
端口: 8085
```

### 关键链路
```
Controller → Service → MyBatis-Plus Mapper / Redis / Kafka / Redisson
```

### 控制器（9 个）
VoucherOrderController、VoucherController、UserController、ShopController、ShopTypeController、BlogController、BlogCommentsController、FollowController、UploadController、TestController

和原版 hmdp 基本一致。

### 和原版 hmdp 的关系
原版 hmdp 是一个单体项目，所有代码在一个模块里。
hmdp-plus 把"通用能力"（缓存、锁、限流、MQ、ID 生成）抽成了独立模块，core-service 只保留业务逻辑。

**可以理解为：原版是把工具塞抽屉里；plus 是把工具摆在工具架上。**

---

## 框架模块（按功能分类）

这些模块不包含业务逻辑，只提供"能力"。

### hmdp-id-generator-framework — 分布式 ID

**做什么：** 生成全局唯一的订单 ID。

**实现：** 雪花算法（Snowflake）
- `SnowflakeIdGenerator` — 核心生成器
- `WorkDataCenterId` — 工作节点 ID 分配
- `IdGeneratorAutoConfig` — 自动配置

**解释：** 秒杀场景下订单 ID 不能自增（容易被猜到总量），也不能重复。雪花算法用"时间戳 + 机器 ID + 序列号"拼成一个 64 位数字，单机就能生成全局唯一 ID。

**可简化点：** 如果只是学习项目不涉及分布式，UUID 就够用。设计模式也比实际需要多了一层接口抽象。

---

### hmdp-mq-framework — 消息队列

**做什么：** 封装 Kafka 的生产者和消费者。

**三个子模块：**
1. **hmdp-mq-common-framework** — `MessageExtend<T>` 通用消息包装体，带 UUID、时间戳、头信息
2. **hmdp-mq-producer-framework** — `AbstractProducerHandler` 抽象生产者，封装发送 + 确认回调
3. **hmdp-mq-consumer-framework** — `AbstractConsumerHandler` 抽象消费者，封装消费 + 幂等处理

**数据流：**
```
业务 → AbstractProducerHandler.send(msg) → Kafka → AbstractConsumerHandler.consume(msg) → 业务处理
```

**解释：** 模板方法模式。生产者只需继承 `AbstractProducerHandler` 指定消息类型；消费者只需继承 `AbstractConsumerHandler` 实现业务逻辑。Kafka 的连接、序列化、重试由框架处理。

**可简化点：**
- 如果只用一种消息场景，这三层抽象可以合并成一个工具类
- `MessageExtend` 的字段不少，实际用的可能只有 payload

---

### hmdp-redis-tool-framework — Redis 缓存 + 限流

**三个子模块：**

#### hmdp-redis-common-framework
Redis 自动配置 + 基础设施。没什么业务逻辑，就是配置。

#### hmdp-redis-framework
**做什么：** 把 Redis 操作封装成缓存接口。
- `RedisCache` 接口 + `RedisCacheImpl` — 带 JSON 序列化、TTL、null 缓存的 CRUD
- `RedisKeyBuild` — 统一的 Redis key 构建器

**可简化点：** 直接用 `StringRedisTemplate` 其实也就多写几行。这层封装的价值在于统一了 key 格式和序列化方式。

#### hmdp-redis-rate-limit-framework
**做什么：** 限流。
- **令牌桶** — `TokenBucketRateLimitOperate`（用 Lua 脚本实现）
- **滑动窗口** — `SlidingRateLimitOperate`
- **秒杀访问令牌** — `SeckillAccessTokenOperate`
- `RateLimitHandler` + `RedisRateLimitHandler`

**解释：** 令牌桶限流的思路是：一个桶里定时放令牌，请求来了拿一个令牌走，令牌拿完了就拒绝。比简单限制"每秒多少次"更平滑。

**可简化点：** 如果只是学习，一个令牌桶就够了，滑动窗口和秒杀令牌可以砍掉。

---

### hmdp-redisson-framework — 分布式协调

#### hmdp-redisson-common-framework
Redisson 公共配置 + Caffeine 本地缓存。

#### hmdp-service-lock-framework
**做什么：** 注解驱动的分布式锁。
- `@ServiceLock` 注解，支持可重入锁、公平锁、读写锁
- `ServiceLockFactory` 根据注解参数选择锁实现

**数据流：**
```
@ServicLock 注解方法 → AOP 拦截 → ServiceLockFactory.getLocker() → Redisson 分布式锁 → 执行业务 → 释放锁
```

**可简化点：**
- 如果不拆模块，一个 RedisLockUtil 工具类就够用。拆成 4 个类 + 工厂 + AOP，对学习来说偏重了。

#### hmdp-repeat-execute-limit-framework
**做什么：** `@RepeatExecuteLimit` 注解，防止重复执行（幂等）。
分布式锁 + 本地锁双重校验。

**解释：** 比如秒杀下单，用户狂点提交，需要保证只创建一笔订单。注解在方法上自动挡掉重复请求。

#### hmdp-bloom-filter-framework
**做什么：** 布隆过滤器，快速判断"某个 ID 可能不存在"。
- `BloomFilterHandler` + `BloomFilterHandlerFactory`

**解释：** 布隆过滤器可以很确定地说"数据不存在"，但说不准"数据存在"（有误判）。用在缓存前，挡掉肯定不存在的查询请求，减少缓存穿透。

#### hmdp-service-delay-queue-framework
**做什么：** 基于 Redisson 的延迟队列。支持分片隔离。
- 秒杀开始前半小时给用户发提醒
- 订单超时未支付自动取消

**解释：** 不是每个订单都需要立即处理，放到延迟队列里，到了时间再处理。

---

## 核心数据流：秒杀下单

整个 hmdp-plus 最复杂的链路。串联了大部分框架模块：

```
用户点击秒杀
     │
     ▼
令牌桶限流（检查是否超过整体流量）
     │
     ▼
布隆过滤器（检查用户 ID / 商品 ID 是否存在）
     │
     ▼
本地缓存 Caffeine（检查秒杀券基本信息）
     │
     ▼
Redis 缓存（检查秒杀券详细信息，如库存、时间）
     │
     ▼
Lua 脚本（原子操作：扣减库存 + 记录用户）
     │
     ▼
Kafka 发送消息（异步创建订单）
     │
     ▼
@RepeatExecuteLimit（幂等：防止重复消费）
     │
     ▼
创建订单 + 写入数据库（分片写入 hmdp_0 / hmdp_1）
     │
     ▼
延迟队列（超时未支付 → 自动取消）
```

每一层都在"过滤"——把不合法的请求提前挡掉，只有最后真正有效的请求才落地到数据库。

---

## 和原版 hmdp 的关键区别

| 维度 | 原版 hmdp | hmdp-plus |
|---|---|---|
| Java 版本 | 8 | 17 |
| Spring Boot | 2.7.4 | 3.5.4 |
| 模块 | 单模块 | 20+ 模块 |
| 数据库 | 单库 | 2 分片（ShardingSphere） |
| 消息队列 | 无（Redis 阻塞队列） | Kafka |
| 限流 | 无 | 令牌桶 + 滑动窗口 |
| 分布式锁 | 自己写的 RedisLockClient | Redisson（多种锁类型） |
| 幂等 | 无 | @RepeatExecuteLimit |
| 缓存穿透防护 | 无 | 布隆过滤器 |
| 监控 | 无 | Prometheus + Micrometer |
| 前端 | 静态页面 + Nuxt | Vue 3 + Element UI |

---

## 整体评价

### 做得好的
- 功能拆分清晰：缓存、锁、限流、MQ 都独立成模块，可以独立测试
- 秒杀链路的层层过滤是经典的高并发设计
- 用了真实工业组件（Kafka、ShardingSphere、Redisson），贴近生产

### 按 skill 原则看，可简化的地方
- **模块拆得太细**：20+ 模块对于一个学习项目来说太重了。很多模块（如 bloom-filter、delay-queue）只有一个接口 + 一个实现，没必要单独开模块
- **接口过度设计**：`RedisCache` 接口 + `RedisCacheImpl`，但只有一个实现类。可以不要接口
- **注解式 AOP 过多**：@ServiceLock、@RepeatExecuteLimit 用起来方便，但理解成本高（看代码找不到调用链路，得去查切面）
- **如果只是学习高并发设计**：核心链路（Lua 扣库存 + 异步下单）才是重点，分片、布隆过滤器、延迟队列可以等需要时再加
