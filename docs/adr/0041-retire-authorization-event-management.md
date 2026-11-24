# 移除授权事件管理页面及接口

状态：已接受，2026-11-20 用户要求清理授权事件功能与后端接口。

移除独立 `/authorization-events` 页面、事件列表与人工重试、页面内的授权解释面板、菜单注册、语言项、请求封装及 `authorization.events.retry` 操作标识。权限管理仍由 `/permissions` 提供。

以下接口退役，已认证请求返回 404：

- `GET /api/authorization-events`
- `POST /api/authorization-events/{eventId}/retry`
- `GET /api/v1/platform/authorization/explanation`
- `GET /api/v1/enterprises/{enterpriseId}/authorization/explanation`

删除接口专用 controller、授权解释服务、事件列表/人工重试服务方法和 DTO，以及仅供授权解释使用的单权限预览分支；移除事件接口特有的租户查询参数解析。未注册路由继续使用原有 404 流程，其他管理资源的鉴权边界不变。

内部授权变更 outbox、领域版本推进、Redis Stream 投递、消费去重和权限缓存失效继续运行，保留自动重试及死信终态。历史事件、回执表和迁移文件保留，不清除既有数据。本决定移除旧实现中的事件查询与人工重试管理入口，继续遵循 ADR 0027 的内部通知机制、ADR 0033 的菜单刷新和 ADR 0039 的权限管理模型。

部署时通过正式菜单清单接口发布去除 `authorization-events` 的新版本；接口目录扫描使卸载的 handler 失效。测试验证旧路由无 handler、返回 404 且不能修改 outbox，并继续覆盖角色变更写入事件、事务回滚、并发版本和内部消费。旧页面及接口的验收脚本保留为历史材料，不再作为当前功能验收入口。
