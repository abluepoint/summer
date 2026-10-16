# Summer 4 T36 最终升级验收与父 #108 追踪

> 状态：T36本地技术验收证据已通过，待root最终集成审阅、提交和关票。候选02的九批原始结果全部保留；候选03受影响原生补验85/0/0/0、Chrome5/0/0/0，exit0，临时资源清理完成。#144与父#108尚未由本报告宣布关闭。

## 需求与设计

本票对应 [#144](https://github.com/abluepoint/summer/issues/144) 和父 [#108](https://github.com/abluepoint/summer/issues/108) 的故事 49–52。升级以 MySQL 权威数据、Redis 共享状态、Flyway 追加迁移及现有模块化单体为基础。预检只判断当前节点的十类运行前提，备份恢复、兼容窗口和真实业务流程采用独立运行证据。

错误遵循 ADR0009：应用校验码 `IAM_RELEASE_INVALID` 保持稳定并显式声明 `INVALID_ARGUMENT`，HTTP 状态由 `ErrorStatusResolver` 决定。幂等冲突的旧非规范码 `SUMMER_IDEMPOTENCY_CONFLICT` 改为框架已有的 `SUMMER_CONFLICT`。未知异常使用固定安全消息。

受管或本地发布身份每次请求重查持久权限：仍需 `internal.release.read`，写操作还需对应写权限。平台能力由 `platformDecision` 汇总主体仍有效的成员企业，精确接受 ACTIVE、scope=PLATFORM、无企业 owner 的同码能力；同码 ENTERPRISE 能力不能替代已撤销的 PLATFORM 授权。外部非受管发行方仍使用原令牌信任与 scope 契约，本票未扩展为外部 IAM 持久化系统。

## 本票追踪

| 用户故事 / AC | 实现与验证入口 | 当前证据 / 状态 |
| --- | --- | --- |
| 49 / AC1、5 | RuntimeReleaseChecks；Flyway 迁移；migration_probe.py | 当前候选MySQL39份、历史31份字节未变；历史31→39实际演练54/0/0/0通过，源库仍31 |
| 50 / AC2 | t36-mysql-archive.py；archive.py | native-attempt-01已完成加密备份/新库恢复、10张关键表计数、真实登录与权限200/403、恢复旧业务值 |
| 50 / AC3 | run.py；browser.mjs | 实际新旧JAR含15个不同class；API/会话/缓存/事件/任务/SSE已通过；Chrome补批WS投递、ping/pong、心跳与重放通过 |
| 50 / AC1、5 | diagnostics/release 页面与菜单 manifest v7 | 候选03真实Chrome十业务行READY、Redis暂停503/安全错误、恢复、403清旧状态和WS均通过，5/0/0/0 |
| 49–52 / AC6 | ReleaseErrorCode、Controller、Service | 安全500有效红绿；可覆盖422有效红绿；原生撤权红2项；同码跨scope有效红2项，最终相关JUnit绿4项；新候选真实旧JWT与同码scope补验93/0/0/0，全部自有资源清理完成 |
| 52 / AC4、7 | 本报告、精确文件白名单、原始证据目录 | 52故事/36票六段追踪、逐批精确计数与原失败已回填；最终Issue状态由root回读 |

## 本轮缺陷与局部验证

| 缺陷 / 修正 | 实际 tests / failures / errors / skipped | 证据 |
| --- | --- | --- |
| 异常响应暴露内部类型：有效 red | 1 / 1 / 0 / 0 | safe-error-red |
| 固定未知异常安全消息：首轮回归 | 14 / 0 / 0 / 0 | safe-error-green |
| 旧令牌撤权仍200：真实 PKCE/MySQL/独立Redis red | 18 / 2 / 0 / 0 | revocation-native-red-01 |
| 持久撤权测试夹具写死 ETag：412失败 | 17 / 1 / 0 / 0 | permission-green（其中模块架构2项通过） |
| 使用响应 ETag 修正夹具后定向回归 | 1 / 0 / 0 / 0 | permission-green-02 |
| 状态解析器回归夹具括号错误 | 0项执行，编译失败，不计有效red | typed-error-red |
| 状态解析器要求422而实际400：有效red | 1 / 1 / 0 / 0 | typed-error-red-02 |
| 类型化错误、冲突码、安全消息定向回归 | 4 / 0 / 0 / 0 | typed-error-green |
| 同码ENTERPRISE替代已撤销PLATFORM：有效read/write red | 2 / 2 / 0 / 0 | scope-red |
| 精确PLATFORM持久判定及旧显式拒绝回归 | 3 / 0 / 0 / 0 | scope-green |
| 跨企业正向夹具漏principal_type=USER，创建企业403 | 3 / 1 / 0 / 0 | scope-union-green（另2项模块架构通过） |
| 补全身份类型后跨企业授权与成员停用回归 | 1 / 0 / 0 / 0 | scope-union-green-02 |
| 最终完整scope类3项及旧撤权1项 | 4 / 0 / 0 / 0 | scope-final-green，exit0，捕获范围无漂移 |

这些是不同运行/输入的计数，不能简单相加成最终测试覆盖。前端最终 source check 的 typecheck、局部 lint、菜单合同均退出0；此前 manifest 版本与权限声明测试失败保留，并在修正后通过。

4个后端主文件和5个相关测试已在 `scope-fix/MAIN_SOURCE_FROZEN.json` 重新冻结，原冻结清单和六个UI摘要保留。最终定向源码快照包含本票源码及当时共享授权入口、测试配置；无漂移仅指捕获范围。统一candidate02已构建且独立预检通过；保留的root backend05为767/0/0/0、180 suites、exit0，729输入无漂移；最终backend06为775/0/0/0、181 suites，730输入稳定，644执行/131缓存。执行与缓存复用边界及frontend02/03分批门禁见[最终门禁报告v2](../acceptance/summer4-final-gates-144-v2.md)，不把767都写成本轮重执行，也不把构建当原生业务验收。

## 运行与制品

- 最终候选03：`e9d1ff6ef33b74c1c8ffef9bc6417b06554d92490f8b1e814c5dec49d3d2f28e`；543输入稳定。相对02只有`SessionFailureConfiguration.java`一个生产源变化，对应外层class与匿名filter class两个条目；987个应用/依赖条目集合一致，其他业务字节、依赖和40份迁移资源完全相同。外层class反汇编语义不变，逻辑增量在Redis超时原因链判定。支持复用02不受影响的scope93、历史迁移54、综合前213及cache故障76；这些都不改称03新执行。

- 保留的候选02：`8a2ec5987b60d7356cf2207b964a0e878d8434f903e59e887500a866ad09fb7e`；543构建输入和4main/5tests冻结摘要核对一致。JAR共40份迁移资源，其中MySQL39份，另1份通用审计迁移；所有资源逐字节匹配源码。root初始校验器错误要求总数39导致原回执失败，已保留原结果并在 `frozen-build-02/migration-count-correction.json` 更正；不是迁移执行失败。历史31份MySQL迁移与候选02字节一致，真实数据库升级补批54/0/0/0通过。

- 已保留的候选01（未包含scope补救，不能作为最终候选）：`6fbb80b774b51f98250b006eee243f6a1153086c77de2d08889e055a1f9c953b`，root 构建，543 输入，39 迁移文件。
- 滚动旧候选：`28a48d6cda86996d2d1b4d396c854e3d51d671ccc7994a41029daa567df02d18`，真实代码不同，使用当前迁移窗口。
- 迁移历史候选：`546510c7646131b34fbb5fa5e8088074de913a4e919dac0f74862a4363dc9b20`，31 份迁移；仅用于明确标注的历史→当前恢复库升级演练。
- 证据根目录：`.git/summer4-closeout-20261015/t36-final/`。每次运行用新子目录，保存执行脚本、候选SHA、输入快照、结果、清理核验；失败结果不覆盖。
- 基础设施：随机自有 MySQL schema/应用账号、独立 Redis 容器、23081/23082 节点与23083恢复节点，UI23573/代理23580。业务配置仅正式HTTP。凭据仅内存、环境或stdin，备份加密后才写盘。

## 模块边界与未完成项

继续保持模块化单体。只有独立伸缩、独立发布、清晰的数据所有权或明确的故障隔离需求出现时，才评估拆分。现有认证、授权、参数/字典、任务、消息与插件边界继续通过契约协作；跨进程以后仍需保留请求关联、错误分类、幂等键、事件版本和重放语义。没有生成生产 Kubernetes 清单，没有拆分微服务，也没有把本地演练作为生产发布批准。

本票原生产品缺口已闭合。root修复后backend06完整门禁已完成：775/0/0/0、181 suites，730输入无漂移；644项本轮执行、131项缓存（含本批Security autoconfigure16），基线767无缺失，新增8项均SessionFailureConfigurationTest。最终双轴只读补审0/0仅限Session两Java；不将这些分批数量相加。提交推送、历史AC复选框同步、#144/#108关闭及最终回读由root执行。历史31迁移验收与历史综合688不作为本轮当前通过数。

## 原生批次与失败保留

| 批次 | tests / failures / errors / skipped | 范围与结论 |
| --- | --- | --- |
| scope-native-attempt-01 | 93 / 0 / 0 / 0 | 同码PLATFORM/ENTERPRISE、原JWT即时撤权、恢复和关联ID；exit0，清理全部通过 |
| native-attempt-01 | 214 / 1 / 1 / 0 | 前213个命名检查通过；最后Chrome表格定位夹具失败。API、滚动、恢复的已执行证据保留，不改写整批为绿 |
| native-ui-attempt-02 | 37 / 1 / 1 / 0 | Chrome 1/1/1/0；Arco表头也匹配行类，11行包含表头 |
| native-ui-attempt-03 | 50 / 1 / 1 / 0 | Chrome 2/1/1/0；tbody可见业务行实际10，正常页通过；故障夹具仅接受200而提前中止，未捕获实际状态 |
| native-ui-attempt-04 | 50 / 1 / 1 / 0 | Chrome 2/1/1/0；Redis暂停实际500、body.error.code缺失、header/body requestId一致；保留产品契约疑点 |
| migration-native-attempt-01 | 17 / 1 / 1 / 0 | 建数夹具漏publish能力，public=true创建403；升级未执行，原批保留 |
| migration-native-attempt-02 | 54 / 0 / 0 / 0 | 实际31→39恢复库升级、V32复合唯一索引替换、V36成员保留、当前API读写旧值；源库仍31，exit0 |
| cache-fault-native-attempt-01 | 76 / 0 / 0 / 0 | 两节点故障读取200、写后受控503/无数据、受限403、恢复后最终收敛；exit0 |
| native-ui-attempt-05 | 74 / 2 / 1 / 0 | Chrome 5/1/1/0：正常10项、恢复、403/清旧状态/再恢复、WS全部通过；500成功包装缺陷仍红。缓存B节点受控503被旧夹具要求200误拒，另做最小补批；Chrome有界强制回收且进程已退出 |
| candidate03-native-ui-attempt-01 | 85 / 0 / 0 / 0 | 两节点Cookie超时503/no-store/无数据/关联ID、纯Bearer200 NOT_READY、两类恢复；Chrome5/0/0/0，正常/故障/恢复/403/WS；exit0 |

上述各批的随机库、账号、独立Redis和5个监听端口均有独立cleanup核验。综合01与UI02/03失败批的543后端与252前端捕获输入另作事后哈希比对，无漂移；这不是补写原运行未执行的尾部断言。

综合原生已执行内容：两节点各十项预检；dryRun=false的400/IAM_RELEASE_INVALID及幂等冲突409/SUMMER_CONFLICT；未执行计划保持NOT_VERIFIED；真实Cookie会话；混合版本参数缓存失效；两个授权事件消费组lag/pending为0和重复事件无额外PROCESSED回执；同幂等键两节点均202而只有一次SUCCESS；两节点SSE与Last-Event-ID重放；ACTIVE/GRACE公钥一致、旧JWT仍可用；插件不可信/不兼容拒绝与兼容升级；滚动重启期间存活节点148次读取全200。恢复阶段10张关键表计数相等，备份加密且0600，已有恢复库拒绝覆盖，真实恢复登录和权限200/403及恢复公钥身份均验证。

原缺陷证据：UI05预检请求约2128ms返回HTTP500，顶层字段为success/data/error/requestId/timestamp，success=true、error=null；data仅记录字段名timestamp/status/error/path。异常类仅保存QueryTimeoutException及RedisCommandTimeoutException，没有原异常文本或凭据。原生截图已目视核对十行中文、错误与requestId、旧结果清除和恢复，无裁切遮挡；故障页面正确退到安全失败，原后端错误信封缺陷保留为红灯。

修复后证据（[Session原始红绿报告](../acceptance/session-redis-timeout-144.md)保留其当时待原生状态，当前结论见本报告及v2）：root仅增加RedisCommandTimeoutException原因链识别，定向9/0/0/0并保留JDBC超时负对照。候选03两节点Cookie分别约2077/2320ms返回503、success=false、SUMMER_SESSION_STORE_UNAVAILABLE、data为空、Cache-Control:no-store和一致requestId；纯Bearer仍独立返回200/NOT_READY。恢复后Cookie与Bearer均READY；Chrome错误页显示稳定错误码和requestId，权限403后无旧表格，恢复再显示十项。最后Chrome有界强制回收，processExited=true，pageErrors=0；自有进程和端口核验通过。

各批结果、清理、真实故障结构和七条AC映射见[结构化原生验收记录](../acceptance/summer4-release-upgrade-144-result.json)。候选02证据不因后续候选变化而改写。

## 父方案追踪与历史AC

完整52故事／36工作票／252条AC的六段追踪已迁至[父方案最终追踪](../acceptance/summer4-parent108-trace.md)及配套JSON，包含设计ADR、实际实现和UI入口、测试文件、分批缺陷与关闭评论。此处不再重复维护仅含commit的旧台账。

历史#109/#117/#133的21项AC见[root独立历史复核](../acceptance/summer4-historical-ac-109-117-133.md)。旧525/47/7批次与本次T36的实际运行分列；历史复选框同步、最终提交与Issue状态由root执行。
