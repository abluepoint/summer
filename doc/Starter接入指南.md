# Starter 接入指南

## 组合

| 目标 | 依赖 |
| --- | --- |
| 统一错误、消息、上下文 | `summer-common-starter` |
| Web 请求链与统一响应 | `summer-web-starter` |
| JPA、模板 SQL、Tuple 映射 | `summer-data-jpa-starter` |
| OAuth2/OIDC | `summer-security-starter` |
| 完整平台骨架 | `summer-boot-starter` |

所有模块版本由 `com.abluepoint.summer:summer-dependencies` 统一约束。业务构建只声明 Summer BOM 和需要的 starter，不重复声明 Boot、Cloud、Modulith 或 Summer 子模块版本。

## 开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `summer.common.enabled` | `true` | 消息解析与公共基座。 |
| `summer.web.enabled` | `true` | Web 自动配置总开关。 |
| `summer.web.cache-request-body` | `true` | 请求体重复读取与上下文注入。 |
| `summer.web.wrap-responses` | `true` | Controller 返回值统一包装；OAuth2/Actuator 自动跳过。 |
| `summer.web.field-validation.enabled` | `true` | 开启查询参数、表单参数的 Field/Style 校验与类型化导出。 |
| `summer.web.field-validation.max-list-size` | `100` | `FieldList` 全局最大元素数，必须为正整数。 |
| `summer.data.jpa.enabled` | `true` | SQL 模板源、渲染器、TupleMapper、JpaSqlTemplate。 |
| `summer.security.enabled` | `true` | 安全自动配置总开关。 |
| `summer.security.authorization-server.enabled` | `false` | 授权服务过滤链与令牌签发。 |
| `summer.security.resource-server.enabled` | `false` | JWT 资源保护链。 |

## 最小数据查询

1. 在资源文件中用 `-- findByEmail` 声明模板段。
2. 注入 `JpaSqlTemplate`。
3. 提供模板参数、JPA 命名参数和目标 Bean/Record/接口类型。

## Field/Style 参数校验

`@Field` 适合校验查询参数、表单参数和其他动态参数，并把通过校验的字符串转换为 Controller 可显式读取的类型化值：

```java
@GetMapping("/orders")
@Field(param = "page", required = true, style = "integerStyle")
OrderView query(FieldValues fields) {
    Integer page = fields.get("page", Integer.class);
    // 查询并返回结果
}
```

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

列表参数使用 `@FieldList`。默认数量参数为 `<param>.count`，也可以通过 `countParam` 指定；`maxSize` 可收紧单个接口的列表上限，但不能突破全局上限：

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

所有字段错误会一次聚合为 HTTP 400，错误码为 `SUMMER-400`，并通过 `violations` 返回字段路径和原因；校验失败时不会导出部分结果。未知 Style、空 `FieldList.items` 等属于应用配置错误，会失败关闭。

Bean Validation 仍是 DTO 和 JSON 请求体的首选方案。Field/Style 主要用于参数化请求和类型化导出；旧版 JSON Schema、全局 Context 和静态应用上下文链路不在本次兼容范围内。

## 最小安全接入

1. 显式开启 Authorization Server/Resource Server。
2. 从安全配置通道提供客户端 secret。
3. 为业务端点配置 scope，并用 `@PreAuthorize("hasAuthority('SCOPE_xxx')")` 验证。
4. 生产覆盖内存客户端、用户和临时 JWK 实现。
