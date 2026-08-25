# Starter 接入指南

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
| `summer.mvc.cache-request-body` | `true` | 独立启用请求体重复读取；JSON Field/FieldList 依赖此能力，请求关联过滤器不受此开关影响。 |
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
        style = "integerStyle"
)
OrderView query(FieldValues fields) {
    Integer page = fields.get("page", Integer.class);
    // 查询并返回结果
}
```

JSON Parameter contract 可以和正常的 `@RequestBody` 反序列化、Jakarta Bean Validation 同时使用。Field/Style 负责传输字段白名单、可复用 Style、类型转换和聚合 Field violation；Bean Validation 继续负责 DTO 字段约束与跨字段对象约束：

```java
@PatchMapping("/customers/{email}")
@FieldContract(rejectUnknownJson = true)
@Field(param = "email", source = FieldSource.PATH, required = true, style = "customerEmailStyle")
@Field(param = "displayName", source = FieldSource.JSON, nullable = true, style = "displayNameStyle")
@Field(param = "active", source = FieldSource.JSON, style = "booleanStyle")
CustomerView update(
        FieldValues fields,
        @Valid @RequestBody UpdateCustomerRequest request
) {
    boolean displayNameWasPresent = fields.contains("displayName");
    String displayName = fields.get("displayName", String.class);
    // omitted: contains=false；显式 null: contains=true 且 get(...) == null
}
```

`required` 只约束字段是否存在，`nullable` 只约束已存在的 JSON 值能否为 null。JSON 字符串、数字和布尔值按 Style 导出类型保持原生类型边界，例如字符串 `"42"` 不会作为 integer Style 的 JSON number；对象和数组不能作为标量 `@Field`。`@FieldContract(rejectUnknownJson = true)` 会拒绝未声明属性，默认关闭以保持兼容。请求体只解析一次，随后仍可由 Jackson 绑定 DTO；malformed JSON 统一返回 HTTP 400。若真实 MVC 映射声明 JSON Field/FieldList 或严格 JSON `FieldContract`，却配置 `summer.mvc.cache-request-body=false`，应用会在启动时以明确配置错误失败；只使用 PARAM/PATH 的应用仍可关闭缓存。

内置 Style 以 Spring Bean 名注册：

| Bean 名 | 导出类型 | 接受格式 |
| --- | --- | --- |
| `emptyStyle` | `String` | 非空参数值。 |
| `integerStyle` | `Integer` | 十进制整数。 |
| `longStyle` | `Long` | 十进制长整数。 |
| `decimalStyle` | `BigDecimal` | 十进制数字。 |
| `booleanStyle` | `Boolean` | `true` 或 `false`，忽略大小写。 |

应用可以按 Bean 名新增 Style，也可以用同名 Bean 覆盖内置实现：

```java
@Bean("customerCodeStyle")
Style customerCodeStyle() {
    return new PatternStyle(
            String.class,
            "a customer code",
            Map.of(),
            Pattern.compile("C-\\d{6}")
    );
}
```

PARAM/PATH 列表使用显式数量参数。默认数量参数为 `<param>.count`，也可以通过 `countParam` 指定；`maxSize` 可收紧单个接口的列表上限，但不能突破全局上限：

```java
@PostMapping("/orders")
@FieldList(
        param = "items",
        maxSize = 20,
        items = {
                @Item(param = "sku", name = "sku", required = true, style = "emptyStyle"),
                @Item(param = "quantity", name = "quantity", required = true, style = "integerStyle")
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
                @Item(param = "sku", name = "sku", required = true, style = "emptyStyle"),
                @Item(param = "details.quantity", name = "quantity", required = true, style = "integerStyle"),
                @Item(param = "note", name = "note", nullable = true, style = "emptyStyle")
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

应用 JSON 端点默认返回 `success/data/error/requestId/timestamp`。OAuth2/OIDC、Actuator、文件、字节流与 Range 响应自动绕过；应用端点可使用 `@RawResponse` 显式保留原始响应。

## 最小安全接入

1. 显式开启 Authorization Server/Resource Server。
2. 从安全配置通道提供客户端 secret。
3. 为业务端点配置 scope，并用 `@PreAuthorize("hasAuthority('SCOPE_xxx')")` 验证。
4. 生产覆盖内存客户端、用户和临时 JWK 实现。
