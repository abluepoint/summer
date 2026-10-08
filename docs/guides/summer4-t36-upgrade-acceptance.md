# T36 升级与水平扩展验收门禁

本文是 T36 的运行手册和验收记录模板。它把迁移、备份恢复、滚动升级、跨节点一致性、错误契约、审计、权限、缓存、实时推送和插件兼容性收敛为同一组可复核门禁。机器可读的门禁矩阵位于 [`summer4-t36-release-gates.json`](summer4-t36-release-gates.json)，可执行入口为 [`scripts/t36-upgrade-gate.sh`](../../scripts/t36-upgrade-gate.sh)。

当前隔离环境结论见文末“2026-10-07 真实隔离环境复验”；此前按日期保留的记录是历史证据，不覆盖该章节的最新结果和剩余边界。

## 需求—设计—实现—测试—缺陷—验收追踪

| 需求 | 设计决定 | 实现/证据 | 测试门禁 | 当前结论 |
| --- | --- | --- | --- | --- |
| 49：每次结构变化可回放、可核对 | Flyway 只做追加迁移；启动前 validate，迁移遵循 expand/contract | `summer-sample/src/main/resources/db/migration/mysql/`、`application.yml` | Flyway validate、迁移版本唯一性、`T36UpgradeAcceptanceHttpTest` | 以运行环境结果为准 |
| 50：备份、健康检查、兼容窗口 | 先备份再升级；旧列/新列并存，双节点共享会话和事件回放 | `t36-upgrade-gate.sh preflight/backup/restore/rolling` | 真实 MySQL dump/restore、两节点 HTTP 探针 | 本地隔离环境通过；生产目标环境未执行 |
| 51：稳定单体边界、可演进拆分 | 当前保持模块化单体；只有独立伸缩、发布、数据 ownership 或故障隔离才拆分 | `summer4-t36-release-gates.json` 的 `microserviceBoundary` | 模块边界、跨模块 API 门禁 | 当前不拆进程 |
| 52：过程可追踪 | 固定门禁矩阵、证据命名和缺陷记录 | 本文与 JSON 矩阵 | 文档契约脚本、后端真实 HTTP 测试、UI 合约测试 | 持续更新 |

## 升级前检查

在目标环境设置受控运行时配置。数据库密码、Redis 密码、主密钥、Token 和私钥只通过 Secret 或进程环境注入；不要写入命令行、脚本、日志、测试快照或本文。需要认证的 HTTP 探针从 `T36_ACCESS_TOKEN` 进程环境读取真实签发且具有 `internal.release.read` 能力的短期令牌；不要将其放入命令参数或文件。

```bash
./gradlew :summer-sample:test \
  --tests 'com.abluepoint.summer.sample.release.ReleaseGateControllerTest' \
  --tests 'com.abluepoint.summer.sample.release.T36UpgradeAcceptanceHttpTest' \
  --no-build-cache
node summer-ui/scripts/verify-t36-upgrade.mjs
./scripts/t36-upgrade-gate.sh contract
./scripts/t36-upgrade-gate.sh migrations
T36_BASE_URL=http://127.0.0.1:8080 \
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
T36_DB_USER=summer_app \
./scripts/t36-upgrade-gate.sh backup

T36_BACKUP_FILE=/secure/evidence/summer-<timestamp>.sql.enc \
T36_ALLOW_RESTORE=1 T36_RESTORE_DATABASE=summer_t36_restore \
T36_DB_HOST=127.0.0.1 T36_DB_PORT=3306 T36_DB_NAME=summer \
T36_DB_USER=summer_app \
./scripts/t36-upgrade-gate.sh restore
```

执行前由受控进程环境注入 `T36_DB_PASSWORD`、`T36_BACKUP_PASSPHRASE` 和所需的 `T36_ACCESS_TOKEN`。备份在内存中加密后才写盘；恢复在内存中解密并通过 stdin 导入新建的隔离库，拒绝覆盖已有库。备份口令独立保管，脚本只输出加密备份路径、恢复库名和表数量。当前内存方式适合本地演练，大库需要流式加密备份系统。

## 双节点滚动升级

节点 A 和 B 必须使用同一 MySQL、Redis、Flyway 版本和兼容配置；`SUMMER_SAMPLE_CACHE_NODE_ID` 每个节点唯一。按以下顺序执行：

1. 备份并执行 expand 迁移；先启动 B，确认健康和 release readiness，再摘除 A 的流量。
2. B 启动成功后，使用真实 API 验证认证会话、租户权限、统一错误信封、审计 requestId、版本化缓存和事件消费者。
3. 切回 B 后升级 A，重复同一组探针；任一节点 readiness 不为 `READY` 或响应结构不一致，立即停止滚动。
4. 两节点都通过后再执行 contract 迁移。旧 key 在 grace 期内保留公钥，旧会话由共享 Redis 索引继续可撤销，事件以 outbox/Stream receipt 去重，SSE 以 `Last-Event-ID`、WebSocket 以消息游标恢复。

```bash
T36_NODE_A_URL=http://127.0.0.1:8080 \
T36_NODE_B_URL=http://127.0.0.1:8081 \
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

- 空 schema 首次启动失败：`AuthorizationService` 初始化读取 `summer_authorization_capability` 时表不存在，失败后 schema 表数为 0；随后已通过显式 Flyway 初始化门闩修复依赖顺序，`summer-sample:compileJava` 通过。此次运行本身不计为迁移验收通过。
- 手工应用迁移后得到 52 张表；Redis 安全上下文启动曾因 `SecurityContextRepository` Bean 缺失失败，随后移除 `@ConditionalOnBean(StringRedisTemplate.class)` 的时序敏感条件并通过编译。真实节点重启验证仍待 Redis 可用环境。
- 为进一步隔离问题，临时关闭 Flyway、session index 并改用内存安全上下文后，两节点健康响应均为 HTTP 200 / UP，Quartz 均声明 JDBC clustered store。但这些覆盖不满足共享会话验收配置，也不证明任务只执行一次。
- 保持 JDBC OAuth 时浏览器授权请求返回 HTTP 400；源码显示关闭 Flyway 同时关闭配置客户端的 seed runner。再临时使用内存 OAuth，浏览器提交登录后到达工作台路由，但页面显示“认证仍不可用。请手动登录；请求不会自动重发。”，未完成登录后的关键流程。
- 两节点未认证 readiness/runtime diagnostics 均返回 HTTP 403。没有取得可用发布操作员认证，因此认证后的 `preflight`/`rolling` 门禁未完成；共享会话撤销、事件/缓存一致性、任务唯一执行和浏览器关键业务流程均未通过，禁止据此宣布 T36 全部验收完成。
- 浏览器工具可显示页面，但只读 evaluate 环境不提供 `performance`/Web Storage；对应调用失败已回读，未据此推断应用缺陷。现有浏览器自动化脚本未返回通过结果，已终止，测试计数不计为通过。
- 临时应用进程和 UI 已停止，18081/18082/5173 回读无监听；隔离 schema 已删除并回读计数为 0，`summer:t36:*` 键回读为 0，临时浏览器标签和凭据文件已清理。会话索引、缓存、Stream 部分前缀在源码中固定，未建立全域 Redis 隔离；本轮不能宣称全域 Redis 无副作用，未删除不明归属键或全库清空。

后续应先修复并验证默认配置的空库初始化和 Redis store 装配，再恢复 JDBC OAuth/shared-session 配置继续验收。内存覆盖只用于诊断，不作为发布配置或验收通过依据。

### 2026-10-05 Flyway 与 Redis 会话 Bean 生命周期修复

本轮源码诊断确认：Spring Boot 4.0.7 依赖图只有 `flyway-core` 与 `flyway-mysql`，未提供可直接复用的 `FlywayMigrationInitializer`/Flyway 自动配置类。空库启动时，自定义 `Flyway` Bean 没有依赖消费者，导致 `AuthorizationService.@PostConstruct` 先读表而失败。已在 `SampleFlywayConfiguration` 增加可选 Flyway 的显式初始化门闩，并让 `AuthorizationService` 依赖该门闩；禁用 Flyway 时门闩为空操作，保留原配置语义。

`SessionIndexConfiguration` 原先以 `@ConditionalOnBean(StringRedisTemplate.class)` 保护 Redis 会话存储，组件扫描阶段可能早于 Redis 自动配置完成，造成 `SummerSessionStore` 缺失并进一步导致 `SecurityContextRepository` 缺失。已移除该时序敏感条件，改由 `SessionIndexService` 的依赖注入在 Redis Bean 不可用时明确失败。

验证：`./gradlew :summer-sample:compileJava --no-daemon --max-workers=1` 通过。随后将 `SharedSessionHttpTest` 的 duration 属性修正为纯毫秒值；上下文可启动，1/5 通过，4/5 因 Redis 会话存储不可用返回 503，该环境阻塞与本轮 Bean 修复分开记录。

### 2026-10-05 SharedSessionHttpTest duration 属性复核

将 `SharedSessionHttpTest` 内 `timeout-ms=100ms`、`open-duration-ms=1s` 修正为契约要求的纯毫秒值 `100`、`1000`。测试上下文现可启动，`rejectsMalformedSessionReferencesBeforeRedisAccess` 通过；其余 4 项返回 `503 SUMMER_SESSION_STORE_UNAVAILABLE`，测试 XML 明确指向 Redis 会话存储不可用，未继续启动 Redis 或本地节点。

### 2026-10-05 SharedSessionHttpTest 本地 Redis 最后验证

使用本地 k8s `local-base/redis-auth` Secret 通过进程环境注入（未回显），连接 `127.0.0.1:6379` 的隔离 Redis DB 15，并设置独立 Spring Session namespace。测试上下文启动成功，5 项中 1 项通过；其余 4 项返回 `503 SUMMER_SESSION_STORE_UNAVAILABLE`。XML 仅记录脱敏后的业务错误，底层 Redis 异常被 SessionIndexService 按设计吞掉；未启动或修改 Redis，测试属性修正未提交。

### 2026-10-05 SharedSessionHttpTest username=default 最终验证

按要求以 `SUMMER_REDIS_USERNAME=default`、进程环境注入的本地 Secret 密码、`127.0.0.1:6379`、Redis DB 15 和独立 namespace 做最后一次有界运行。结果仍为 1/5 通过、4/5 返回 `503 SUMMER_SESSION_STORE_UNAVAILABLE`；未启动或修改 Redis，未提交测试属性修正。

本次父会话有效凭据注入复核：用户名为 `default`、密码通过本地 Secret 进程环境注入，结果与本地 Secret 复核一致：上下文启动，1/5 通过，4/5 返回 `503 SUMMER_SESSION_STORE_UNAVAILABLE`。凭据未写入文件或日志。

### 2026-10-05 SessionIndexService 定点诊断收口

临时脱敏日志仅确认 `RedisConnectionFailureException → RedisConnectionException → RedisCommandExecutionException` 异常链；不记录异常消息或凭据时无法进一步区分 Redis 写入、序列化、ACL 或超时。诊断源码和临时文件已恢复/清理，未形成代码修复。保留 SharedSessionHttpTest 结果：1/5 通过、4/5 为 `503 SUMMER_SESSION_STORE_UNAVAILABLE`。


### 2026-10-05 Redis 认证映射、同一会话撤销与失败关闭修复

此前 4 项 503 的实际根因是测试 `application.yml` 遮蔽主配置，导致 `SUMMER_REDIS_*` 未映射到测试连接工厂；定点检查确认用户名/密码未设置，底层错误标识为 `NOAUTH`。测试现显式声明环境变量映射，凭据仅由本地 Kubernetes Secret 注入进程环境。测试使用真实 `127.0.0.1:6379` Redis、DB 15、随机独立 namespace，每项结束只清理该 namespace；索引 namespace 可配置，默认仍为 `summer:auth`。

`RedisSummerSessionStore` 在共享记录缺失、失效、撤销或 Redis 不可用时不再恢复本机身份；保存先写共享索引，写失败清除本机上下文并传播失败。清空上下文会撤销现有共享记录，索引执行器随 Bean 销毁关闭。

原测试反复注入 `jwt()` 会改变 MockHttpSession 标识，导致撤销后实际请求使用另一会话。用例现复用已认证的 servlet 会话，并断言列表引用、撤销记录和后续请求对应同一 session id；测试方法及权限、错误码断言保留。UUID 标识避免数字 mock id 与响应时间戳偶然重合。故障测试的 long 属性也修正为纯毫秒值。

本轮定向执行（`--no-configuration-cache --no-build-cache --max-workers=1`）：

| 测试类 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| SharedSessionHttpTest（原始全部 5 项） | 5 | 0 | 0 | 0 |
| RedisSummerSessionStoreTest | 6 | 0 | 0 | 0 |
| SharedSessionRedisFailureHttpTest | 1 | 0 | 0 | 0 |
| SessionControllerTest | 3 | 0 | 0 | 0 |

范围限制：上述 HTTP 用例以已有本机 servlet 登录为夹具，证明索引管理/撤销行为；仓库单测证明失败关闭。资源服务器安全链尚未显式接入共享 SecurityContextRepository，跨节点 servlet session 恢复也尚未验证；不能据此宣布 API 全链路失败关闭、T25 或 T36 完成。双节点、JWT 独立性、浏览器与发布门禁仍待后续集成修复及实测。本轮未修改该安全链、业务数据库或原 18080 实例，未推送。

## 2026-10-07 真实隔离环境复验

本轮 **10 类本地门禁在下述范围内通过**，不代表生产发布批准或 T18/T19 全部需求完成。汇总及逐项红绿证据保存在项目内 `.git/summer4-review-20261007/t36-live/result.json`；实际使用随机 `summer_t36_*` MySQL schema、独立 Redis 容器、18081/18082 两个 JVM，以及恢复节点 18083。浏览器通过 5173/18580 完成真实用户密码登录、PKCE 和服务端签发，未伪造或注入 Token。

| 门禁 | 本轮实际证据 |
| --- | --- |
| 迁移 | 31 个文件版本均被脚本计入；真实启动执行 Flyway validate/migrate；恢复库 history 31/31 一致 |
| 配置与预检 | 携带真实短期令牌的 `preflight`、两节点 readiness 探针退出 0；暂停隔离 Redis 后返回 `NOT_READY`，不再无条件 READY |
| 密钥 | 显式选择 MySQL key store；两节点初始公钥一致；轮换后 active/grace 共 2 把公钥一致；旧令牌继续可用 |
| 权限 | 特权与受限账号跨节点 6 项 200/403 矩阵通过且均有 requestId；OAuth 仍只有原四项 scope，业务能力与组织上下文从持久化记录解析 |
| 任务 | 两节点同 key 并发触发返回 202/202，仅 1 条成功执行；失败任务尝试 3 次；实际杀掉执行 JVM 后另一节点恢复成功，1 条执行记录且 recoveryCount 为 1 |
| 缓存 | 两节点预热后，经 A 更新，B 读到新值；Redis 共享版本递增，存在真实 L2 条目 |
| 事件 | outbox 发布、两个独立节点消费组均清空 pending/lag；重复事件只有一条 processed receipt；暂态故障无需重新发布即可恢复；持续失败在第 3 次后进入死信 |
| 实时 | 浏览器 Cookie 鉴权连接 B，A 的消息经 Redis 到达 B；pong、heartbeat、游标重放及其他租户拒绝通过；任务 SSE 两节点均收到目标事件，无串任务事件，Last-Event-ID 重放通过 |
| 插件 | 真实 API 拒绝未知目录摘要，注册/启用成功；不兼容版本不可启用，回退到兼容版本后恢复；B 读取兼容诊断，受限账号 403 |
| UI | 原始 `e2e-t18-t19.mjs` 六组全部通过：策略预览、参数创建、组织/租户/基础覆盖、组织字典、无效 JSON 零写入、真实 403/requestId；tests=6、failures=0、errors=0、skipped=0 |

备份恢复还核对了密钥、角色绑定、角色、Capability、本地用户、OAuth 客户端、OAuth 授权及 Flyway history 八类表计数；恢复节点真实 PKCE 登录及原有令牌访问均为 200。包含 OAuth 二进制列的内存 dump 使用 `--hex-blob`，从未把明文 dump 写盘。运维脚本的加密备份/恢复另经真实 MySQL 验证：加密文件无明文 SQL、关键表计数一致、已有目标库被拒绝覆盖，临时备份已删除。

滚动演练使用两个真实不同的本地开发制品：旧 JAR SHA-256 `546510c7646131b34fbb5fa5e8088074de913a4e919dac0f74862a4363dc9b20`，新 JAR SHA-256 `912d4bc5b3616c80ea111565aee8cb06db926038f64d2efbbc8f47aae4e8be03`。两者混合运行时授权读取成功；停 A 后 B 继续服务，A 升级后旧令牌仍可使用。每次最终演练都先冻结独立 JAR，避免构建期间替换运行制品。此证据不覆盖历史生产版本或破坏性 schema 变更。

本轮修复：

- 本地身份业务声明在登录及 OAuth 签发时读取持久化角色、显式拒绝与组织上下文；保持 OAuth 可申请 scope 不变。
- 用户角色绑定改写合法 JSON 数组，兼容读取原文本投影，修复 MySQL 真实绑定 API 500。
- readiness 查询真实运行前提；`gates:validate` 对未执行的外部门禁返回 `NOT_VERIFIED`，不伪报执行通过。
- Quartz 作业启用 recovery，并在启动时升级已有作业的恢复标志；任务 SSE 按任务读取已提交事件，实现跨节点推送与游标重放。
- 授权事件先重读本节点 pending，再读取新事件；死信终态不再被另一节点继续执行，显式操作员重试可重置死信 receipt。
- MySQL 原生查询采用 `hibernate.query.native.prefer_jdbc_datetime_types=true`，保留 JDBC Timestamp 到原有 Instant DTO 的转换，并明确 JDBC/Hibernate UTC 配置；真实 MySQL 测试覆盖非空列表和时间正确性。
- 补齐 `/dictionary/form` 的页面入口，复用现有字典页面。
- 发布脚本支持进程环境中的短期令牌，正确计入含下划线的 Flyway 版本；备份加密后才写盘，恢复只允许新建隔离目标库。

后端本轮综合定向回归实际执行 50 项，全部 0 failures/errors/skipped；终态保护新增 2 项测试通过，并重跑授权事件 HTTP 测试通过。Security 模块原有 12 项本轮输入未变，Gradle 复用既有结果，不算新增执行数。前端 typecheck/build 通过；构建仍提示既有 Browserslist 数据陈旧、eval 与大 chunk 警告。所有 Gradle 命令均使用共享 `fcntl` 执行锁。

剩余边界：固定 `ent-acme` 的通用指定租户能力仍未完成；平台菜单 GET 请求返回 400（当前平台 controller 只有 PUT），不在上述六组页面断言范围内；任务进度在执行事务提交后推送，不证明执行中的增量进度或外部业务副作用恰好一次；插件验证是内置可信目录摘要匹配，不是第三方二进制签名验证。没有执行生产部署、推送或关闭 Issue。

最终三个冻结制品演练均已清理自己的 schema/恢复库、Redis 容器和监听端口，未操作原 18080 或业务库。本聊天未执行暂存或提交；共享索引相较初始快照新增 10 项条目，具体行为者未核实，已保留原样并写入 `index-final-audit.json`。临时诊断 runner 已从工作区删除，其已存在的暂存快照未回写；整合时按 `own-manifest.json` 的明确文件清单审阅，排除诊断 runner 与日志，勿统一暂存或复活旧迁移。
