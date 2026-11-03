# Reuse SummerException for application failures

Summer 自有框架异常与业务异常统一复用 `SummerException` 作为主要实现：仅字段校验场景保留专用异常子类，且该子类必须继承 `SummerException`；其余自有异常统一直接使用 `SummerException` 和对应的 `ErrorCode` 表达，不再自定义异常类。JDK、Spring 和第三方异常保留原有继承体系，在适配边界统一处理。项目接受减少具名异常类型的取舍，以减少重复实现并统一错误信息的承载方式。

`SummerException` 不支持任意结构化上下文容器，不增加通用的 `data`、`details` 或 `context` 字段。减少自定义异常类不以引入通用数据容器为代价。现有固定格式的字段校验明细 `violations`（字段与校验消息）保留，不属于被排除的通用结构化上下文。

`SummerException` 支持显式传入可选的自定义 `message`。API 错误提示按“自定义 `message` → 国际化错误码文案 → 错误码默认文案”的顺序选择；显式自定义消息原样作为 API 错误提示，不再进行国际化解析，未提供时继续使用现有错误码消息解析机制。原始异常 `cause` 继续保留。

应用 JSON 端点统一由全局 `ApiExceptionHandler` 生成错误响应，删除 Controller 中仅用于拼装错误响应的重复 `catch`；实际用于恢复、重试或补偿的局部捕获保留。迁移保持现有 HTTP 状态及错误码语义，协议端点沿用原有响应规则。错误到 HTTP 状态的映射继续归适配层，不将 HTTP 状态放入异常基类。

本决策补充 [ADR 0008](0008-use-a-default-unified-response.md) 和 [ADR 0009](0009-reserve-the-summer-error-namespace.md)：统一响应的适用范围、框架与业务错误码的命名空间，以及传输无关的错误类别仍遵循原决策。

本决策同时收窄 [ADR 0034](0034-organize-packages-by-responsibility-and-role.md) 配套包结构规范中的功能特有异常安排：仅字段校验所需的专用子类按实际职责归包，其他自有异常不再单独建类或建立对应异常包。已有类型清单保留为源码盘点记录，不代表这些异常类应继续保留。
