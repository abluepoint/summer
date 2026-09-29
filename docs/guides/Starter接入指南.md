# Starter 接入指南

本指南描述当前可用的框架接入能力。授权服务支持 `summer-ui` public client 的 Authorization Code + PKCE（S256）链路，同时保留应用显式配置的 confidential client。

按 [ADR 0021](../adr/0021-manage-request-body-caching-automatically.md)，MVC 自动管理请求体缓存；无需配置 `summer.mvc.cache-request-body`。JSON Field/FieldList、DTO 和 HTTP 日志消费者共享同一份可重复读取的请求体。

[ADR 0022](../adr/0022-define-styles-without-builtin-names.md) 已确认 Style 全部由应用公共配置定义，框架提供链式规则构建能力，不预注册具名 Style；下文内置名称与覆盖方式同样只描述当前实现，迁移目标以该 ADR 为准。

## 组合

| 目标 | 依赖 |
| --- | --- |
| 统一错误、消息与响应契约 | `summer-common-starter` |
| MVC 请求链、参数契约与统一响应 | `summer-mvc-starter` |
| JPA、SQL template 与结果投影 | `summer-data-jpa-starter` |
| OAuth2/OIDC | `summer-security-starter` |
| 完整平台骨架 | `summer-boot-starter` |

所有模块版本由 `com.abluepoint.summer:summer-dependencies` 统一约束。业务构建只声明 Summer BOM 和需要的 starter，不重复声明 Boot、Cloud、Modulith 或 Summer 子模块版本。

## 开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `summer.common.enabled` | `true` | 消息解析与公共基座。 |
| `summer.mvc.enabled` | `true` | MVC 自动配置总开关。 |
| `summer.mvc.request-logging.enabled` | `true` | 输出 requestId、方法、路径、状态和耗时的结构化请求摘要；关闭后不注册请求日志过滤器。 |
| `summer.mvc.request-logging.body.enabled` | `false` | 公共开启请求体日志预览；默认关闭，业务读取仍使用完整请求体。 |
| `summer.mvc.request-logging.body.max-preview-length` | `2048` | 日志预览最大字符数，包含截断标记；不限制业务请求体大小。 |
| `summer.mvc.request-logging.body.redacted-fields` | 内置敏感字段集合 | JSON 快照中替换为 `[REDACTED]` 的字段名；不允许为空。 |
| `summer.mvc.wrap-responses` | `true` | Controller 返回值统一包装；OAuth2/Actuator 自动跳过。 |
| `summer.mvc.field-validation.enabled` | `true` | 开启 PARAM、JSON、PATH 来源的 Field/Style 校验与类型化导出。 |
| `summer.mvc.field-validation.max-list-size` | `100` | `FieldList` 全局最大元素数，必须为正整数。 |
| `summer.data.jpa.enabled` | `true` | SQL 模板源、渲染器、JpaSqlTemplate 与内部结果投影适配器。 |
| `summer.security.enabled` | `true` | 安全自动配置总开关。 |
| `summer.security.authorization-server.enabled` | `false` | 授权服务过滤链与令牌签发。 |
| `summer.security.resource-server.enabled` | `false` | JWT 资源保护链。 |

## 最小数据查询

1. 在资源文件中用 `-- findByEmail` 声明模板段。
2. 注入 `JpaSqlTemplate`。
3. 提供模板参数、JPA 命名参数和目标结果类型。

目标结果类型只支持单列标量、`Map<String,Object>`、Record/构造器 DTO、Spring Data 接口投影和原始 `Tuple`。列别名会显式规范化为 camelCase；setter Bean 与依赖列顺序的 `List<Object>` 不再支持。

## Field/Style 参数校验

`@Field` 不配置输入来源。MVC 根据同一请求中实际出现的位置自动推断查询/表单参数、JSON 标量和 URI template variable；同一个字段同时出现在多个来源时返回 HTTP 400。通过 Style 校验的值会转换为 Controller 可显式读取的类型化结果。字段默认必填且不接受 null，可选字段显式写 `required = false`，允许 JSON null 时再写 `nullable = true`：

```java
@GetMapping("/orders")
@Field(
        param = "page",
        style = "customerPage"
)
OrderView query(FieldValues fields) {
    Integer page = fields.get("page", Integer.class);
    // 查询并返回结果
}
```

JSON Parameter contract 可以和正常的 `@RequestBody` 反序列化、Jakarta Bean Validation 同时使用。Field/Style 负责传输字段白名单、可复用 Style、类型转换和聚合 Field violation；Bean Validation 继续负责 DTO 字段约束与跨字段对象约束。下例为应用自定义 Order 契约，DTO 和 orderCodeStyle 由应用提供；它演示 nullable 能力，不是 Customer 的目标契约：

```java
@PatchMapping("/orders/{orderCode}")
@FieldContract
@Field(param = "orderCode", style = "orderCodeStyle")
@Field(param = "note", required = false, nullable = true, style = "text")
@Field(param = "active", required = false, style = "booleanValue")
OrderView update(
        FieldValues fields,
        @Valid @RequestBody UpdateOrderRequest request
) {
    boolean noteWasPresent = fields.contains("note");
    String note = fields.get("note", String.class);
    // omitted: contains=false；显式 null: contains=true 且 get(...) == null
}
```

`required` 只约束字段是否存在，`nullable` 只约束已存在的 JSON 值能否为 null。空字符串和全空白字符串都算已传值，继续交给 Style 校验；不能用空白值绕过格式规则。JSON 字符串、数字和布尔值按 Style 导出类型保持原生类型边界，例如字符串 `"42"` 不会作为 integer Style 的 JSON number；对象和数组不能作为标量 `@Field`。使用 Field 契约的接口始终拒绝未声明的查询/表单、JSON 和路径字段，不提供宽松开关。MVC 会在请求链开始时缓存可重复读取的完整正文，Field 校验、Jackson DTO 绑定和 HTTP 日志消费者可以按各自职责读取；malformed JSON 统一返回 HTTP 400。

请求体缓存容量与业务请求体限制属于两个边界。MVC 不提供手动缓存开关，也不为了日志预览截断业务正文；缓存保存业务请求实际接收的完整正文。业务允许的请求体大小由应用或 Servlet 容器的请求限制负责，日志正文的脱敏和预览长度由 HTTP 日志快照配置负责，二者不会改变 `@RequestBody` 或 Field 读取到的数据。

框架不预注册具名 Style，也不为 `@Field.style` 提供空名称回退。应用在公共 Java 配置中以 `@Bean` 定义需要的 Style，Bean 方法名就是字段引用名；同一个 Bean 可以被多个字段复用。基础类型和常用规则通过 `Styles` 链式构建器表达：

```java
@Configuration(proxyBeanMethods = false)
class ParameterStyles {

    @Bean
    Style customerEmail() {
        return Styles.string()
                .email()
                .maxLength(200)
                .build();
    }

    @Bean
    Style customerPage() {
        return Styles.integer().range(0, 999_999_999).build();
    }
}
```

字段必须显式引用应用定义的 Style，例如 `style = "customerEmail"`。引用不存在的 Style 会在应用启动阶段失败，诊断信息包含处理器、字段和 Style 名称；不会等到请求到达后才发现配置错误。

FieldList 使用 Field 子项，来源由实际请求自动推断。Query/form 通过连续索引提交，不再提交 countParam；JSON 只接受一层对象数组，子字段路径相对于每个元素：

```java
@PostMapping("/orders")
@FieldList(
        param = "items",
        maxSize = 20,
        items = {
                @Field(param = "sku", style = "text"),
                @Field(param = "details.quantity", style = "integerValue"),
                @Field(param = "note", required = false, nullable = true, style = "text")
        }
)
OrderView create(FieldValues fields) {
    List<Map<String, Object>> items = fields.get("items", List.class);
    // 创建并返回结果
}
```

Query/form 示例：

```text
items[0].sku=A-1
items[0].details.quantity=2
items[1].sku=B-2
items[1].details.quantity=5
```

集合默认 `required=true`、`nullable=false`、`minSize=1`，因此缺失、显式 null 和空数组分别按各自规则处理，`[]` 始终返回 HTTP 400。`maxSize` 未声明时使用公共默认 `summer.mvc.field-validation.max-list-size`（默认 100），显式值直接覆盖公共默认，局部值可以大于公共默认。启动时校验 `1 <= minSize <= 有效 maxSize`，非法范围、空子项、0 或负 maxSize 会失败关闭。

Query/form 索引必须从 0 开始连续；索引缺口、从 1 开始、未知子字段以及 PATH 列表均返回 HTTP 400。只支持对象数组和对象内的标量字段，不支持 primitive array、nested array 或递归 FieldList。
所有字段错误会一次聚合为 HTTP 400，错误码为 `SUMMER_INVALID_ARGUMENT`，并通过 `violations` 返回字段路径和原因；校验失败时不会导出部分结果。未知 Style、空 `FieldList.items` 等属于应用配置错误，会失败关闭。

Bean Validation 与 Field/Style 是互补契约，不互相替代。旧版 JSON Schema、全局 Context 和静态应用上下文链路不在本次兼容范围内。

应用 JSON 端点默认返回 `success/data/error/requestId/timestamp`。当前 MVC 显式旁路 `/oauth2/`、`/.well-known/`、`/actuator/`，并跳过文件、字节流与 Range converter；应用端点可使用 `@RawResponse` 显式保留原始响应。OIDC logout 和新增 `/csrf` 的完整验证见 [现状与目标差异](../architecture/现状与目标差异.md)。

## 请求日志

请求日志由 HTTP 适配层在请求完成后生成不可变摘要，再交给 `RequestLogSink` 输出。默认输出包含 `requestId`、请求方法、路径、响应状态和耗时（毫秒），请求正文默认不记录。应用可通过公共配置关闭整条摘要日志链：

```yaml
summer:
  mvc:
    request-logging:
      enabled: false
```

如需为所有请求消费者开启正文预览，使用同一组公共配置。MVC 只从自动缓存的请求包装器生成日志副本；日志 sink 不读取 Servlet 原始流。JSON 对象和数组中的敏感字段默认替换为 `[REDACTED]`，预览超过上限时截断。正文日志的配置不会截断或修改 Controller、DTO、Field 读取到的完整请求体：

```yaml
summer:
  mvc:
    request-logging:
      body:
        enabled: true
        max-preview-length: 2048
        redacted-fields:
          - password
          - token
          - client_secret
```

正文不是 JSON 时，文本请求按字符截断；二进制请求只记录 `[BODY_UNAVAILABLE]`。无法解析的 JSON 同样不会把原文写入日志。

应用如需接入其他输出系统，可提供自己的 `RequestLogSink` Bean；实现只接收 `RequestLogRecord`，不读取 Servlet 请求流、不维护全局请求数据袋，也不改变业务响应。请求日志过滤器的装配顺序位于 requestId 关联过滤器之后，因此摘要中的 `requestId` 与响应头保持一致。

## 最小安全接入

1. 显式开启 Authorization Server/Resource Server。
2. 浏览器客户端声明 `public-client: true`、`client-authentication-method: none`、`authorization_code` 和 redirect URI；自动配置强制 PKCE S256，并按 public client 默认设置 code 5 分钟、access/ID Token 15 分钟且不签发 refresh token。
3. 为业务端点配置 scope，并用 `@PreAuthorize("hasAuthority('SCOPE_xxx')")` 验证。
4. 生产覆盖内存客户端、用户和临时 JWK 实现。`GET /csrf` 返回原始 CSRF JSON，`/oauth2/**`、`/.well-known/**`、`/connect/**` 与协议登录响应不经过统一业务 envelope。
