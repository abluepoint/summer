# T31｜字典更新实时通知：实际验收交接

对应 [#139](https://github.com/abluepoint/summer/issues/139)、父方案 #108、故事 9/41/42。依赖 #119、#137 已关闭。本次功能证据齐备，状态 **READY_FOR_REVIEW**，由主会话复核和关闭 Issue。新增 9 个验收文件，没有修改生产代码。结构化结果见 [dictionary-notifications-139-result.json](dictionary-notifications-139-result.json)。

## 实现与验证边界

已有字典通知服务在业务事务中持久化失效元数据，提交后通知本节点连接，其他节点通过心跳回读提交事件。页面收到通知后重新读取当前有权公开版本，不把通知当作选项数据。复用 T11 的不可变发布快照与授权版本读取证据；不把 T29 任务 SSE 用例计入本票。

初始候选 SHA-256 为 `9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af`；最终恢复验证使用主会话冻结的 `9f2446f79736b13a28da00cfd9409bbdc08b138eb5f74b270480fdabd1f350e2`。20 个字典后端输入在两个候选间一致，并匹配 T11；两份字典 UI 输入与 T11 相同。22 个字典源码/页面输入最终仍匹配本次基线。未执行 Gradle 或重新构建。

使用真实 MySQL、隔离 Redis、两个生产 JAR JVM、PKCE 登录的真实 Chrome 和共享工作区 UI。业务准备与变更均走 HTTP；SQL 仅用于基础设施 DDL 和只读证据。不替换身份、不伪造响应、不直接插入通知。最终故障仅销毁真实字典 SSE 上游 socket；菜单和业务 HTTP 始终保持可用。

## 批次及准确计数

计数顺序均为 tests / failures / errors / skipped；各批不合并为一轮全绿。

| 批次 | HTTP / 环境 | Chrome | 解释 |
| --- | --- | --- | --- |
| attempt-01 | 8 / 0 / 1 / 0 | 8 / 1 / 1 / 0 | 整个订阅 JVM 停机导致菜单读取失败和权限关闭，原测试范围不当；原失败保留 |
| attempt-02 | 2 / 0 / 1 / 0 | 13 / 4 / 0 / 0 | SSE 单独断线；测试 Vite 代理未传播响应前错误，重试连接悬停 |
| attempt-03 | 2 / 0 / 1 / 0 | 6 / 2 / 1 / 0 | 定向诊断确认 Last-Event-ID 为 2，代理重试悬停；提前终止后续检查 |
| attempt-04 | 2 / 0 / 1 / 0 | **13 / 0 / 0 / 0** | 修正测试代理后全部定向业务检查完成；执行器退出异常见下文 |
| attempt-04 独立 MySQL 回读 | **1 / 0 / 0 / 0** | — | 五次生命周期通知分别持久化一次 |
| lifecycle-02 | — | **0 / 0 / 0 / 0** | 独立真实 Chrome 清理探针退出 0，耗时 15.28 秒；不计业务用例 |

attempt-01 的八项 HTTP 检查和前七项浏览器检查是真实正向证据：Cookie-only Raw SSE 与关联 ID、匿名 401、真实无权成员订阅/公开读取 403、非法游标 400 / `SUMMER_DICTIONARY_SSE_LAST_EVENT_ID`、V4/V23/V27、通知仅含失效元数据、另一 JVM 发布抵达既有连接和已打开页面、自动读取版本 2、停用选择提示重新选择、三项未提交输入保留、有效重选清除提示。复用这些已通过项不改变整批失败状态。

attempt-04 的 13 项浏览器检查覆盖：真实传输断线显示 RECONNECTING、期间周期菜单读取 200、草稿保留、原生断点重连追赶离线发布、重读版本 3 并清除警告、恢复保留草稿、整字典停用自动重读 404、不可用刷新保留草稿、回滚自动重读版本 1、回滚保留草稿、零自动业务提交、SSE URL/请求不暴露 Bearer、零未处理页面错误。CDP 记录重连 `Last-Event-ID: 2`；不能用 Playwright 简化 headers 中未出现该字段推断它没有发送。

## 原失败归因与执行器限制

主会话纠正：整 JVM 失联导致菜单权限读取失败时关闭访问是预期安全行为。撤回首轮保留旧菜单权限的修复建议；没有修改 main/menu 或放宽授权。此次只验证菜单和业务 HTTP 可用条件下的独立 SSE 故障恢复。

attempt-02/03 的悬停来自测试代理。Vite 默认代理错误处理只记录错误，没有结束下游响应。夹具现在同时传播真实上游 aborted 和响应前 error，销毁下游连接，使原生 EventSource 能够继续重试；没有制造成功响应，也没有修改生产逻辑。

attempt-04 已写出 complete=true、13 项全绿、Chrome 已退出的回执，但 Node 执行器仍存活。确认自有 PID 后对其 SIGTERM，父运行器据实记录 browserExitCode=-15、外层 error=1、退出 1。底层存活句柄原因未被独立证明，不能将其称为完整运行器绿灯。之后仅修正自有 Chrome 子进程 signalCode 判定、关闭 stdio/unref，并增加 lifecycle-only 分支；业务断言正文未改变。lifecycle-01 已完成且 Chrome 已退出，但工具截断丢失退出码；另用有界包装器执行 lifecycle-02，取得实际退出 0 和零业务计数。未重跑 13 项已绿业务矩阵。

## 七项 AC 对照

| AC | 证据 |
| --- | --- |
| 发布通知到达已打开页面 | attempt-01 跨 JVM 原生通知、自动版本 2 重读 |
| 只重读当前有权版本 | 原批拒绝 403；attempt-04 自动 3 → 不可用 404 → 回滚 1 |
| 未提交输入保留 | 正向、断线、恢复、停用、回滚均检查 JSON、范围及版本；停用选择须重新选择，零自动提交 |
| 故障可诊断、可恢复 | attempt-04 RECONNECTING → OPEN；警告消失、Last-Event-ID=2 追赶 |
| 真实迁移/数据库/HTTP/UI 与失败路径 | 原批迁移、401/403/400；实际 socket 故障；五次通知持久化回读 |
| 公共契约及敏感边界 | Raw SSE / Unified JSON、SUMMER 错误与关联 ID、仅元数据通知、Cookie 鉴权；四批敏感日志检测无命中 |
| 可追溯及保留改动 | 原失败、执行脚本、图片、摘要、独立清理回执均保存；生产改动 0，未操作真实索引、提交、推送或 Issue |

已目视检查停用选择、断线、恢复及最终回滚截图。最终截图显示 OPEN、已发布版本 1，同时保留未提交 JSON、范围 `_tenant` 和版本 7。

## 清理与复现

四批均回读确认隔离 schema、应用账号和 Redis 容器不存在，23081/23082/23083/23573/23580 无监听。Chrome 有界 SIGTERM 回收记录为 forced=true/exited=true，不称优雅退出。自有临时 profile/cache 已清理。CustomerController 仍为 `d6a830…`，用户 `.t25-dependencies.log` 保留；共享工作区其他变更不归因或回滚。

[脚本说明](../../scripts/acceptance/dictionary-notifications/README.md)提供完整及定向模式。原证据位于 `.git/summer4-closeout-20261015/t31-live/`，最终清单与交接位于 `.git/summer4-closeout-20261015/t31-fix/`。当前 browser.mjs 相对 attempt-04 仅新增生命周期探针和清理修正，README 另行更新；实际执行快照始终保留，不能声称当前全部文件与业务批执行版本逐字节相同。
