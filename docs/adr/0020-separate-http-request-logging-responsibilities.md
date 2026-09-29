# Separate HTTP request logging responsibilities

MVC 管理共享请求数据，HTTP 日志适配层消费这些数据并生成日志快照，日志模块接收结构化记录并负责输出。请求来源推断与请求日志都可能需要读取请求体，因此读取与复用能力归属于 MVC，不能由日志模块另行读取原始请求流，也不能只围绕 Field 校验设计。

## 职责边界

- **MVC**：管理请求数据读取、必要的缓存与复用，以及已有的 requestId 关联能力；为字段校验、业务读取和 HTTP 日志适配提供一致的数据基础。
- **HTTP 日志适配层**：理解 HTTP 请求生命周期，采集请求与处理结果信息，将共享请求数据转换为适合输出的日志快照；脱敏和长度限制作用于日志副本，不修改业务接收的数据。
- **日志模块**：消费结构化日志记录，负责输出及输出机制；不承担 Servlet 请求流读取、Field 来源推断或请求体缓存。

沿用 [ADR 0010](0010-use-framework-native-request-contexts.md) 的框架原生请求上下文方案，不引入通用请求数据袋或自建全局 ThreadLocal。沿用 [ADR 0003](0003-keep-summer-common-narrow.md) 的 Common 边界，不把 HTTP 日志实现放入 Common。适配层是职责边界，具体包名、Gradle 模块拆分与自动装配方式尚未确定。

## 默认内容与公共配置

请求日志默认只记录摘要：requestId、请求方法、路径、响应状态和耗时。请求正文默认不记录，需要时通过公共配置统一开启，无需逐接口声明；开启后统一脱敏、限长，业务仍读取完整请求。

本轮范围止于请求摘要与按需请求正文的采集、快照处理和输出，保持上述职责边界与少量公共配置，不继续扩展日志能力范围。具体脱敏规则、长度值和配置键留待实现规格确定。

## 实现状态

本次确认默认摘要、正文按需开启的策略。后续 [ADR 0021](0021-manage-request-body-caching-automatically.md) 已确认由 MVC 自动管理必要缓存并移除 `summer.mvc.cache-request-body`，其与 ADR 0010 的替代关系由该决定记录；具体缓存容量与读取机制留待实现设计。

当前 MVC 使用 `RequestLoggingFilter` 采集请求完成后的摘要，并将不可变的 `RequestLogRecord` 交给 `RequestLogSink`；默认 `Slf4jRequestLogSink` 只输出结构化摘要。过滤器负责 HTTP 生命周期和快照，sink 只负责输出，不读取 Servlet 请求流。`summer.mvc.request-logging.enabled` 默认开启，可由应用公共配置关闭；`summer.mvc.request-logging.body.enabled` 默认关闭，开启后由同一组公共配置控制预览长度和 JSON 敏感字段名。正文快照只读取 MVC 自动缓存的请求包装器，先脱敏再截断；业务仍读取完整请求体。HTTP seam 测试验证默认关闭、requestId 关联、脱敏、截断、业务重复读取和输出失败不改变业务响应。

请求体能力仍由 MVC 的请求数据管理负责。缓存包装器保存实际接收的完整正文；`summer.mvc.request-logging.body.max-preview-length` 只限制日志副本，不能替代应用或 Servlet 容器的请求大小限制。未知或不可解析的正文不会直接写入日志。已有架构检查不构成请求日志能力的验收证据。
