# Starter 接入指南

本指南描述当前可用的框架接入能力。Summer UI 的 public client/PKCE 配置尚待实现，目标见 [安全模型](../architecture/OAuth2-OIDC安全模型.md)；不要向当前客户端配置直接填入未支持的 `none` 选项。

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

`@Field` 通过 `FieldSource` 显式声明输入来源，并把通过 Style 校验的值转换为 Controller 可显式读取的类型化结果：

| `FieldSource` | 输入 | 说明 |
| --- | --- | --- |
| `PARAM` | 查询参数和表单参数 | 默认值，兼容既有声明。 |
| `JSON` | JSON request body | 支持根标量和点分嵌套标量；依赖请求体缓存。 |
| `PATH` | URI template variable | 用于稳定校验资源标识。 |

```java
@GetMapping("/orders")
@Field(
        param = "page",
        source = FieldSource.PARAM,
        required = true,
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
@FieldContract(rejectUnknownJson = true)
@Field(param = "orderCode", source = FieldSource.PATH, required = true, style = "orderCodeStyle")
@Field(param = "note", source = FieldSource.JSON, nullable = true, style = "text")
@Field(param = "active", source = FieldSource.JSON, style = "booleanValue")
OrderView update(
        FieldValues fields,
        @Valid @RequestBody UpdateOrderRequest request
) {
    boolean noteWasPresent = fields.contains("note");
    String note = fields.get("note", String.class);
    // omitted: contains=false；显式 null: contains=true 且 get(...) == null
}
```

`required` 只约束字段是否存在，`nullable` 只约束已存在的 JSON 值能否为 null。JSON 字符串、数字和布尔值按 Style 导出类型保持原生类型边界，例如字符串 `"42"` 不会作为 integer Style 的 JSON number；对象和数组不能作为标量 `@Field`。`@FieldContract(rejectUnknownJson = true)` 会拒绝未声明属性，默认关闭以保持兼容。MVC 会在请求链开始时缓存可重复读取的完整正文，Field 校验、Jackson DTO 绑定和 HTTP 日志消费者可以按各自职责读取；malformed JSON 统一返回 HTTP 400。

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

PARAM/PATH 列表使用显式数量参数。默认数量参数为 `<param>.count`，也可以通过 `countParam` 指定；`maxSize` 可收紧单个接口的列表上限，但不能突破全局上限：

```java
@PostMapping("/orders")
@FieldList(
        param = "items",
        maxSize = 20,
        items = {
                @Item(param = "sku", name = "sku", required = true, style = "text"),
                @Item(param = "quantity", name = "quantity", required = true, style = "integerValue")
        }
)
OrderView create(FieldValues fields) {
    List<Map<String, Object>> items = fields.get("items", List.class);
    // 创建并返回结果
}
```

对应请求参数格式：

```text
items.count=2
items[0].sku=A-1
items[0].quantity=2
items[1].sku=B-2
items[1].quantity=5
```

JSON `FieldList` 从实际数组长度取得元素数，只接受一层对象数组，并支持 Item 内的点分嵌套标量路径：

```java
@PostMapping("/orders")
@FieldContract(rejectUnknownJson = true)
@FieldList(
        param = "items",
        source = FieldSource.JSON,
        maxSize = 20,
        items = {
                @Item(param = "sku", name = "sku", required = true, style = "text"),
                @Item(param = "details.quantity", name = "quantity", required = true, style = "integerValue"),
                @Item(param = "note", name = "note", nullable = true, style = "text")
        }
)
OrderView create(FieldValues fields) {
    List<Map<String, Object>> items = fields.get("items", List.class);
    // 创建并返回结果
}
```

局部 `maxSize` 与 `summer.mvc.field-validation.max-list-size` 取更小值。primitive array、nested array 和递归 FieldList 不在支持范围内；数组元素必须是 JSON object。

所有字段错误会一次聚合为 HTTP 400，错误码为 `SUMMER_INVALID_ARGUMENT`，并通过 `violations` 返回字段路径和原因；校验失败时不会导出部分结果。未知 Style、空 `FieldList.items` 等属于应用配置错误，会失败关闭。

Bean Validation 与 Field/Style 是互补契约，不互相替代。旧版 JSON Schema、全局 Context 和静态应用上下文链路不在本次兼容范围内。

应用 JSON 端点默认返回 `success/data/error/requestId/timestamp`。当前 MVC 显式旁路 `/oauth2/`、`/.well-known/`、`/actuator/`，并跳过文件、字节流与 Range converter；应用端点可使用 `@RawResponse` 显式保留原始响应。OIDC logout 和新增 `/csrf` 的完整验证见 [现状与目标差异](../architecture/现状与目标差异.md)。

## 最小安全接入

1. 显式开启 Authorization Server/Resource Server。
2. 当前自动配置要求从安全配置通道提供 confidential client secret；浏览器 public client 的无 secret 配置按 ADR 0015 实施后才可接入。
3. 为业务端点配置 scope，并用 `@PreAuthorize("hasAuthority('SCOPE_xxx')")` 验证。
4. 生产覆盖内存客户端、用户和临时 JWK 实现。
