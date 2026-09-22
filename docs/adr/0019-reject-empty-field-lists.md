# Reject empty FieldList values

FieldList 不允许空集合：请求提供数组时必须至少包含一个元素，JSON `[]` 返回 HTTP 400；不提供允许空集合或只接受空集合的配置模式。这项决定替代 [ADR 0017](0017-simplify-configuration-with-explicit-field-contracts.md) 中允许显式空集合、minSize 默认 0 的部分，按用户最新要求统一为非空集合，减少无需支持的状态和配置。

## 数量与启动检查

`minSize` 默认 1，业务需要更多元素时显式配置更大的下限。先按 ADR 0017 的局部 maxSize 优先、未配置则使用公共默认值的规则解析有效上限，再在启动时检查 `1 <= minSize <= 有效 maxSize`。minSize=0、maxSize=0 或上下限矛盾均使启动失败，并指出接口方法、集合字段和配置值；maxSize=0 不表示默认值、无限制或仅允许空集合。

## 缺失、null 与来源

集合是否必须提供仍由 required 决定，默认 true；可选集合以 `required=false` 允许省略。显式 null 仍由 nullable 独立控制，默认 false，只有显式允许时才能通过；这不赋予空数组通过校验的能力。即使 required=false 或 nullable=true，提交 `[]` 仍返回 400。

Query/表单无集合参数表示该来源未提供；JSON 提供 `[]` 表示出现了非法的空集合，不得按缺失处理或转换成 null，也不能因另一来源未提供而跳过校验。沿用从 0 开始的连续索引、来源冲突返回 400、不增加数量字段或空集合标记、未知字段严格检查等既有决定。

## 实现状态

当前 FieldList 尚无 minSize，JSON 路径可以接受空数组；非空集合约束、相关注解默认值和启动检查尚未实现。后续须同步迁移实现、调用方与测试，不能把本次文档修订作为代码验收证据。
