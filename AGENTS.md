# HMDP 黑马点评

You are a **Java/Spring Boot backend developer** working on a Redis-based social commerce app. Your priorities:
1. Correctness first — data consistency (Redis + MySQL) over performance
2. Follow existing patterns — strategy pattern, service layering, DTO isolation
3. Keep Feed 流 pull-based — no fanout writes
4. Verify with `mvn clean compile` before declaring done

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 8 |
| Framework | Spring Boot | 2.7.4 |
| ORM | MyBatis-Plus | 3.5.2 |
| DB Migration | Flyway | (spring-boot-starter) |
| Cache | Redis (Lettuce pool) + Caffeine | — |
| Auth | Session / JWT / Redis-token | (configurable) |
| Build | Maven | — |
| Frontend | Nuxt 3 (Vue 3) | `frontend/` |

## Key Commands

### Build & Verify
```bash
mvn clean compile                        # Compile only (fast)
mvn test                                 # Unit tests
mvn verify                               # Unit + integration tests
mvn clean package -DskipTests            # Package without tests
```

### Run
```bash
mvn spring-boot:run                      # Start on port 9090
```

### DB Migration
```bash
# Flyway auto-runs on startup; add migration file:
# src/main/resources/db/migration/V{序号}__description.sql
```

## Architecture

### Module Map
| Package | Responsibility |
|---|---|
| `controller/` | Thin REST endpoints, delegate to service |
| `service/` | Business logic + `strategy/` for ranking |
| `mapper/` | MyBatis-Plus interfaces |
| `entity/` | DB mapping (one per table) |
| `dto/` | API request/response objects |
| `config/` | Spring config, interceptors, exception handler |
| `utils/` | Redis constants, lock client, regex helpers |
| `auth/` | Auth template (Session / JWT / Redis-token) |
| `interceptor/` | Login + auth interceptors |
| `sms/` | SMS sender abstraction (`LogSmsSender` for dev) |

### Critical Flows

**Feed 流（pull 模式）**
```
发博: save(blog) → tb_blog (无 fanout，无 Redis 写)
读 Feed:
  1. followCache.get(userId)           ← Caffeine 5分钟
  2. SELECT ... WHERE user_id IN(...)
       ORDER BY create_time DESC LIMIT 200
  3. rankingStrategy.rank(blogs, ctx)
  4. 填充用户信息 + 点赞标志
  5. 返回 ScrollResult(minTime, lastScore)
```

**认证**
```
配置: hmdp.auth.method=redis-token (默认)
链: 拦截器注册在 AuthMvcConfig → 按 method 选择认证模板
备选: session, jwt, redis-token, auto
```

**秒杀**
```
下单: 校验库存 → Redis 扣减 → 创建订单 → 异步处理 (阻塞队列)
```

## Code Style & Patterns

### ✅ Good Patterns
- Service interface + impl separation: `IBlogService` / `BlogServiceImpl`
- Strategy pattern for ranking: `RankingStrategy` interface → `SimpleRankingStrategy`, `WeightedRankingStrategy`
- DTOs for API payloads, entities for DB mapping (don't expose entities as API responses)
- Use `RedisConstants.java` for all Redis key formats (centralized, not inline string literals)

### ⚠️ Conventions
- Entity classes use Lombok (`@Data`, `@TableName`)
- Controllers return `Result` (generic wrapper: `Result.success(data)` / `Result.fail(msg)`)
- Autowire by constructor (or `@RequiredArgsConstructor`), avoid field injection
- Flyway migration naming: `V{number}__description.sql` (two underscores)
- Logger: use Lombok `@Slf4j`

### ❌ Avoid
- Hand-rolling pagination when MyBatis-Plus `Page` suffices
- Raw `String.format` for Redis keys — use `RedisConstants` instead
- Synchronous blocking in controller threads (use async where possible)
- Exposing `HttpServletRequest` / `HttpSession` in service layer

## Key Files

| What | Path |
|---|---|
| Entry point | `src/main/java/com/hmdp/HmDianPingApplication.java` |
| App config | `src/main/resources/application.yaml` |
| Redis key constants | `src/main/java/com/hmdp/utils/RedisConstants.java` |
| Feed ranking strategy | `src/main/java/com/hmdp/service/strategy/RankingStrategy.java` |
| Feed ranking impls | `src/main/java/com/hmdp/service/strategy/impl/` |
| Auth config | `src/main/java/com/hmdp/config/AuthMvcConfig.java` |
| Auth templates | `src/main/java/com/hmdp/auth/` |
| SMS abstraction | `src/main/java/com/hmdp/sms/` |
| ScrollResult DTO | `src/main/java/com/hmdp/dto/ScrollResult.java` |
| Flyway migrations | `src/main/resources/db/migration/` |

## Common Pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `NOGROUP No such key 'stream.orders'` | Redis Stream group not created | Run `XGROUP CREATE stream.orders g1 $ MKSTREAM` |
| Flyway fails on existing tables | baseline-version mismatch | Check `baseline-version` in `application.yaml` |
| Caffeine cache stale | Follow list not invalidated on unfollow | Call `followCache.invalidate(userId)` |
| Blog liked count out of sync | Redis `BLOG_LIKED_KEY` not synced to MySQL | Sync on read or schedule periodic flush |
| Lombok compile errors | Annotation processing not enabled | IDE: enable annotation processing for Lombok |

## Boundaries

### ✅ Always
- Run `mvn clean compile` after any Java change
- Add Flyway migration for DB schema changes (not raw DDL)
- Use `RedisConstants` for all Redis key construction
- Keep controllers thin — put logic in service layer

### ⚠️ Ask First
- Changing auth provider (Session / JWT / Redis-token)
- Adding new Redis data structures or keys outside existing patterns
- Modifying `ScrollResult` (has subclasses)
- Switching away from pull-based Feed 流
- Adding new Maven dependencies

### 🚫 Never
- Hardcode Redis keys or SQL in controllers
- Remove `tb_feed_inbox` table without migration (even if unused)
- Expose entities directly as API responses — use DTOs

## Verification

```bash
cd Database/hmdp
mvn clean compile
```
