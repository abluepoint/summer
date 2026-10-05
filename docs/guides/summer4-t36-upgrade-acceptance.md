# T36 升级与水平扩展验收门禁

本文是 T36 的运行手册和验收记录模板。它把迁移、备份恢复、滚动升级、跨节点一致性、错误契约、审计、权限、缓存、实时推送和插件兼容性收敛为同一组可复核门禁。机器可读的门禁矩阵位于 [`summer4-t36-release-gates.json`](summer4-t36-release-gates.json)，可执行入口为 [`scripts/t36-upgrade-gate.sh`](../../scripts/t36-upgrade-gate.sh)。

## 需求—设计—实现—测试—缺陷—验收追踪

| 需求 | 设计决定 | 实现/证据 | 测试门禁 | 当前结论 |
| --- | --- | --- | --- | --- |
| 49：每次结构变化可回放、可核对 | Flyway 只做追加迁移；启动前 validate，迁移遵循 expand/contract | `summer-sample/src/main/resources/db/migration/mysql/`、`application.yml` | Flyway validate、迁移版本唯一性、`T36UpgradeAcceptanceHttpTest` | 以运行环境结果为准 |
| 50：备份、健康检查、兼容窗口 | 先备份再升级；旧列/新列并存，双节点共享会话和事件回放 | `t36-upgrade-gate.sh preflight/backup/restore/rolling` | 真实 MySQL dump/restore、两节点 HTTP 探针 | 需要目标环境执行 |
| 51：稳定单体边界、可演进拆分 | 当前保持模块化单体；只有独立伸缩、发布、数据 ownership 或故障隔离才拆分 | `summer4-t36-release-gates.json` 的 `microserviceBoundary` | 模块边界、跨模块 API 门禁 | 当前不拆进程 |
| 52：过程可追踪 | 固定门禁矩阵、证据命名和缺陷记录 | 本文与 JSON 矩阵 | 文档契约脚本、后端真实 HTTP 测试、UI 合约测试 | 持续更新 |

## 升级前检查

在目标环境设置受控运行时配置。数据库密码、Redis 密码、主密钥、Token 和私钥只通过 Secret 或进程环境注入；不要写入命令行、脚本、日志、测试快照或本文。需要认证的 HTTP 探针使用权限为 `internal.release.read` 的受保护 curl 配置文件，通过 `T36_CURL_CONFIG` 指定其路径；脚本不会读取或打印该文件内容。

```bash
./gradlew :summer-sample:test \
  --tests 'com.abluepoint.summer.sample.release.ReleaseGateControllerTest' \
  --tests 'com.abluepoint.summer.sample.release.T36UpgradeAcceptanceHttpTest' \
  --no-build-cache
node summer-ui/scripts/verify-t36-upgrade.mjs
./scripts/t36-upgrade-gate.sh contract
./scripts/t36-upgrade-gate.sh migrations
T36_BASE_URL=http://127.0.0.1:8080 \
T36_CURL_CONFIG=/path/to/protected-curl.conf \
./scripts/t36-upgrade-gate.sh preflight
```

`preflight` 同时检查 `/actuator/health`、`/internal/v1/release/readiness` 和 `/internal/v1/diagnostics/runtime`。后两者返回统一响应并带 `X-Request-Id`；运行诊断只读取组件状态和指标数量，不保存正文。权限不足或依赖不可用必须失败关闭；不能用 HTTP 200 或静态文件存在替代健康结论。

`migrations` 在连接数据库前检查 MySQL Flyway 文件名中的版本号唯一性；随后仍必须在目标数据库执行 Flyway validate/migrate 并回读 history。若重复版本已经写入目标库 history，必须停止发布并按迁移回滚流程处理；若重复仅来自尚未应用的并行迁移，则必须先确认目标库没有对应 history，再以追加迁移的方式永久分配唯一版本号并重新执行门禁。

## 迁移、备份与恢复

1. 在发布窗口外对目标 MySQL 做一致性备份。脚本使用 `--single-transaction`、routines、events、triggers 和 `--no-tablespaces`；备份文件必须非空，并由发布系统保存摘要和保留期。
2. 运行 `preflight`，确认 Flyway validate、数据库、Redis、审计和运行时配置均可用。生产 `ddl-auto` 必须为 `none`，Flyway 必须启用并使用 `validate-on-migrate`。
3. 先发布 expand 迁移（新增列、表、索引或兼容视图），让旧节点仍能读写；观察一整个兼容窗口后再切换代码使用新字段。
4. 使用隔离库名演练恢复。`restore` 强制要求 `T36_ALLOW_RESTORE=1`，且目标库名必须以 `_t36_restore` 结尾，避免误覆盖业务库。恢复后至少核对表数量、Flyway history、关键 Customer、角色/Capability、会话索引和审计记录。
5. contract 阶段只能在确认所有节点和消费者已升级后执行；删除旧列前先保存新的可恢复备份。任何迁移失败都停止发布，不把部分成功当作完成。

```bash
T36_DB_HOST=127.0.0.1 T36_DB_PORT=3306 T36_DB_NAME=summer \
T36_DB_USER=summer_app T36_DB_PASSWORD='(由受控环境注入)' \
./scripts/t36-upgrade-gate.sh backup

T36_BACKUP_FILE=/secure/evidence/summer-<timestamp>.sql \
T36_ALLOW_RESTORE=1 T36_RESTORE_DATABASE=summer_t36_restore \
T36_DB_HOST=127.0.0.1 T36_DB_PORT=3306 T36_DB_NAME=summer \
T36_DB_USER=summer_app T36_DB_PASSWORD='(由受控环境注入)' \
./scripts/t36-upgrade-gate.sh restore
```

上面的占位文本只表示由 Secret 注入，不是可提交或可复制的凭据。脚本输出仅包含备份路径、恢复库名和表数量。

## 双节点滚动升级

节点 A 和 B 必须使用同一 MySQL、Redis、Flyway 版本和兼容配置；`SUMMER_SAMPLE_CACHE_NODE_ID` 每个节点唯一。按以下顺序执行：

1. 备份并执行 expand 迁移；先启动 B，确认健康和 release readiness，再摘除 A 的流量。
2. B 启动成功后，使用真实 API 验证认证会话、租户权限、统一错误信封、审计 requestId、版本化缓存和事件消费者。
3. 切回 B 后升级 A，重复同一组探针；任一节点 readiness 不为 `READY` 或响应结构不一致，立即停止滚动。
4. 两节点都通过后再执行 contract 迁移。旧 key 在 grace 期内保留公钥，旧会话由共享 Redis 索引继续可撤销，事件以 outbox/Stream receipt 去重，SSE 以 `Last-Event-ID`、WebSocket 以消息游标恢复。

```bash
T36_NODE_A_URL=http://127.0.0.1:8080 \
T36_NODE_B_URL=http://127.0.0.1:8081 \
T36_CURL_CONFIG=/path/to/protected-curl.conf \
./scripts/t36-upgrade-gate.sh rolling
```

该命令只比较健康状态和 release readiness；缓存键收敛、Quartz 任务唯一执行、Redis Stream receipt、WebSocket/SSE 重放和会话撤销仍须由真实集成测试及运行记录补充，不能把两个 HTTP 200 当作完整双节点证明。

## 错误码、审计、权限、缓存与实时推送

| 领域 | 成功路径 | 失败/越权路径 | 必须保留的证据 |
| --- | --- | --- | --- |
| 迁移/配置 | readiness `READY` | Flyway 校验或配置不兼容时停止发布 | 迁移版本、配置来源和 requestId |
| 密钥 | active kid 签发、旧 grace kid 验签 | 主密钥/引用不可用时拒绝写操作 | kid、版本、状态和脱敏审计 |
| 权限 | Capability 与租户/组织范围匹配 | 无 scope 返回 `403 SUMMER_FORBIDDEN` | 主体、tenant、组织、资源、结果 |
| 业务错误 | `ApiResponse` 的 `data` | `SUMMER_*` 框架码或业务码、校验 violations | `X-Request-Id` 与错误 envelope |
| 审计 | 事务成功后写入事件 | 写入失败不伪造成功 | action、resource、主体、结果、requestId；无密钥正文 |
| 缓存 | Caffeine L1/Redis L2 命中 | 版本不可读时安全绕过或失败关闭 | tenant/version/node、失效 receipt |
| 任务 | Quartz 集群锁、幂等执行 | 重复触发、超时、未知结果可重试但不重复副作用 | execution、attempt、node、状态 |
| 实时 | WebSocket 握手、SSE 心跳与回放 | 未授权/跨租户/游标过期返回拒绝或 resync | 连接主体、tenant、cursor、关闭原因 |
| 插件 | trusted digest 与兼容版本 | 未注册、摘要不匹配或不兼容时禁止启用 | 版本、摘要、诊断和审计 |

框架错误必须使用 `SUMMER_` 命名空间；业务错误不得占用该命名空间。协议端点保持 Raw response，业务 JSON 使用统一响应；日志和测试快照只保留脱敏摘要。

## Summer UI 结果与真实流程

T36 不新增第二套前端工程或 mock 后端。Summer UI 的真实路由、Capability 菜单、统一错误映射、参数/字典发布回滚、任务/消息/插件页面由现有合约脚本和真实后端测试覆盖。`verify-t36-upgrade.mjs` 检查门禁矩阵、发布 API、运行配置和相关页面路由；最终浏览器验收仍需在 Mock 关闭、真实 Spring 和真实 MySQL/Redis 上执行，并记录成功、无权限、无效输入和依赖故障路径。

## 微服务边界

当前交付保持模块化单体：MySQL 是权威数据 owner，Redis 负责短期会话、缓存、事件传播和协调，Summer UI 是独立前端。只有出现独立伸缩、独立发布节奏、清晰数据 ownership 或故障隔离需求时，才为 capability 划分进程边界；拆分前必须补充 API 版本、事件 schema、审计主体传播、租户上下文、幂等、迁移 owner 和回滚策略。T36 不交付生产 Kubernetes 清单，也不引入跨服务分布式事务。

## 回滚方案

回滚顺序固定为：停止发布写入 → 保留现场与 requestId → 切回上一应用版本 → 维持 expand 兼容结构 → 恢复必要的配置/路由 → 验证健康、登录会话、权限、审计、缓存和实时重放 → 再决定是否恢复数据库备份。只要新代码已经写入新字段，禁止直接执行破坏性反向迁移；数据库回滚必须由经过演练的备份恢复或兼容反向迁移完成。密钥轮换保留 grace 公钥，事件/任务按 receipt 和 execution 状态恢复，插件按 trusted digest 回退。`rollback:dry-run` 只产生计划，不改变当前版本。

## 证据与未完成项

每次运行将以下内容放入受控证据目录：命令版本、提交哈希、迁移 history 摘要、备份摘要、恢复库表数、两节点 URL（不含凭据）、HTTP 状态/错误码/requestId、测试计数以及失败/跳过原因。当前仓库提交只包含脚本、矩阵、文档和验收测试；真实 MySQL/Redis 双节点、备份恢复和浏览器流程必须在目标环境执行后填写结果，不能由本地 H2、零用例或静态脚本替代。

## 2026-10-05 执行记录

- `T36UpgradeAcceptanceHttpTest`：2 tests，failures=0，errors=0，skipped=0。
- `ReleaseGateControllerTest`：11 tests，failures=0，errors=0，skipped=0；合并运行共 13 tests 通过。
- Summer UI `npm test`：19 tests，failures=0，errors=0，skipped=0；`npm run typecheck` 和 `npm run build` 通过。
- `node summer-ui/scripts/verify-t36-upgrade.mjs`、`scripts/t36-upgrade-gate.sh contract`：通过。
- `scripts/t36-upgrade-gate.sh migrations`：通过，26 个版本号唯一。经确认目标 schema 不存在且没有可回读的 Flyway history 后，将三组未应用重复版本永久改为 `V6_1`、`V23_1`、`V26_1`；同时修正 MySQL 9.7 不接受的多列 `ALTER TABLE ... IF NOT EXISTS` 语法和 `TEXT` 默认值定义。
- 隔离 MySQL 演练：在临时 `summer_t36_source` schema 顺序执行 26 个迁移，得到 52 张表；插入合成 Customer 行后使用 `mysqldump --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF` 恢复到 `summer_t36_restore`，恢复后仍为 52 张表且合成行可读，随后删除两个隔离 schema。首次不关闭 GTID 的恢复按预期被 MySQL 拒绝，未触碰既有业务 schema。
- T32 回归：业务字典发布/禁用/回滚的 404 保持业务错误，不再被 resilience fallback 改写为 503；字典 HTTP 与 SSE 测试共 6 tests 通过。`ResilienceDiagnosticsHttpTest` 2 tests、`ResilientReadServiceTest` 2 tests 通过；后者将 fallback 断言改为按 operation 名称选择，避免依赖排序位置。
- `preflight`、`backup`、`restore`、`rolling` 的真实双节点 HTTP 流程仍需目标环境执行；本轮已完成隔离 MySQL 迁移及备份恢复演练，未使用或输出任何凭据。


## 2026-10-05 本地双节点与浏览器实测（未通过）

本轮使用本项目源码、本地 Kubernetes MySQL 9.7.1/Redis、隔离 schema `summer_t36_runtime`、临时节点端口 18081/18082，以及关闭 Mock 的 Summer UI 5173。原有 18080 实例未修改或停止。

- 空 schema 默认启动失败：`AuthorizationService` 初始化读取 `summer_authorization_capability` 时表不存在，失败后 schema 表数为 0。尚未定位 Flyway 与业务初始化的依赖顺序；不能将这次尝试记为 Flyway 自动迁移通过。
- 手工应用迁移后得到 52 张表；使用 Redis 安全上下文配置启动仍失败，`LocalConsoleAuthenticationConfiguration` 注入所需的 `SecurityContextRepository` Bean 不存在。源码中 Redis store 的 `@ConditionalOnBean(StringRedisTemplate.class)` 装配时序是待验证线索。
- 为进一步隔离问题，临时关闭 Flyway、session index 并改用内存安全上下文后，两节点健康响应均为 HTTP 200 / UP，Quartz 均声明 JDBC clustered store。但这些覆盖不满足共享会话验收配置，也不证明任务只执行一次。
- 保持 JDBC OAuth 时浏览器授权请求返回 HTTP 400；源码显示关闭 Flyway 同时关闭配置客户端的 seed runner。再临时使用内存 OAuth，浏览器提交登录后到达工作台路由，但页面显示“认证仍不可用。请手动登录；请求不会自动重发。”，未完成登录后的关键流程。
- 两节点未认证 readiness/runtime diagnostics 均返回 HTTP 403。没有取得可用发布操作员认证，因此认证后的 `preflight`/`rolling` 门禁未完成；共享会话撤销、事件/缓存一致性、任务唯一执行和浏览器关键业务流程均未通过，禁止据此宣布 T36 全部验收完成。
- 浏览器工具可显示页面，但只读 evaluate 环境不提供 `performance`/Web Storage；对应调用失败已回读，未据此推断应用缺陷。现有浏览器自动化脚本未返回通过结果，已终止，测试计数不计为通过。
- 临时应用进程和 UI 已停止，18081/18082/5173 回读无监听；隔离 schema 已删除并回读计数为 0，`summer:t36:*` 键回读为 0，临时浏览器标签和凭据文件已清理。会话索引、缓存、Stream 部分前缀在源码中固定，未建立全域 Redis 隔离；本轮不能宣称全域 Redis 无副作用，未删除不明归属键或全库清空。

后续应先修复并验证默认配置的空库初始化和 Redis store 装配，再恢复 JDBC OAuth/shared-session 配置继续验收。内存覆盖只用于诊断，不作为发布配置或验收通过依据。
