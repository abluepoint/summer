# T18/T19 implementation and acceptance evidence

> Current backend update: see **2026-10-15 通用租户与覆盖独立版本** below. Earlier fixed-tenant/no-override-history statements are historical boundaries superseded by ADR 0031; they are not the current backend contract. Browser and deployed multi-node acceptance remain separate.

2026-10-05. Requirements: GitHub #108, #125, #126, #127 (cached authoritative issue bodies/comments in `.git/summer4-review-20261005/`). Review baseline: `76dbed5`; handoff HEAD: `be9520b`; concurrent frontend commit advanced the shared branch to `cb0a989`. This session created no commit and did not push or close issues.

## Requirement, implementation and verification

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| T18 same condition semantics in preview, SQL and Repository | DataScopeExpression retains values, EQ/NE/IN/NOT_IN, nested AND/OR, branch-gated DENY and tenant boundary; organization descendants are expanded from metadata | DataScopeEnforcementHttpTest: 9 tests, including SQL list/count, JPA detail/write, organization descendants, injected-looking literal values and unknown operators |
| T18 persisted role allow and user deny | V29 + DataScopeBindingEntity/Repository; active roles come from durable role/user assignments; USER deny is subtracted last; preview rejects hypothetical authority inputs | Persisted role/user cases in DataScopeEnforcementHttpTest and updated DataScopeCompositionHttpTest; checksum/version/audit evidence |
| T19 public marker and current subject | PublicSystemParameterController, scoped runtime reads, dictionary type/item scope before snapshot projection, managed interface decisions on public endpoints | AuthorizedConfigurationHttpTest: public/private, anonymous SELF denial, different creators/items, persisted interface denial and explicit foreign-tenant membership |
| T19 separate actions | Stable ResourcePermission codes; per-action scopes; publish/disable checks on fields that change runtime/public state | Creation does not grant update/publish; update cannot smuggle disable; legacy published dictionaries and create-as-public parameters require publish |
| T19 tenant/organization override priority | V30 + ConfigurationOverrideService; organization > tenant > base; inaccessible/private/disabled selected override returns 404; replacement uses expectedVersion | Parameter and dictionary override cases, stale version 409, invalid/invisible option 400; writes emit CONFIGURATION_OVERRIDE_PUBLISHED audit without values |
| T19 UI authorization result | Parameter/dictionary pages show permission errors and requestId, clear stale server data after denied reads, expose override publication; data-scope page edits nested trees and persists bindings | TypeScript typecheck, production build and existing npm contract tests pass; browser flow not run |
| Real MySQL migration and authorization | ConfigurationMySqlHttpTest creates a random summer_t19_ schema, applies all migrations including V29/V30, calls production HTTP handlers, verifies override and persisted denial | 1 executed test, no skip; schema dropped and absence checked; credential loaded in memory from local-base/mysql-auth |

## Test counts

Final broad run contains 37 tests / 0 failures / 0 errors / 0 skipped:

- DataScopeEnforcementHttpTest 9; DataScopeHttpTest 2; DataScopeCompositionHttpTest 2.
- AuthorizedConfigurationHttpTest 8; ConfigurationMySqlHttpTest 1.
- SystemParameterQueryHttpTest 2; SystemParameterHttpTest 2; SystemParameterReleaseHttpTest 2.
- DictionaryHttpTest 3; DictionaryVersionHttpTest 3; DictionaryNotificationSseHttpTest 3.

Summer UI npm test: 19 tests / 0 failures / 0 skipped. Typecheck and build pass; build reports the existing large-chunk advisory. Java HTTP tests use the actual Spring application and MockMvc (not a live browser or external-socket login test). Gradle runs were serial with max-workers=1; no source was neutralized to bypass a failure.

Machine-readable counts and logs: `.git/summer4-review-20261005/t18-t19-final-results.json`, `t18-t19-verified.log`, `t18-t19-ui-tests.log`, `t18-t19-typecheck-final.log`, `t18-t19-ui-build.log`. Review file manifest: `t18-t19-files.txt` in that directory. These local evidence files are not intended for source commits.

## Defects found and corrected

1. Inherited T18 implementation returned 503 for a scope-hidden parameter detail because not-found was raised inside the existing resilience wrapper. Scope validation now runs before that wrapper; out-of-range remains 404. ResilientReadService was not edited.
2. Preview accepted request-provided roleAllows/userDenies rather than authoritative bindings. Replaced with persisted binding resolution shared by real queries; supplied hypothetical authority is rejected.
3. Public dictionary reads ignored the principal and item scope. Public routes now carry Authentication, use scoped database selections and check managed interface denial before any cached result.
4. Creation/update scopes could change publication/activation through body flags or the legacy immediate-public representation. Those paths now require the appropriate publication/disable action.
5. New dictionary override input errors initially used INVALID_STATE (409). They now use the invalid-input category (400); the regression is retained.

## Remaining acceptance and boundaries

- Browser login and interactive success/denied/invalid-input flows for the three updated UI pages have not been executed. Do not close #126/#127 as fully accepted on the basis of these build/HTTP results alone.
- This sample configuration domain remains fixed to ent-acme. Organization/tenant overrides and foreign membership denial are verified; arbitrary enterprise provisioning and cross-tenant default hierarchies are not implemented or claimed.
- Overrides have optimistic replacement and audit; independent override revision history/deletion/rollback is not implemented. Base parameter/dictionary publish, disable and rollback remain available and separately authorized.
- Existing legacy *.write scopes remain all-write-actions grants. Deployments wanting action separation must grant specific action scopes/capabilities instead; managed explicit denies still take precedence.
- T35 PluginService direct database reads and T32 failure/fallback behavior remain assigned to later work. Identity/session/security auto-configuration and WebSocket proxies were not modified by this session. No live multi-node or Redis outage acceptance is claimed.
- The real shared index was changed externally during the frontend commit. This session did not stage, reset, clean, commit or rewrite the index; all pre-existing changes remain. The manifest contains inherited T18 files as well as this session's additions, not a safe blanket staging list.

## 2026-10-07 后端整合与原需求边界复核

整合以已包含 T25 共享会话修复的 `5892b66` 为父基线。逐文件比较 HEAD、真实索引和工作树；原 39 文件与 T32/T35 的 14 文件去重后，6 个前端文件由 Sol 后续修改，本提交只接入 45 个后端/文档文件。另纳入 `SystemParameterReleaseHttpTest` 已有编译修正：正确的 SecurityMockMvc import 和五处 Java 文本块换行，不改断言。共 46 文件，采用独立临时 index / blob / commit-tree，真实 index 字节保留，旧迁移与日志不进入新树。身份、安全链、会话、前端及 T36 初始化变更不由本次提交修改。

原需求依据为 #126、#127 及其父方案 #108 的故事 5–8、29–33，不能用本次实现自行缩小验收范围：

- **固定租户是真实功能范围缺口。** #126 要求指定用户和租户的有效范围预览；#127 要求租户/组织覆盖，#108 故事 29 要求同一平台按租户隔离。当前实现只支持 ent-acme 内组织覆盖及租户覆盖，并拒绝其他租户；它证明这个 Sample 租户的边界，不能证明任意租户可配置/预览，也不是原需求明确授权的完整替代。对应票据不能据此标全部完成。
- **独立覆盖回滚尚未实现。** #127 明确要求回滚操作分别授权，当前基础参数/字典的既有回滚接口已接入单独权限。原票没有单列覆盖记录独立修订历史或独立回滚入口，因此本轮不据此扩展实现；已有覆盖乐观替换不等于覆盖历史回滚，后续若按“全部配置可回滚”口径验收，仍需补齐这个能力。
- T32 的安全降级和 T35 的受控插件读取已在后续修复中接入，之前的“后续工作”条目是 10 月 5 日历史状态，最新证据见 [T32/T35 记录](summer4-t32-t35-acceptance.md)。
- 新安全链下的提交前定向复验结果见该记录。真实浏览器、MySQL/Redis 双节点和综合发布门禁仍由 T36 单独验收；之前 H2/MockMvc 或 UI 组件通过不能替代它们。

逐文件 HEAD/index/worktree 摘要、归属、提交文件清单及最终哈希存于本地 `.git/summer4-review-20261007/backend-integration/`。该目录不纳入提交；6 个前端文件和共享未提交改动保留给原负责人。

### 下一阶段待办：支持通用指定租户（未完成，本次不实现）

需求来源：[T18 #126](https://github.com/abluepoint/summer/issues/126) 的“管理端可预览指定用户和租户的最终有效范围”；[T19 #127](https://github.com/abluepoint/summer/issues/127) 的“租户/组织覆盖遵循明确优先级并写审计”；[方案 #108](https://github.com/abluepoint/summer/issues/108) 故事 29–33 的租户隔离、持久化查询范围及默认拒绝。下一阶段必须先接续 T36 已验证的真实授权与组织上下文，不能仅删常量校验或信任任意 tenantId 请求字段。

以下为已从当前代码核对的影响文件，Java 路径均以 `summer-sample/src/main/java/com/abluepoint/summer/sample/` 为前缀；列出的共享身份/组织接缝须由下一阶段统一协调，本轮没有修改：

| 影响面 | 精确现有文件 | 要处理的契约 |
| --- | --- | --- |
| 租户授权与有效预览 | `authorization/AuthorizationService.java`、`authorization/DataScopeService.java`、`authorization/DataScopeController.java`、`authorization/DataScopeModels.java` | 移除固定企业条件前，统一解析并验证目标企业、活动成员、能力及该企业内组织上下文；目标用户 preview、绑定、审计必须使用同一企业。DataScopePolicyRepository、DataScopeBindingRepository 已有企业键，保留并回归隔离 |
| 参数入口和读写 | `systemparameter/SystemParameterController.java`、`systemparameter/PublicSystemParameterController.java`、`systemparameter/SystemParameterService.java`、`systemparameter/SystemParameterQueryService.java`、`systemparameter/SystemParameterQuery.java`、`systemparameter/SystemParameterRepository.java`、`systemparameter/PublishedParameterReader.java` | 经过验证的租户贯穿路径/查询、列表/count/detail、冲突检查、公开入口及插件 facade；不能从请求任意换租户，也不能以全局 key 查询存在性 |
| 参数持久化、版本与事件 | `systemparameter/SystemParameterEntity.java`、`systemparameter/SystemParameterVersionEntity.java`、`systemparameter/SystemParameterVersionRepository.java`、`systemparameter/SystemParameterReleaseService.java`、`systemparameter/SystemParameterEventService.java`、`systemparameter/SystemParameterEventController.java`、`systemparameter/SystemParameterEventOutboxRepository.java` | 构造器不再写死租户；同 key 可属于不同租户；历史、发布/回滚、事件列表/重试和幂等查询按目标租户。事件 payload/失效使用实体租户 |
| 字典入口和持久化 | `dictionary/DictionaryController.java`、`dictionary/DictionaryService.java`、`dictionary/DictionaryVersionService.java`、`dictionary/DictionaryRepositories.java`、`dictionary/DictionaryTypeEntity.java`、`dictionary/DictionaryItemEntity.java`、`dictionary/PublishedDictionaryReader.java` | 类型/code、项/value、发布快照、覆盖候选和冲突查询必须带租户；实体的全局唯一 code/项约束需同步调整 |
| 覆盖、降级和插件 | `configuration/ConfigurationActionAccess.java`、`configuration/ConfigurationOverrideService.java`、`resilience/ConfigurationFallbackGuard.java`、`resilience/ResilientReadService.java`、`plugin/PluginController.java`、`plugin/PluginService.java` | 当前租户流入动作判定、组织>租户>基础值优先级、实时降级 proof；缓存/fallback 键不能跨租户。插件路径 enterpriseId 必须与配置读取、权限和审计一致 |
| 审计、通知与 SQL | `customer/CustomerAuditService.java`、`dictionary/DictionaryAuditService.java`、`dictionary/DictionaryNotificationService.java`、`dictionary/DictionaryNotificationController.java`；`summer-sample/src/main/resources/sql/customer-queries.sql` | 修正审计/通知固定租户；参数模板和 JPA 使用同一验证后租户；SSE/replay/outbox/缓存失效按目标租户隔离 |
| 数据库升级 | `systemparameter/SystemParameterEntity.java`、`dictionary/DictionaryTypeEntity.java`、`dictionary/DictionaryItemEntity.java`；新增 `summer-sample/src/main/resources/db/migration/mysql/` 下迁移（实施前再分配版本） | 参数当前 parameter_key 全局唯一，字典 code 全局唯一、item 外键只按 code；需 expand/contract 改为租户复合唯一/引用，回填并核验旧 ent-acme 数据。不得修改已执行的 V3/V4 历史迁移或复活旧重编号迁移 |
| 前端后续对接（交给前端负责人） | `summer-ui/src/pages/data-scopes/api.ts`、`summer-ui/src/pages/data-scopes/index.tsx`、`summer-ui/src/pages/system-parameters/api.ts`、`summer-ui/src/pages/system-parameters/index.tsx`、`summer-ui/src/pages/dictionary/api.ts`、`summer-ui/src/pages/dictionary/index.tsx` | API 使用已验证活动租户，切换租户时清理旧列表/预览/历史/覆盖数据并保留正确脏表单提示；接续 T36 真实 claim/组织修复后再实施 |

最低验收矩阵（沿用现有测试类并新增真实双租户场景，不用 H2 替代 MySQL/Redis/浏览器）：

1. `DataScopeEnforcementHttpTest` / `DataScopeCompositionHttpTest`：两个活动租户、同一主体不同角色/组织、指定目标用户预览；租户 A 的 role allow、USER deny、组织树和 policy 缺失均不影响 B，preview/SQL/JPA 同结果。伪造、非成员、停用成员、跨企业组织全部拒绝。
2. `AuthorizedConfigurationHttpTest` / `SystemParameterQueryHttpTest` / `DictionaryVersionHttpTest`：两租户同 key/code/value 可以共存；列表、总数、详情、更新、冲突/404、历史、发布、停用、回滚均隔离。公开入口必须具有明确租户语义，未知/无上下文不能默回 ent-acme；匿名、带身份公开读均校验当前策略。
3. `ConfigurationMySqlHttpTest`：真实 MySQL 隔离 schema 从旧迁移升级，保留旧行、版本、外键和 outbox；新增同名另一租户数据并验证复合唯一键，分别测试组织>租户>基础值与隐藏高优先级覆盖不回退。备份/恢复回读交 T36 门禁。
4. `ResilientConfigurationHttpTest` / `PluginConfigurationHttpTest`：两租户暖缓存、相同主体/key/code；修改、撤权、发布指针变化和依赖故障不得跨租户复用 fallback；插件 enterpriseId 与配置、字典快照、菜单和审计一致。
5. `SystemParameterEventRedisIntegrationTest` / `DictionaryNotificationSseHttpTest` 加真实双节点 Redis 运行：租户 A 失效、重试、SSE 游标和权限变化只影响 A；审计/outbox/receipt 归属正确，B 仍可用。
6. 真实 Summer UI 登录：双租户成员切换上下文、指定用户 preview、同名参数/字典覆盖和发布回滚、撤权/故障、迟到响应与脏表单；回读 MySQL、网络请求及双节点结果。组件 mock 和 H2 通过不能将该项标完成。

执行顺序：先由 T36 收口真实授权/组织 claim 接缝，再统一租户解析及数据库约束，然后接入服务/缓存/事件，最后由前端负责人接入并执行真实浏览器矩阵。此清单是明确待办，不是已交付声明。


## 2026-10-15 通用租户与覆盖独立版本

本轮按 #121/#125/#126/#127 的配置后端范围接续；父会话负责 `AuthorizationService`/`AuthorizationRequestInterceptor` 的共享边界，前端与 Task SSE 由其他既有会话负责。未提交、推送或关闭 Issue；实际工作区为唯一源码来源，未用基线副本替换源码，真实索引逐字节保留。

### 实现契约

- 新路径 `/api/v1/enterprises/{enterpriseId}/system-parameters/**`、`.../dictionaries/**` 及参数事件路径；公开读取使用 `/public/enterprises/{enterpriseId}/system-parameters/{key}` 和 `.../dictionaries/{code}`。旧路径仍可使用一致的 `X-Enterprise-Id`/`tenantId` 选择器、JWT `active_enterprise_id` 或唯一 ACTIVE 成员关系。缺失、冲突、歧义或非活动成员返回拒绝，没有 ent-acme/default 回退。匿名公开读必须明确租户。
- 租户贯穿当前值、SQL list/count、JPA 查询/冲突检查、版本/幂等键、审计/outbox、插件 facade、缓存和独立事务中的降级校验。参数 key、字典 code/value 可跨租户同名；V32 改复合唯一键和字典项复合外键，不修改旧迁移。
- 数据范围读取目标企业的策略、角色/用户绑定和组织元数据。当前组织、创建目标组织必须在目标企业内且 ACTIVE；不同企业的同一主体使用各自持久角色和 USER deny。缺策略继续默认拒绝。
- V33 为既有覆盖保留 revision 1，并新增独立 draft/publish/disable/rollback/history。草稿不改变运行值；发布、停用、回滚分别授权。预期 aggregate `version` 与历史 `revision` 不可互换，过期写返回 409；回滚重新校验当前基础值约束/可见字典项。组织 > 租户 > 基础值，隐藏或停用的已发布覆盖不向下回退。
- 字典创建接受组织，项继承其类型组织。类型响应补 tenantId/organizationId。插件按 enterpriseId 显式调用受控读取 facade；企业级读取审计使用该企业。
- 字典通知发送前复核权限与数据范围，撤权后关闭连接；心跳从持久化最新有界窗口补读，游标避免同连接重复发送。参数事件列表和重试按租户定位，不能重试另一租户的 eventId。

### 失败回归与修复

1. 新企业保存策略原返回 403；同名参数/字典路由和全局唯一约束阻止实际双租户使用。
2. 跨企业 organizationId 原可创建参数（201），现返回拒绝；字典此前不接受组织字段，现显式声明并校验。
3. 覆盖无独立版本路由；新回归验证草稿不生效、发布生效、停用拒绝读取、回滚恢复及基础历史不受影响。
4. 字典 SSE 撤权后仍收到下一次发布；发送前重新读取当前授权/策略后回归通过。
5. MySQL 非空 SQL 列表原把 LocalDateTime -> Instant 投影失败吞成 400；按应用 UTC 配置转换后，MySQL 的预览/SQL/JPA 一致性用真实非空结果验证。
6. 测试夹具修正不是放宽生产边界：旧 JWT 明确成员关系，匿名公开 URL 指定租户，组织使用实际存在的节点，MySQL 用户绑定使用合法 JSON。

### 验证与仍需集成验收的部分

`final-regression.json` 为 23 类、99 项，0 失败/错误/跳过。包含真实 MySQL 9 项（新矩阵 8、既有 1）、真实 Redis 4 项（缓存 3、参数 outbox 1）、双租户依赖故障 2 项及字典 SSE 4 项。MySQL 测试先在随机隔离 schema 迁移至 V31，保存旧参数/字典项/覆盖，再升级 V32/V33；验证旧值和覆盖版本保留、租户复合 FK 拒绝串租户引用，结束删除 schema 并断言不存在。Redis 使用独立 Testcontainers 容器并自动清理，未清空共享 Redis。

完整日志、逐类 JUnit XML、红绿记录、源文件清单及前端契约位于本地 `.git/summer4-closeout-20261015/backend/`；它们不纳入源码提交。所有 Gradle 在共享 fcntl 锁下使用 JDK 21、最多 2 workers。MySQL 凭据从既有 Kubernetes Secret 仅载入进程环境，不写入参数、文件或输出。

以上是真实 Spring HTTP/MySQL/Redis 组件证据，不能替代真实浏览器登录/企业切换，也不能把两个缓存对象称为两套独立应用进程。完整 BFF/strict 身份链、整应用双节点断连恢复/通知重放和发布门禁，仍由协调会话结合其他工作验收；本会话不据此关闭整张 T13/T17/T18/T19 票。
