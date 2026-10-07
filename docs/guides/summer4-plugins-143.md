# T35 受信任插件启停与升级

本切片把插件扩展限制在 Summer Sample 内置、已登记的 SPI 实现。`SummerPlugin` 只声明稳定元数据：能力、菜单、参数声明、接口基路径和数据资源；运行时不接受类、脚本或 JAR 上传。

## 生命周期

平台管理员通过 `/api/v1/platform/plugins` 提交插件 ID、版本和产物摘要。服务端只接受 `PluginCatalog` 中的摘要，记录到 `summer_plugin`，并把不兼容版本置为 `INCOMPATIBLE`。只有 `REGISTERED`/`DISABLED` 且摘要仍受信任的记录才能通过 `:enable`；`:upgrade` 会重新执行摘要与版本检查。`/diagnostic` 返回受信任、兼容性、状态和诊断问题。

平台接口要求 `platform.plugins.read` 或 `platform.plugins.write` 的 scope 与 platform capability claim 同时存在。企业接口要求活动成员关系和对应 `enterprise.plugins.*` capability；示例插件还要求 `customer.read`，因此插件 API 不能扩大数据权限。

## 示例插件

`customer-insights` 是应用内置示例。它声明客户、系统参数、字典三类资源，菜单权限为 `enterprise.plugins.customer-insights.read`，配置声明为 `customer-insights.label` 和 `customer-insights.segment`，读取接口为 `/api/v1/enterprises/{enterpriseId}/plugins/customer-insights/summary`。接口通过 `PublishedParameterReader` / `PublishedDictionaryReader` 复用现有公开读取服务，执行当前主体、组织/本人数据范围、接口拒绝和覆盖优先级检查。参数仅返回可见且 ACTIVE 的公开值；字典采用当前发布指针对应的不可变快照，并按当前字典类型及字典项范围过滤，草稿修改不会提前生效。企业菜单另按菜单能力过滤。只把真正不可见/不存在的可选配置视为缺省；权限拒绝和依赖故障继续失败，不伪装成成功。

`CustomerAuditService` 在成功读取后写入 `plugin:customer-insights` 审计。每次受控读取使用自己的事务，避免可选配置的 NOT_FOUND 异常污染整次请求的事务。插件不再自行查询参数值或字典项表。

数据库迁移为 `V28__trusted_plugins.sql`。Summer UI 的“受信任插件”页面使用真实生命周期 API，展示诊断和示例读取结果。

## 原切片验收证据

- `PluginLifecycleHttpTest`：3 tests，成功注册/启用/企业读取、错误摘要、版本不兼容、未注册启用、缺少 Customer 数据权限均为真实 MockMvc HTTP 路径。
- Summer UI `npm test`：19 个 Node/契约测试通过，其中 `verify-plugins.mjs` 检查生命周期、诊断、示例 API 和路由。
- `npm run typecheck` 和 `npm run build` 通过。
- `:summer-sample:compileJava` 通过。

本切片不关闭 Issue、不推送远端，也不改变框架公共模块。

## 2026-10-07 修复复验

组织/本人范围、有效覆盖、项范围、发布草稿/回滚、公开撤回/停用、菜单与权限存储故障回归见 `PluginConfigurationHttpTest`。本轮计数、红绿证据与尚待 T36 浏览器/多节点验收的边界见 [T32/T35 验收记录](summer4-t32-t35-acceptance.md)。原切片前端通过记录不是本轮浏览器验收。
