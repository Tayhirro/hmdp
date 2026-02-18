# Spring 依赖注入注解笔记：`@Autowired` / `@Resource`

面向本项目的常见用法：在 Service、Config、Resolver、Interceptor 等组件里注入 `StringRedisTemplate`、各种 `Service`、`Mapper` 等 Bean。

## `@Autowired`

- **来源**：Spring 框架注解（`org.springframework.beans.factory.annotation.Autowired`）。
- **用途**：把 Spring 容器里的 Bean 注入到字段、构造器、Setter、方法参数上。
- **默认匹配策略**：按 **类型（type）** 解析依赖（比如字段类型是 `StringRedisTemplate`，就找这个类型的 Bean）。
- **多候选处理**：当同类型 Bean 不止一个时，通常需要：
  - 配合 `@Qualifier("beanName")` 指定；或
  - 让其中一个 Bean 标注为 `@Primary` 作为默认。
- **必需性**：默认 `required = true`（找不到会启动失败）；可用 `@Autowired(required = false)` 让注入变为可选。
- **推荐姿势**：更推荐 **构造器注入**（可配合 Lombok `@RequiredArgsConstructor`），便于测试、避免空指针与循环依赖隐患。

## `@Resource`

- **来源**：JSR-250 标准注解（`javax.annotation.Resource`）。
- **用途**：同样用于注入 Bean，常用于字段或 Setter（也可用于方法）。
- **默认匹配策略（Spring 的实现习惯）**：
  - 优先按 **名称（name）** 解析：默认用“字段名/属性名”去找同名 Bean；
  - 找不到再按 **类型（type）** 兜底匹配。
- **显式指定**：可用 `@Resource(name = "beanName")` 指定要注入哪个 Bean。
- **适用场景**：当你更想用“Bean 名字”来表达依赖（例如同类型多实现、但 BeanName 不同），`@Resource` 会更直观。
- **注意点**：当名称与类型都无法唯一匹配时，同样会导致启动失败（需要改名或改成显式指定）。

