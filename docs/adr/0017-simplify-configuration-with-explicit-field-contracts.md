# Simplify configuration while keeping each field contract explicit and complete

> 部分被 [ADR 0019](0019-reject-empty-field-lists.md) 替代：下文关于允许显式空集合、minSize 默认 0 的记录保留作历史；当前规则为不允许空集合、minSize 默认 1，maxSize=0 为非法配置。
>
> 公共 Style 的内置名称安排由 [ADR 0022](0022-define-styles-without-builtin-names.md) 替代：框架提供规则构建能力，全部具名 Style 由应用公共配置显式定义，不提供框架预注册名称；Java 链式定义写法已确认。下文相应旧决定与待定说明保留作历史，其余决定继续适用。

Summer 4 各项能力以降低开发过程中的配置成本为目标；Field 的每个字段仍须明确、完整，简化重点是每项配置的表达与设置方式。选择保留字段契约的完整声明，拒绝以“公共字段规则 + 接口只声明差异”作为主要使用模型，接受一定声明重复以保持接口契约可直接理解。已确认的默认值、公共 Style 使用方式和统一 Java 定义方式见下文；具体内置规则清单、构建 API 及其他细节仍待后续访谈确定，本决定不代表完整设计已定稿或实现。

## 已确认的必填默认值

Field 默认 `required = true`，可选字段显式声明 `required = false`，以减少常见必填字段的重复配置。筛选条件、PATCH 中可省略的字段等仍须显式标为可选，不根据 HTTP 方法自动改变必填性；必填约束处理字段是否出现，显式 null 的许可由独立的 nullable 规则处理。

这是对当前 `required = false` 默认值的行为变更。实施时必须逐项审查省略 required 的既有字段，并为确实可选的字段补充 false，防止列表筛选和部分更新被意外变成必填；当前仅记录决定，代码尚未修改。

## 已确认的 null 默认策略

保留 `nullable = false` 默认值，只有允许显式 null 的字段才声明 `nullable = true`。`required = false` 只允许省略字段，不自动允许 null；例如 PATCH 可省略 `email`，但显式提交 `"email": null` 仍因 nullable=false 被拒绝。

| required | nullable | 字段缺失 | 显式 null |
| --- | --- | --- | --- |
| true（目标默认） | false（保留默认） | 拒绝 | 拒绝 |
| false | false | 允许 | 拒绝 |
| true | true | 拒绝 | 允许 |
| false | true | 允许 | 允许 |

上表仅定义 presence 与 nullability，非 null 值继续按字段的 Style 校验。空字符串和全空白字符串的统一语义见下文。

## 已确认的空字符串与全空白字符串语义

未传字段由 required 判断，显式 null 由 nullable 判断；空字符串 `""` 和全空白字符串 `"   "` 都视为已经传值，由所选 Style 判断是否合法。该语义在 Query、表单参数与 JSON 等来源间保持一致，不因字符串为空或全空白而跳过 Style 校验，也不将其当作缺失或 null。

例如，可选邮箱字段省略时允许通过，显式传空字符串时仍须经过邮箱 Style 并因格式不合法被拒绝。允许空字符串的文本 Style 则可以接受空字符串，required 本身不承担非空白校验。来源冲突仍按字段是否出现判断，空字符串不能使一个已出现的来源被忽略。

当前非 JSON 字段读取使用 `StringUtils.hasText(rawValue)`，把空字符串和全空白字符串按缺失处理；JSON 字符串已经进入 Style 校验。迁移须修正前者，并验证各来源对缺失、null、空字符串、全空白字符串的处理，尤其检查可选字段此前跳过校验的行为变化；本轮尚未修改实现。

## 已确认的公共 Style 使用方式

字段逐个声明，并显式选用公共 Style；类型和格式规则集中定义在 Style 中。框架提供常用公共 Style，业务只新增特殊规则，不按字段名自动套用整份公共字段契约；required、nullable 仍由字段自身的默认值与显式声明决定。

目标用法示意：

```java
@Field(param = "email", style = "email")
```

该字段必填、不允许 null、来源自动识别，使用公共 email Style 的类型和格式规则。此示例表达已确认的使用方式；当前实现仍按 Spring Bean 名查找 Style，email 尚非框架内置 Style，必填默认值与来源推断也尚未迁移。

公共 Style 的具体清单、内置名称和构建 API，后续分别确定。

## 不在 Field 上追加业务校验属性

不允许通过 `@Field` 的 `maxLength`、数值范围等额外属性追加业务约束；不采用 `@Field(param = "email", style = "email", maxLength = 200)` 这类设计。Field 的配置项保持精简。

## 已确认的业务限制归属

普通 Field 的格式、长度、数值范围等业务限制统一放入具名 Style，字段只选择 Style。FieldList 的集合级约束按下文单独配置，不套用这条归属规则。例如，公共定义中的 `customerEmail` 表达邮箱格式、最长 200 字符的完整规则，字段引用：

```java
@Field(param = "email", style = "customerEmail")
```

不同业务约束使用各自的具名 Style，相同规则可由多个字段复用；字段自身仍负责 required、nullable。Style 的统一定义方式见下文；具名 Style 之间的继承、组合机制尚未确定，示例尚未落地为运行时代码。

## 已确认的统一 Java 定义方式

Style 统一在 Java 公共配置中定义，使用 `@Configuration` 与 `@Bean` 注册，Field 按 Bean 名引用，默认名称取 Bean 方法名。常见规则通过简洁 API 表达，复杂规则也遵循同一个 Style 接口与 Bean 注册方式。统一性优先于按规则复杂度分别选择配置语言，因此不采用“常规 Style 用 YAML、复杂 Style 用 Java”的分流方式；本决定仅约束 Style 定义，不改变其他应用配置的载体。

候选构建 API 示例：

```java
@Bean
public Style customerEmail() {
    return Styles.string().email().maxLength(200).build();
}
```

以上确认公共 Java 配置与字段引用的统一形式，`Styles` 构建器的具体签名仍为草案。当前代码已有 Style Bean 注册与按名称查找，但尚未实现该构建 API；后续简化应减少公共定义中的样板，同时保留每个接口字段的明确声明。

## 已确认的 Style 引用启动检查

启动时自动检查所有已注册接口的 Field 所引用的 Style，无须额外配置开关。引用名称不存在时，应用启动失败，错误信息明确指出接口方法、字段和不存在的 Style 名称；不能等收到包含该字段的请求后才发现配置错误。可选字段同样需要检查，不能因请求可能省略它而绕过引用有效性检查。

当前 `StyleRegistry.require` 在请求校验路径查找 Style，找不到时抛出异常；已有 `JsonFieldCachingValidator` 的启动检查只处理 JSON 校验所需的 body 缓存条件，尚未覆盖 Style 引用。本决定尚未实现，不扩张为对任意业务校验逻辑的启动期验证。

## 已确认的未知字段严格策略

使用 Field 契约的接口始终拒绝未声明的请求字段，Query 与 JSON 保持一致；遇到未知字段返回 HTTP 400，并在错误中指出该字段。例如契约声明 `email` 而请求提供 `emial` 时，应报错，不能静默忽略。开发者无须为每个接口重复开启严格模式；本决定的适用范围是使用 Field 契约的接口，不据此为所有 Spring MVC 或协议端点添加字段白名单。

不提供“忽略未知字段”的宽松模式或开关，包括全局配置和接口级设置；需要接收的字段必须明确声明。这项限制减少配置项，并确保使用 Field 契约的接口具有统一的严格行为。

这部分替代 [ADR 0013](0013-expand-parameter-contracts-to-json-and-path.md) 中“严格未知字段校验通过方法级 FieldContract 选择启用”的使用方式，保留其传输字段白名单的方向。该段记录历史设计；现行行为见本文末的 2026-09-29 implementation status。公共参数的归属与处理见下文。

## 已确认的公共参数边界

使用 Field 契约的业务接口将请求追踪等技术信息放在 HTTP Header 中，例如现有的 `X-Request-Id`。分页、排序等业务参数仍在每个接口逐项声明 Field，并复用公共 Style；公共复用的是参数规则，不是未声明也可以接收的字段名单。

Query 和 JSON 不设置自动放行的特殊参数名单。未声明的 `requestId`、分页或排序字段仍按未知字段拒绝，不能因为名称常见或被视为技术参数而跳过契约检查。本边界适用于使用 Field 契约的业务接口，既有协议端点继续遵守自身协议处理规则；当前仅记录决定，尚未完成严格检查的代码迁移。

## 已确认的集合长度自动推断

集合长度直接从实际请求数据推断：JSON 读取数组长度，Query/表单根据 `items[0].sku`、`items[1].sku` 等带索引的字段识别集合元素。不再要求调用方提交专门的数量字段，取消 `FieldList.countParam` 配置，以减少开发者和调用方需要维护的重复信息。

当前 JSON FieldList 已按数组长度读取；非 JSON 路径仍依赖 `countParam` 或默认的 `<param>.count` 决定读取范围，自动索引识别尚未实现。迁移须更新相关注解与调用方，旧数量字段不再具有计数语义，也不获得未知字段检查的豁免。本次确认 JSON 与 Query/表单的长度推断方式和下述索引连续性规则；缺失与空集合的历史表达见下一节，现行非空集合约束见 ADR 0019；[ADR 0018](0018-infer-field-sources-automatically.md) 已确认集合不支持 PATH 来源。

Query/表单的集合元素索引必须从 0 开始连续排列，与 JSON 数组的位置语义一致。例如 `items[0].sku=A&items[1].sku=B` 合法，`items[0].sku=A&items[2].sku=B` 因缺少索引 1 返回 HTTP 400。索引集合存在缺口或未从 0 开始时不接受该集合，不自动补空元素或压缩重编号；同一元素的不同子字段共享同一索引，仍分别按各自字段契约校验。该规则不要求请求参数在传输文本中按索引顺序排列，自动识别与连续性检查均尚未实现。

## 已确认的集合缺失与空集合表达

Query/表单未出现任何集合参数时，视为该来源未提供集合；不将其转换为显式空集合。显式传递空集合使用 JSON，例如 `{"items":[]}`，该值与未出现 `items` 的请求不同。Query/表单不额外设计空集合标记，也不恢复以数量 0 表示空集合的方式，以避免增加另一套输入约定。

缺失按整个请求中是否存在该集合判断，仍遵守来源推断规则：Query/表单未提供集合而 JSON 提供 `items: []` 时，集合已由 JSON 提供。本节确定表达方式，集合本身的 required/nullable 规则见下文；不能把显式空集合自动解释为满足所有业务约束。

## 已确认的集合必填与可空默认值

集合本身沿用普通字段的 required/nullable 语义：默认 `required = true`、`nullable = false`，允许省略时显式声明 `required = false`，允许显式 null 时声明 `nullable = true`。前述四种 presence/nullability 组合表同样适用于集合；允许省略不等于允许 null。

`[]` 算已提供，满足 required 对出现性的要求；是否要求至少一个元素由独立的数量约束决定。required 不表达“非空集合”，nullable 约束集合本身是否可为 null，不替代集合元素及子字段的校验。

当前 FieldList 尚无 required、nullable 属性，JSON 路径把集合缺失和 null 都报为 required 错误；可选和可空集合的声明与读取需要迁移。集合数量约束的归属见下文，尚未修改运行时代码。

## 已确认的 FieldList 独立业务约束

FieldList 的集合级业务约束直接在 FieldList 上配置，例如 `maxSize`；不为集合数量限制单独定义并引用 Style。集合内部的子字段仍逐项声明并引用各自的 Style，普通 Field 不增加长度、范围等约束属性的决定继续适用。

`maxSize` 采用显式配置优先：未声明时使用公共默认值，显式声明时使用该集合自己的值。公共配置承担默认值的角色，不再作为局部配置无法超过的上限。例如公共默认值为 100 时，未声明 maxSize 的集合上限为 100，声明 20 的上限为 20，声明 500 的上限为 500。

FieldList 增加 `minSize`，默认 0；需要非空集合时显式声明 `minSize = 1`。required 决定集合是否必须出现，minSize 决定已提供集合的最少元素数量：例如 `required = true, minSize = 0` 允许已提供的 `[]` 通过最小数量检查，而 `required = true, minSize = 1` 拒绝 `[]`。这两个属性分别表达出现性和数量下限，不能相互替代。

集合数量配置在启动时自动校验：先按显式配置优先规则解析有效上限，再检查上下限是否合法。`minSize = 5, maxSize = 3` 这类矛盾配置使应用启动失败，错误明确指出接口方法、集合字段和具体配置值；未声明 maxSize 时也须将 minSize 与解析后的公共默认上限比较。检查不依赖收到请求，也不因集合可选而跳过。

当前 FieldList 已有 `maxSize`，实现取局部值与公共 `summer.mvc.field-validation.max-list-size` 的较小值，公共配置当前默认为 100；显式配置优先的决定需要修改当前取较小值的逻辑，尚未实现。minSize 属性、最小数量检查和上述启动期上下限检查也尚未实现；maxSize 为 0 等具体取值边界仍待确定。

## 已确认的集合子字段统一声明

移除独立的 Item 注解，FieldList.items 改为 `Field[]`。普通字段与集合内部子字段统一使用 Field，采用相同的声明方式及默认值：required 默认 true、nullable 默认 false，显式引用公共 Style；name 沿用普通 Field 的导出别名约定，未设置时使用 param。

目标声明形式（待实现）：

```java
@FieldList(
    param = "items",
    maxSize = 20,
    items = {
        @Field(param = "sku", style = "sku"),
        @Field(param = "quantity", style = "quantity")
    }
)
```

FieldList 继续负责集合的出现性、可空性和独立数量约束；子字段 param 相对于每个集合元素读取，并逐项校验，字段所在层级不因复用注解而改变。集合非空规则以 ADR 0019 为准。

当前 FieldList.items 仍为 Item[]，Item 的 name 必填、required 默认 false。迁移须替换现有 Item 声明、更新读取与严格未知字段检查，并审查子字段的可选声明和导出别名；启动期 Style 引用检查同样覆盖集合子字段。本次未修改实现。

## 2026-09-29 implementation status

标量 `Field` 已落地本 ADR 的运行时契约：`required` 默认 `true`、`nullable` 默认 `false`；缺失和显式 JSON `null` 分别按两个约束处理，空字符串与全空白字符串均继续进入 Style 校验。`FieldContract` 不再提供 `rejectUnknownJson` 宽松开关，使用 Field 契约的接口对 Query/form、JSON 和 PATH 输入统一执行未声明字段检查并返回 400。DTO 绑定与 Jakarta Bean Validation 仍在 Field 校验之后独立执行。
