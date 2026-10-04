# T10 系统参数发布与回滚

本票在现有系统参数 CRUD 和 T09 二级缓存之上增加独立的版本控制面。草稿写入 `summer_system_parameter_version`，不会改变 `summer_system_parameter`，运行时读取仍只读当前生效记录。

## HTTP 契约

- `POST /api/system-parameters/{key}/draft`：保存草稿。请求必须带 `baseVersion` 和 `idempotencyKey`。
- `GET /api/system-parameters/{key}/versions`：返回版本历史、当前乐观锁版本和当前生效状态。
- `POST /api/system-parameters/{key}/publish`：按 `revision` 发布草稿，并用 `expectedVersion` 检测并发冲突。
- `POST /api/system-parameters/{key}/rollback`：按历史 `revision` 回滚；草稿不能作为回滚目标。
- `POST /api/system-parameters/{key}/deactivate`：按 `expectedVersion` 停用并生成版本记录。

发布、回滚和停用在同一事务内更新当前参数、写版本记录和 MySQL outbox，然后失效 L1 缓存；已有 T09 事件消费者负责其他节点的失效。每个成功动作写入操作审计，接口保留 `X-Request-Id` 和统一错误信封。重复使用同一幂等键会返回先前结果，过期乐观锁返回 `SUMMER_PARAMETER_VERSION_CONFLICT`。

迁移为 `V22__system_parameter_versions.sql`。生产回滚应先停止发布动作，再回退应用版本并按数据库迁移策略保留版本表；历史业务数据不会因代码回退自动删除。
