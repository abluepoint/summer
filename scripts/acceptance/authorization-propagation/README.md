# T20 授权变更传播实际验收

> 2026-11-20：授权事件页面、列表/重试和授权解释接口已退役，见 [ADR 0041](../../../docs/adr/0041-retire-authorization-event-management.md)。本目录涉及旧页面或 `/api/authorization-events` 的脚本保留为历史验收材料，不能用于当前版本验收；内部事件写入、事务一致性与投递测试继续保留。

从仓库根目录执行，使用本地 Kubernetes `docker-desktop/local-base` 的 MySQL、
独立临时 Redis、两个实际 Spring Boot JVM 和实际 Chrome。复用
`../task-configuration/environment.py` 的凭据注入、随机 schema/账号及清理逻辑；
不对共享业务库执行写入，不从命令行传密码，不使用模拟 HTTP 响应。

先提供已冻结且摘要匹配的候选制品；每次使用新的证据目录，保留原失败批次。

```sh
export SUMMER_TASK_CONFIGURATION_CANDIDATE=/absolute/path/to/candidate.jar
export SUMMER_TASK_CONFIGURATION_CANDIDATE_SHA256=<verified-sha256>
export SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t20-live/new-unique-attempt"
python3 scripts/acceptance/authorization-propagation/run.py
```

候选输入变量以公共环境脚本的 `immutable_candidate()` 校验为准。需要 Java 21、
Node 22、Docker、kubectl 和当前用户可用的实际 Chrome；固定前后端测试端口为
26081、26082、26083、26573、26580，已被占用时停止，不终止未知进程。

完整执行覆盖实际接口扫描、持久角色授权、节点间撤权、事件重复、pending 恢复、
消息丢失、Redis 数据丢失后恢复、接口权限与数据策略、非法策略、outbox 写入故障
回滚以及停留页面的菜单和动作更新。业务变更通过正式 HTTP API；故障注入只重命名
本批独立 schema 的 outbox/receipt 表，finally 恢复后清理。Redis 固定本批宿主端口，
停机后启动同一独立容器，不依赖随机端口或已被 `--rm` 删除的容器。

可以设置 `SUMMER_T20_PHASE` 只补尚未覆盖的阶段，避免重复绿色矩阵：

| 值 | 执行范围 |
| --- | --- |
| 不设置 | 全部阶段 |
| `backend` | 初始化、Redis 恢复、接口/数据策略及事务故障；不运行 Chrome |
| `redis` | 初始化与 Redis 恢复；不运行策略、事务或 Chrome |
| `ui` | 初始化与实际 Chrome；不运行后端故障矩阵 |
| `remainder` | 初始化、Redis、策略、事务、Chrome；跳过此前跨节点/重复/pending/丢失阶段 |

`SUMMER_T20_PHASE=ui` 时，可额外设置 `T20_BROWSER_MODE=transport`，验证未变化的
权限轮询保留草稿、实际权限 HTTP 响应一直悬停时超时关闭旧访问、解除阻塞后恢复。
代理只延迟实际上游响应，不伪造权限数据。默认 Chrome 流程通过节点 A 实际撤销
动作及禁用菜单，再观察节点 B 页面无需重载地拒绝并恢复。

每批 `result.json` 保留 tests/failures/errors/skipped，Chrome 有独立计数与关闭回执；
检查数包含初始化，不跨批相加。`cleanup.json` 验证 schema、账号、Redis 和端口清理。
`executed-scripts` 保存当批脚本与前端入口，新增批次还保存菜单 API。Token、密码
只在进程内使用；原失败不删除，也不把 fixture 错误记为产品缺陷。
