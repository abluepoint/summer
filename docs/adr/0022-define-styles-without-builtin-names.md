# Define Styles without builtin names

不提供框架预注册的具名 Style 或内置名称清单。框架提供可复用的类型、格式与约束构建能力，应用在 Java 公共配置中显式定义所需 Style Bean，字段按名称引用；名称默认取 Bean 方法名，由应用决定。这项决定部分替代 [ADR 0017](0017-simplify-configuration-with-explicit-field-contracts.md) 中“框架提供常用公共 Style、业务只新增特殊规则”及内置名称待定的安排，保留每个字段明确声明、公共规则复用的原则。

## 已确认的定义写法

采用 `@Configuration`、`@Bean` 和链式构建 API，认可如下定义与引用方式：

```java
@Configuration
class FieldStyles {
    @Bean
    Style customerEmail() {
        return Styles.string()
            .email()
            .maxLength(200)
            .build();
    }
}
```

```java
@Field(param = "email", style = "customerEmail")
```

`string()`、`email()` 是规则构建操作，不表示已注册名为 string 或 email 的 Style。即使规则只包含基础类型，也由应用公共配置定义 Bean 后引用；同一 Bean 可以被多个字段复用。Field 的 required、nullable、来源推断及 FieldList 的独立集合约束沿用已有决定，不增加字段级业务校验属性。

## 迁移状态

自动配置不再注册 emptyStyle、integerStyle、longStyle、decimalStyle、booleanStyle，Field.style 不提供默认回退。`Styles` 提供基础类型、格式和长度/范围规则的链式构建能力，应用通过 `@Configuration`/`@Bean` 显式定义全部需要的 Style。启动阶段会遍历 MVC HandlerMethod 的 Field 及集合子字段引用，未知引用会以处理器、字段和 Style 名称诊断并失败关闭。取消内置名称不取消基础类型与格式规则的复用能力；FieldList 的结构迁移和来源推断由后续 ADR 实施票处理。
