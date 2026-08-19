# Reserve the SUMMER_ error namespace

Summer 4 将 `SUMMER_` 保留为跨能力框架错误码命名空间，业务应用虽然可以实现统一的 `ErrorCode` 契约，但必须使用自己的业务前缀。框架错误码使用 `SUMMER_INVALID_ARGUMENT`、`SUMMER_UNAUTHORIZED`、`SUMMER_FORBIDDEN`、`SUMMER_NOT_FOUND`、`SUMMER_CONFLICT`、`SUMMER_INTERNAL_ERROR` 等语义名称，不使用 HTTP 数字后缀；每个 `ErrorCode` 显式声明传输无关的 `ErrorCategory`，Summer MVC 通过可覆盖的 `ErrorStatusResolver` 将类别或特定业务错误映射为 HTTP 状态，不通过错误码字符串模式推断。项目接受框架码与业务码分别治理的成本，以避免命名冲突、传输语义泄漏和业务错误被误认为框架故障。
