# Summer 4 参数与字典二级缓存

对应 Issue #117，依赖 T06（`fda2183`）和 T08（`503f9f3`）。

## 设计

- 系统参数和公开字典读取使用 Caffeine L1、Redis L2；缓存 key 同时包含租户、资源、版本和条目标识。
- Redis 版本键在失效时递增。每次读取先校验版本，再访问 L1/L2；节点重连后旧版本条目自动不可达。
- 同一条目使用节点内锁防止并发回源击穿，L1/L2 共享 TTL，默认 5 分钟。
- 参数 outbox 投递成功后递增参数缓存版本；每个节点使用独立 Redis Stream consumer group 接收事件并清理本地 L1。共享 receipt 只负责幂等，不阻止本节点清理缓存。
- 字典写入在成功保存后递增字典缓存版本。Redis 不可用时读取绕过缓存回源，记录固定诊断日志，不返回无法校验版本的旧缓存。

## 配置

`summer.sample.cache.ttl` 控制 L1/L2 TTL，`SUMMER_SAMPLE_CACHE_TTL` 可覆盖；`summer.sample.cache.node-id`（或 `SUMMER_SAMPLE_CACHE_NODE_ID`）必须在多节点部署中保持每个节点唯一。Redis 地址和认证沿用现有 `spring.data.redis` 运行时注入配置。

## 验证证据

- `VersionedTwoLevelCacheTest`：L1 命中、并发击穿保护、Redis 版本不可读时绕过旧缓存。
- `VersionedTwoLevelCacheRedisIntegrationTest`：真实 Redis 两节点 L2 共享、版本失效、TTL 到期。
- `DictionaryHttpTest`、`SystemParameterHttpTest`、`SystemParameterEventDeliveryServiceTest`、`SystemParameterEventConsumerTest`：真实 HTTP、outbox 投递和幂等消费回归。

未改变 Summer UI 的现有参数和字典页面契约；页面继续通过真实 API 读取，缓存对前端透明。
