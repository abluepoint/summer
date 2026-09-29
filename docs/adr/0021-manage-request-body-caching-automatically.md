# Manage request body caching automatically

Summer MVC 自动管理必要的请求体缓存，移除公开配置 `summer.mvc.cache-request-body`。开发者声明 Field/FieldList 后，无须另行开启缓存，MVC 负责保证字段校验与后续业务读取可以正常协作；框架承担读取与复用的内部复杂度，减少需要开发者协调的配置项。

缓存需要由实际启用的消费者决定，不能仅以是否声明 Field 作为唯一条件。按 [ADR 0020](0020-separate-http-request-logging-responsibilities.md)，HTTP 日志适配层需要请求正文时复用 MVC 管理的数据，即使接口没有 Field，也无须额外开启缓存。请求正文日志仍默认关闭，通过日志公共配置开启；自动管理缓存不改变日志内容策略，也不扩大日志模块的职责。

本决定替代 [ADR 0010](0010-use-framework-native-request-contexts.md) 中由独立开关控制请求体缓存的部分，以及 [ADR 0013](0013-expand-parameter-contracts-to-json-and-path.md) 中因关闭缓存而拒绝 JSON 字段契约启动的部分。原有请求关联、框架原生上下文及字段契约边界继续适用。

## 实现与迁移

实现已移除旧配置属性、条件装配与 JsonFieldCachingValidator，并更新配置说明、Sample 配置和相关测试；MVC 始终提供可重复读取的请求体，不能只删除属性而失去重复读取能力。验收覆盖 JSON Field/FieldList 校验后业务仍能读取完整正文，以及 HTTP 日志消费者与字段校验同时读取时互不干扰。

缓存容量与业务请求体限制保持独立：MVC 不提供手动缓存开关，也不通过截断业务请求体满足日志预览长度。业务允许的请求体大小由应用或 Servlet 容器限制，日志正文的脱敏和预览长度由 HTTP 日志快照配置限制；缓存保存实际接收的完整正文。
