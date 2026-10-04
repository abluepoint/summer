# Summer 4 字典管理与表单消费

对应 Issue #114，依赖 T04（提交 `13d9a66`）。

## 需求与设计

- 用户故事 7：`summer_dictionary_type` 管理字典编码、发布、公开读取和启用状态；`summer_dictionary_item` 管理值、标签、排序和启用状态。
- 用户故事 8：`GET /public/dictionaries/{code}` 只返回已发布、公开且启用的字典，以及启用的字典项。
- 用户故事 9：管理写入经过 T04 的追加式操作流水，并通过刷新按钮让 UI 重新读取最新选项。
- 用户故事 11：接口沿用 Summer MVC 的 Parameter contract、统一响应、requestId 和统一错误信封。

版本发布历史和回滚留给 T11，实时推送留给 T31。

## 实现

- 后端模块：`summer-sample/.../dictionary`，管理员 scope 为 `platform.dictionary.read/write`。
- 管理接口：`/api/dictionaries`、`/api/dictionaries/{code}` 及其 `/items` 子资源。
- 公开接口：`/public/dictionaries/{code}`，放在受保护 `/api/**` 之外以支持匿名表单读取。
- 迁移：MySQL `V4__dictionary.sql`；H2 测试由 JPA schema 自动创建。
- 前端：`/dictionary/form` 使用真实 API、Arco `Select` 和显式刷新动作，不读写浏览器持久化存储。

## 验证证据

- `DictionaryHttpTest`：管理员创建、启停和排序，公开过滤，非法值，未发布字典和无权限。
- `DictionaryMySqlIntegrationTest`：真实 MySQL/Flyway 建表及 HTTP 读取。
- Summer UI `npm test`：基线、Redis、字典契约和既有 19 个 Node 测试。
- Summer UI `npm run typecheck`、`npm run build`：通过。

已知限制：本票不实现版本历史、回滚或实时推送；生产 Caddy/Vite 只代理公开字典路径，不改变认证边界。
