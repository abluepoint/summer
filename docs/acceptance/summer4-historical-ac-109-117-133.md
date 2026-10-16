# 三张历史任务票验收项同步映射

#109、#117、#133 在历史验收后已关闭，但各有7条复选框未同步。本报告逐条记录21项的原证据及边界；同步依据原验收，不推导当前候选已经完成。当前门禁见[最终检查](summer4-final-gates-144.md)，T36原生结果见[升级验收](../guides/summer4-t36-final-acceptance.md)。

原关闭引用为 `ef96e13`；此前完整检查525/0/0/0、T25 Sample47/Security12/Chrome7和独立双节点14分别保留，两条BFF/strict早退不算真实外部链路。旧T25的Chrome7不证明MySQL持久JWK；缓存故障单测不是原生故障。每项仅按仓库已实现、已验收范围同步。

## #109 T01｜认证存储扩展点预重构

故事：1, 34, 35, 37；[原关闭评论](https://github.com/abluepoint/summer/issues/109#issuecomment-6185696796)。

| AC | 原要求 | 支持证据 | 验收边界 |
|---|---|---|---|
| 1 | 现有登录、PKCE 回调、退出、CSRF 和会话测试继续通过。 | 10项PKCE、14项双节点会话、Cookie/Secure各1及真实Chrome7保留登录/回调/注销/CSRF/会话行为。（证据 C109, D25, S25, SEC, X, PKCE, NODES, B25） | MockMvc与最小Tomcat、完整应用Chrome分列；历史47/12/7有重叠，不加总为新执行。 |
| 2 | 身份、OAuth 客户端、授权同意、会话和密钥存储均可由配置选择实现。 | 默认memory与六维custom选择、缺失adapter拒绝均3绿；Sample相应JDBC/Redis/MySQL适配器已有受控官方接口。（证据 SEL, WIR, ADP, JDBC, X） | 配置选择是启动期adapter/应用Bean覆盖；不承诺Sample任意运行时热切换或每个store支持所有数据库。 |
| 3 | 凭据与密钥仅通过受控内部契约处理；浏览器 Token 仍遵循既有内存存储和协议边界。 | 浏览器userStore在内存，协议state可放sessionStorage；OAuth管理不返回secret；JDBC受控类型映射保留claims/iat/exp。（证据 OIDC, PKCE, JDBC, D25, D36, B25） | 原T25未启用MySQL keys，不能用7Chrome证明持久密钥/JWK共享；仅历史10-07 T36另做的显式MySQL key证据支持后者。 |
| 4 | 增加契约测试，验证默认实现与未来 MySQL/Redis 实现的外部行为一致。 | 已增加默认/自定义装配契约，JDBC往返、Redis会话与真实PKCE作为后续适配器外部行为的分层验证。（证据 SEL, SEC, JDBC, NODES, X, B25, R36） | 3项SEL直接证明装配及替换，不等于所有未来适配器全功能等价矩阵；仅本仓已实现适配器及已验收行为可同步。 |
| 5 | 验证所需迁移、后端真实 HTTP 行为和 Summer UI 流程；含成功、无权限、无效输入或故障路径，不以 mock 或零用例通过替代验收。 | 空随机MySQL/Flyway、实际JDBC OAuth、Redis两JVM、真实Chrome登录/注销，以及CSRF403、撤销Cookie401；输入/权限另有PKCE和UI6。（证据 D25, B25, NODES, D36, R36, UI6） | 完整生产HTTPS、独立BFF/strict外部链路及会话管理页全部按钮不在T01扩展点验收内；本次未新跑迁移/HTTP/UI。 |
| 6 | 复用 Parameter contract、Unified/Raw response、Request correlation 与领域术语；框架错误使用 SUMMER_ 命名空间，敏感值不得进入日志、Issue 或测试快照。 | 复用Spring官方存储接口和协议Raw；业务ApiResponse、SUMMER_框架码及requestId；凭据仅历史受控注入，报告不复制Token/私钥。（证据 WIR, OIDC, PKCE, JDBC, SESS, X, DM） | Parameter contract按适用端点使用，不要求协议端点改JSON Field；这不是全仓敏感值扫描或所有未来adapter安全证明。 |
| 7 | 记录本票用户故事—设计—实现—测试—缺陷—验收证据；报告实际 tests/failures/errors/skipped，保留既有用户改动。 | 关闭评论关联ef96、公开设计/修复红灯/实现/分组结果；原525归档XML本次重新汇总相符；原用户改动按历史合并记录排除。（证据 C109, D25, DM, S25, SEC, B25, X） | 提交祖先不等于验收；525内两条BFF/strict早退，不算真实链路。用户历史索引保护依原记录，非本次重演。 |

## #117 T09｜参数与字典二级缓存

故事：9, 14, 15；[原关闭评论](https://github.com/abluepoint/summer/issues/117#issuecomment-6185697881)。

| AC | 原要求 | 支持证据 | 验收边界 |
|---|---|---|---|
| 1 | 读取路径优先使用 L1/L2，缓存未命中回源 MySQL。 | version验证后优先L1/L2，miss调用权威loader；单元L1命中/击穿、真实Redis L2共享2项归档可核。（证据 D09, ADR09, CACHE, CACHEIN, CACHEUNIT, CACHETC, X, R36） | 参数/字典先查当前授权、可见性或快照版本；不能解释为零SQL。只有已接入参数find/公开字典路径，不是全部任意查询。 |
| 2 | 参数或字典变更后所有节点最终失效旧缓存。 | 共享version递增使旧key不可达；每节点独立Stream组且receipt前清L1；历史双JVM A更新B读新值，Redis version递增/L2存在。（证据 D09, ADR09, CACHE, CACHEIN, CACHETC, R36, D36） | 最终一致性，依赖节点恢复、独立node-id与事件/TTL；不承诺网络永久分区中立即强一致。R36缓存摘要无逐HTTP计数，不虚构n/n。 |
| 3 | Redis 不可用时行为可诊断，不能返回越权或过期的敏感配置。 | version/Redis读取异常清L1并回权威loader；故障计数+固定脱敏日志；按当前可见性/scope/主体分key，不绕过授权。（证据 CACHE, CACHEIN, CACHEUNIT, X, ADR09） | 故障单测为mock注入，历史R36 NOT_READY不是缓存安全回源的直接原生证明；当前候选真实Redis故障与恢复须由计划中的T36补强，非新增功能缺陷。 |
| 4 | 集成测试覆盖命中、未命中、失效、TTL 和跨节点一致性。 | 历史缓存单测3/0/0/0+真实Redis集成2/0/0/0覆盖命中、未命中、版本失效、TTL；另有双JVM真实API收敛摘要。（证据 CACHETC, CACHEUNIT, X, R36, D36） | 两cache对象同JVM不可冒称两JVM；当前新增租户隔离第三项不倒算入旧2项。未运行时不能以Docker条件或测试存在算通过。 |
| 5 | 验证所需迁移、后端真实 HTTP 行为和 Summer UI 流程；含成功、无权限、无效输入或故障路径，不以 mock 或零用例通过替代验收。 | 历史MySQL迁移/双JVM缓存与UI6真实页面涵盖参数/字典成功、无效JSON零写入、真实403/requestId。（证据 D09, D36, R36, UI6, X） | DictionaryHttpTest/SystemParameterHttpTest归档是MockMvc，不能照公开简写“真实HTTP”扩大；原生依据是双JVM与UI6。缓存对UI透明，不要求新增缓存页面。 |
| 6 | 复用 Parameter contract、Unified/Raw response、Request correlation 与领域术语；框架错误使用 SUMMER_ 命名空间，敏感值不得进入日志、Issue 或测试快照。 | 缓存不改变参数/字典原业务API；tenant/resource/version+主体scope隔离；不输出driver异常或缓存值；原统一错误/requestId断言保留。（证据 ADR09, CACHE, CACHEIN, X, UI6） | 不能凭缓存类存在保证所有字段/敏感值全仓安全；本次只核缓存路径及公开响应契约。 |
| 7 | 记录本票用户故事—设计—实现—测试—缺陷—验收证据；报告实际 tests/failures/errors/skipped，保留既有用户改动。 | #117评论、ADR与缓存契约、原始JUnit suite及真实UI结果形成可复核链；旧525总体数已重读。（证据 C117, D09, D36, DM, X, R36, UI6） | 本报告显式保留旧失败ui-diagnostic与最终UI6区别；不把关闭扩为T18/T19或当前T36完成，不改历史记录/用户文件。 |

## #133 T25｜共享登录会话与撤销

故事：1, 34, 42；[原关闭评论](https://github.com/abluepoint/summer/issues/133#issuecomment-6185698881)。

| AC | 原要求 | 支持证据 | 验收边界 |
|---|---|---|---|
| 1 | 同一登录会话可在多个后端节点验证。 | SpringSession共享完整Context；14项中sameCookieAuthenticatesOnAnotherNode；完整应用7项同CookieB读取、停A续接、重启A后继续。（证据 D25, ADR25, SESS, NODES, S25, X, B25） | 最小Tomcat内存OAuth14与完整MySQL/JDBC OAuth/Chrome7分列；非所有部署拓扑/不同生产版本兼容保证。 |
| 2 | 退出和管理员撤销后，原服务端会话不能继续使用；已有 JWT 行为按既有有效期与业务权限契约验证。 | logout/OIDC先撤共享索引，管理员revoke后Cookie401；有效旧JWT仍200，无效/过期Bearer401且不退回Cookie。（证据 ADR25, SESS, NODES, X, B25） | JWT不会被会话注销全局吊销；业务权限仍执行既有持久授权判定。原生7的列表/撤销为browser fetch，不是每个管理员按钮。 |
| 3 | 会话过期、并发会话和 CSRF 行为符合既有 ADR。 | idleExpiry、anotherBrowserSessionSurvivesRevocation、concurrentRefreshCannotResurrect、cookieWriteRequiresCsrf及跨节点CSRF均实际14项内。（证据 ADR25, NODES, X, B25） | 空闲默认30min由配置，短测试窗口证明过期机制；非任意并发/性能上界或生产HTTPS代理的完整验收。 |
| 4 | Redis 故障和会话不存在均有可诊断结果，不泄露 Token。 | missingIndex拒Cookie401；真实不可达Redis节点503/SUMMER_SESSION_STORE_UNAVAILABLE并有X-Request-Id；有效Bearer仍200；API非法/未知reference按400/404。（证据 SESS, NODES, X, D25） | 真实14项覆盖连接失败；未逐一注入所有保存超时、序列化故障。只报告状态/码/关联信息，不复制Token。 |
| 5 | 验证所需迁移、后端真实 HTTP 行为和 Summer UI 流程；含成功、无权限、无效输入或故障路径，不以 mock 或零用例通过替代验收。 | 完整应用空库Flyway+MySQL/JDBC OAuth、独立Redis双JVM+Chrome7均通过；真实CSRF403、Cookie401、注销和停机接续。（证据 D25, B25, S25, X, R25） | 生产HTTPS、全页面按钮、独立BFF/strict链路和完整升级矩阵由父范围跟踪，不额外归给本票；本次没有执行新环境。 |
| 6 | 复用 Parameter contract、Unified/Raw response、Request correlation 与领域术语；框架错误使用 SUMMER_ 命名空间，敏感值不得进入日志、Issue 或测试快照。 | 身份仅从完整SecurityContext恢复，索引不重建JWT；协议Raw、业务统一错误、SUMMER_码及requestId；Token只保留浏览器内存。（证据 ADR25, SESS, PKCE, OIDC, X） | 旧JWT按原有效期和业务权限而非登录索引撤销；敏感值未复制入本审查产物。不能把局部检查当全仓日志审计。 |
| 7 | 记录本票用户故事—设计—实现—测试—缺陷—验收证据；报告实际 tests/failures/errors/skipped，保留既有用户改动。 | 公开文档记录首次跨节点/CSRF/JDBC红灯、修复设计、47/12/7计数、清理和边界；关闭评论指向固定ef96。（证据 C133, D25, DM, S25, SEC, B25, R25, X） | 原47/12/7、后续525、当前全量门禁是不同批次；全部分别保留，用户改动依原记录保留，未在本次重演。 |

## 证据索引

完整结构化来源和摘要见[同名JSON](summer4-historical-ac-109-117-133.json)。`.git/`原始XML和浏览器回执为本机保留证据；下列实现、设计及原关闭评论可在仓库或GitHub回读。索引SHA为原审计快照，不将后续文档演进倒算到历史执行。

- `C109`：[原关闭评论](https://github.com/abluepoint/summer/issues/109#issuecomment-6185696796)。2026-10-15 关闭评论；明确本次关票未重跑，引用历史验收。
- `C117`：[原关闭评论](https://github.com/abluepoint/summer/issues/117#issuecomment-6185697881)。同上；未把关闭扩为 T18/T19 完成。
- `C133`：[原关闭评论](https://github.com/abluepoint/summer/issues/133#issuecomment-6185698881)。同上；排除生产 HTTPS、所有页面按钮、独立 BFF/strict 链路。
- `D25`：[summer4-t25-shared-session-acceptance.md](../../docs/guides/summer4-t25-shared-session-acceptance.md)。2026-10-07 历史验收：Sample47、Security12、完整应用Chrome7，分组而非相加为新一轮。当前文档与关闭提交字节一致。
- `D09`：[summer4-cache-117.md](../../docs/guides/summer4-cache-117.md)。L1/L2、租户/version/key、TTL、节点独立消费组及故障绕过契约；与关闭提交字节一致。
- `D36`：[summer4-t36-upgrade-acceptance.md](../../docs/guides/summer4-t36-upgrade-acceptance.md)。只引用“2026-10-07 真实隔离环境复验”：两JVM缓存、MySQL密钥与轮换、六组真实UI等。当前文档开头指向新最终报告不作新通过依据。
- `DM`：[summer4-merge-review-20261015.md](../../docs/guides/summer4-merge-review-20261015.md)。历史完整check525/0/0/0；两BFF/strict测试提前返回，不作实际外部链路；原用户改动排除在提交外。
- `X`：本地原始回执 `.git/summer4-closeout-20261015/final-review/historical-ac-sync-01/historical-suite-readback.json`。本次解析121份历史XML得到525/0/0/0，并摘录17个相关suite及逐case状态；不打印system-out，不是新执行。每份目标XML有SHA。
- `B25`：本地原始回执 `.git/summer4-review-20261005/t25-20261007/browser-result.json`。完整Sample、空随机MySQL/Flyway/JDBC OAuth、独立Redis、两JVM、Chrome真实PKCE；浏览器fetch列表/撤销，非完整会话管理页按钮。
- `R25`：本地原始回执 `.git/summer4-review-20261005/t25-20261007/runtime-result.json`。原schema、独立Redis已移除，18081/18082无监听，errors=[]。不证明其他运行清理。
- `S25`：本地原始回执 `.git/summer4-review-20261005/t25-20261007/sample-final-counts.json`。10个Sample suite；其中SharedSessionNodes14为随机端口真实Tomcat/Redis，其他MockMvc/单测按实际边界。
- `SEC`：本地原始回执 `.git/summer4-review-20261005/t25-20261007/security-counts.json`。含SecurityStorageContractTest3；默认/自定义装配契约，不是所有适配器的完整同构行为矩阵。
- `R36`：本地原始回执 `.git/summer4-review-20261007/t36-live/result.json`。SCOPED_LOCAL_GATES_PASSED；cache/keys/ui均true；不同本地制品rolling和备份恢复。cache是分领域布尔门禁，未给出可据以声称n/n的逐HTTP case原始计数。不是当前T36。
- `UI6`：本地原始回执 `.git/summer4-review-20261007/t18-t19-ui/live/result-1791382435330.json`。real-backend/executed=true，参数创建/覆盖、组织字典、无效输入零写入、真实403/requestId；不得使用t36-live/ui-diagnostic-latest.json里的旧失败诊断冒充该绿灯。
- `SEL`：[SecurityStorageContractTest.java](../../summer-security-autoconfigure/src/test/java/com/abluepoint/summer/security/autoconfigure/SecurityStorageContractTest.java)。exposesDefaultConsentAndSessionStorageContracts、rejectsCustomStorageSelectionWithoutAnApplicationAdapter、usesApplicationAdaptersWhenEveryStorageIsConfiguredAsCustom。默认/自定义均同官方接口。
- `WIR`：[SummerSecurityAutoConfiguration.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/SummerSecurityAutoConfiguration.java)。storage六维配置与ConditionalOnMissingBean；配合SummerSecurityProperties和SecurityStorageSelectionValidator，非memory而缺adapter启动失败。
- `ADP`：详见结构化映射。JDBC客户端/授权/同意、身份仓库、Redis session、MySQL/reference密钥adapter；T01是扩展点，不要求每类store提供所有后端。Sample身份应用Bean优先并非按配置动态热切换。
- `OIDC`：[oidc.ts](../../summer-ui/src/auth/oidc.ts)。userStore=InMemoryWebStorage；sessionStorage仅协议state/recovery；S256/public-client、无refresh由PKCE契约测试佐证；不把sessionStorage存在误认成Token持久化。
- `PKCE`：[PublicClientPkceIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/PublicClientPkceIntegrationTest.java)。MockMvc 10历史case：PKCE/code、raw token/CSRF、拒plain与不支持grant、登录CSRF、OIDC注销、独立浏览器session。原生浏览器另看B25。
- `JDBC`：[OAuthPersistenceHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/OAuthPersistenceHttpTest.java)。usesPersistentStoresAndProtectsClientManagement、clientAndConsentCanBeWrittenAndReadThroughJdbcContracts、managesPublicClientLifecycleWithoutReturningSecrets；历史XML3绿是H2/JDBC+MockMvc，不称MySQL。当前新增编辑测试不倒算到历史3。
- `CACHE`：[VersionedTwoLevelCache.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCache.java)。get每次先读version，再L1/L2/loader；version或entry读取失败清本地并直接loader；invalidate本地清除+共享version递增；TTL、聚合故障计数与固定日志。
- `CACHEIN`：详见结构化映射。参数find与字典publicSnapshot先校验当前可见性/版本再缓存；cache key含主体、scope指纹等；outbox投递递增version，各node独立group在receipt检查前evictLocal。安全前置查库不等于完全免SQL。
- `CACHEUNIT`：[VersionedTwoLevelCacheTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCacheTest.java)。readsFromL1AfterTheFirstLoadWithinTheSameVersion、protectsAColdEntryFromAConcurrentCacheStampede、bypassesLocalAndRemoteEntriesWhenRedisCannotValidateTheVersion；第三项为mock故障，非真实Redis停机。
- `CACHETC`：[VersionedTwoLevelCacheRedisIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCacheRedisIntegrationTest.java)。历史XML实际2项：twoNodesShareL2AndVersionInvalidationMakesOldEntriesUnreachable、l1AndL2ExpireAfterConfiguredTtl。真实TC Redis、两cache对象，同JVM；当前第三项租户隔离不能倒算历史已执行。disabledWithoutDocker不能单凭注解称通过，历史XML两项skip=0。
- `NODES`：[SharedSessionNodesHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/SharedSessionNodesHttpTest.java)。14历史case：真实HTTP/Tomcat/Redis，内存OAuth和最小测试应用；同Cookie跨节点、CSRF、缺索引、管理员撤销、另浏览器不受影响、过期/并发撤销、503、Bearer独立、OIDC。
- `SESS`：详见结构化映射。redis选择启用SpringSession；索引仅deny已有context不铸造身份，Lua不复活REVOKED；撤销权限/主体归属；401/404/400/503/统一错误与requestId。
- `ADR09`：[0025-versioned-two-level-parameter-dictionary-cache.md](../../docs/adr/0025-versioned-two-level-parameter-dictionary-cache.md)。最终一致性、租户边界、不可验证version不读旧值，非强一致全网广播。
- `ADR25`：[0029-share-servlet-login-context-and-isolate-bearer-authentication.md](../../docs/adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。Cookie与Bearer隔离、CSRF、空闲期、注销不隐式撤access token；单实例Redis，非Cluster跨槽承诺。
