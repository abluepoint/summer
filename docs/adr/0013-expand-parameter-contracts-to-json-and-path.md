# Expand parameter contracts to JSON and path sources

> 缓存部分被 [ADR 0021](0021-manage-request-body-caching-automatically.md) 替代：MVC 自动管理必要缓存，移除缓存开关及其关闭时的专属启动检查；以下保留历史决定。严格模式与来源配置的其他替代关系见 ADR 索引。

Summer MVC 将 Parameter contract 从查询/表单参数扩展到显式的 `PARAM`、`JSON` 和 `PATH` 来源，统一使用 Field/Style 完成传输字段白名单、聚合校验、类型转换和 `FieldValues` 导出；JSON DTO 与跨字段对象约束仍由 Jakarta Bean Validation 负责。JSON Field 支持根标量和点分嵌套标量，JSON FieldList 支持一层对象数组，`required` 与 `nullable` 分别表达 presence 和显式 null，严格未知字段校验通过方法级 `FieldContract` 选择启用。JSON 契约复用 Summer MVC 的请求体缓存和应用 `ObjectMapper`，真实映射声明 JSON Field/FieldList 且关闭缓存时应用启动失败。该决策 supersedes [ADR 0005](0005-keep-parameter-contracts-alongside-bean-validation.md) 中“Parameter contract 不处理 JSON 请求体”的边界，但保留其 Field/Style 与 Bean Validation 并存、且不恢复 JSON Schema 的核心方向和历史记录。
