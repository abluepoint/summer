# Use framework-native request contexts

Summer 4 删除 `SummerContext` 和 `SummerContextHolder`，不维护自建通用 ThreadLocal 上下文。Summer MVC 负责生成和规范化 requestId，并写入请求属性、响应头和 MDC，通过可注入的 `RequestIdAccessor` 读取；Locale 与安全主体分别使用 Spring 的 `LocaleContextHolder` 和 `SecurityContextHolder`，异步传播按需使用 Spring TaskDecorator 或 Micrometer Context Propagation。请求关联由独立的 `RequestCorrelationFilter` 始终处理，请求体缓存由后续的 `CachedBodyFilter` 处理并受 `summer.mvc.cache-request-body` 独立控制。项目放弃把所有请求信息统一放入一个对象的便利，以避免全局数据袋、上下文重复、线程泄漏和不完整传播；租户等业务信息必须使用独立类型化契约或显式参数。
