# T32 / T35 修复验收记录（2026-10-07）

本轮在 `summer4` 共享工作区接续 T18/T19，只修改受控降级、参数/字典调用点、插件读取和相关测试文档。未修改身份、Security、依赖、构建或会话配置；未暂存、提交、推送或关闭 Issue。原有改动保留。

## 缺陷、实现与回归对应

| 用户故事 / 需求 | 复现缺陷 | 修复与验证 |
| --- | --- | --- |
| T32 #140：故障下安全读取（故事 43、44） | 暖缓存后更改版本、停用、权限表不可读，仍返回旧值 200 | 参数键加入行 ID / 版本；`ConfigurationFallbackGuard` 在 `REQUIRES_NEW`、`READ_COMMITTED` 事务中重读当前授权与资源元数据；HTTP 故障注入验证 503，无旧值 |
| T32：当前权限与有效版本 | 原降级只检查 TTL；无法确认当前主体、公开标记、发布指针或覆盖值 | 降级要求显式 proof；参数校验版本、ACTIVE、公开标记、范围；字典校验发布指针、公开/启用/发布状态、项范围、适用覆盖是否出现。验证异常时失败关闭；明确接口拒绝保留 403 |
| T32：边界、指标、恢复与隔离 | 值与写入时间存在于两张并发表，指标非原子；包装异常影响拒绝计数 | 单条不可变缓存条目，原子计数，解包后分类；核验完成后复查 TTL；未知操作在调用 loader 前拒绝。测试覆盖过期、无 proof、业务拒绝、重试、超时晚完成、跨键隔离、并发 bulkhead、恢复 |
| T35 #143：插件不扩大数据权限 | 调用 ScopeFilter 后丢弃结果，自行 SQL 读值；字典返回当前草稿 | `PublishedParameterReader` / `PublishedDictionaryReader` 直接委托既有受控公开读取；复用组织/本人/项范围、公开与 ACTIVE 条件、当前发布快照和覆盖优先级 |
| T35：权限与生命周期 | 企业插件列表未调用已有菜单能力过滤器 | 接入 `enterpriseView`；保留可信摘要、版本兼容和插件/客户接口权限回归 |
| T35：故障不能伪装成功 | 广泛异常处理可能吞掉受控读取的错误语义 | 仅 NOT_FOUND 的可选值视为缺省；其余运行时故障交统一处理；权限存储不可用不返回配置。读取不共享外层写事务，避免缺省值异常导致审计回滚 |

## 真实执行证据

所有 Gradle 运行均由 Python `fcntl.flock` 独占 `.git/summer4-review-20261005/gradle-execution.lock`，持锁等待整个 Gradle 子进程及结果归档。等待另一个后端会话期间不启动竞争构建。

证据目录：`.git/summer4-review-20261005/t32-t35-20261007/`，其中 `files.txt` 为本轮 14 文件清单，`start/` 为五个修改前核心源码副本，`initial-index.entries` 为真实索引条目快照。

- `t32-red`：3 tests / 3 failures / 0 errors / 0 skipped；原实现三个受限情形均错误返回 200。
- `t35-red`：夹具误传字典接口不支持的 `organizationId`，4 项在创建阶段收到 400；不是有效业务缺陷证据。
- `t35-red-corrected`：4 / 4 / 0 / 0；正确复现越界参数、草稿读取、停用参数泄漏和菜单泄漏。组织归属随后按接口能力以独立测试库元数据设置，覆盖正反主体。
- `t32-t35-green`：测试泛型推断编译失败，没有执行新测试；该轮 JSON 不可当成新测试计数。
- `t32-t35-green2`：21 / 1 / 0 / 0。HTTP 与生命周期全部通过；单元夹具使用第三方默认重试配置，引发权限异常重试超时，已改为调用生产 `ResilienceConfiguration`。
- `t32-t35-expanded`：25 / 0 / 0 / 0；包含 10 项 HTTP 降级、6 项单元、6 项插件配置、3 项生命周期。
- `final-regression`：67 / 1 / 0 / 0；唯一失败是 `PluginLifecycleHttpTest` 复用了仍缓存的 Spring 上下文，但共享 `summer-test` H2 表已由其他上下文的 create-drop 删除（日志：`SUMMER_PLUGIN` 不存在）。本轮三个相关 HTTP 测试类已指定不同 H2 内存库，不修改全局测试配置。
- `final-isolated-regression`：**67 tests / 0 failures / 0 errors / 0 skipped**，16 个测试类全部通过。包含本轮 26 项与参数、字典、数据范围、缓存、诊断既有回归 41 项；`git diff --check` 通过，测试期间本轮源码哈希未改变。

HTTP 回归使用 Spring Boot / MockMvc、实际控制器、授权服务、JPA 与隔离 H2。故障用例只在缓存边界注入依赖失败，或在测试库修改授权/资源元数据模拟并发变化；没有用 mock 返回值代替最终 HTTP 授权结果。本轮没有运行真实 MySQL / Redis 多节点或真实浏览器。

## T36 接续验收边界

1. 在合并 T25、T30 与本轮改动后，使用真实登录和生产代理验证插件菜单、示例页面、公开撤回、发布/回滚、故障状态及错误展示；本轮未改 UI，也未把此前 npm 通过记作本轮浏览器通过。
2. 使用隔离 MySQL schema 与 Redis namespace，在双节点验证权限撤销、字典发布/停用/回滚及缓存故障的跨节点有效性。T18/T19 上轮 MySQL 1 项通过不代表本轮新增 guard 已在 MySQL 实测。
3. 配置服务仍沿用 Sample 的 `ent-acme` 边界；任意租户扩展、独立覆盖版本回滚不在本轮实现内。字典草稿项的修改在发布前不改变不可变发布快照。
4. 安全降级依赖权威元数据存储可访问。若数据库/权限存储本身不可用，拒绝读；不声称离线缓存仍可用。可选配置不可见时按缺省返回，权限与基础设施故障不会被转换成配置成功。
5. 本轮没有新增数据库迁移，依赖已有 T18/T19 的 V29/V30 和相应工作树代码。共享索引包含先前分工成果，因此保留未提交供主会话统一收口，勿把该文件清单直接作为独立于前置改动的可运行提交。

### 最终测试明细

| 测试类 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| AuthorizedConfigurationHttpTest | 8 | 0 | 0 | 0 |
| DataScopeCompositionHttpTest | 2 | 0 | 0 | 0 |
| DataScopeEnforcementHttpTest | 9 | 0 | 0 | 0 |
| DataScopeHttpTest | 2 | 0 | 0 | 0 |
| VersionedTwoLevelCacheTest | 3 | 0 | 0 | 0 |
| DictionaryHttpTest | 3 | 0 | 0 | 0 |
| DictionaryNotificationSseHttpTest | 3 | 0 | 0 | 0 |
| DictionaryVersionHttpTest | 3 | 0 | 0 | 0 |
| ResilienceDiagnosticsHttpTest | 2 | 0 | 0 | 0 |
| PluginConfigurationHttpTest | 7 | 0 | 0 | 0 |
| PluginLifecycleHttpTest | 3 | 0 | 0 | 0 |
| ResilientConfigurationHttpTest | 10 | 0 | 0 | 0 |
| ResilientReadServiceTest | 6 | 0 | 0 | 0 |
| SystemParameterHttpTest | 2 | 0 | 0 | 0 |
| SystemParameterQueryHttpTest | 2 | 0 | 0 | 0 |
| SystemParameterReleaseHttpTest | 2 | 0 | 0 | 0 |

## 2026-10-07 后端整合提交

本节记录上述修复后的选择性提交，前文“未提交”是首次复验时状态。以 T25 的 `5892b66` 为父基线，合并 T18/T19/T32/T35 后端及文档 45 文件，并接入基础参数发布测试的既有编译修正 1 文件；不纳入 Sol 前端后续改动、T36 初始化注解、应用配置、旧迁移或日志。通过临时 index 生成 Git 对象，提交前核对 HEAD 并以旧哈希作为 update-ref 的并发条件，真实 index 字节保留，绝不把 HEAD 内容回写源码。

提交前针对新共享安全链选择参数/字典授权、持久化范围、发布回滚、降级与插件共 7 个测试类复验；本次实际执行 **45 tests / 0 failures / 0 errors / 0 skipped**（授权配置 8、持久范围 9、发布回滚 2、插件生命周期 3、插件配置 7、HTTP 降级 10、降级单元 6），归档为 `.git/summer4-review-20261007/backend-integration/regression-results.json`。本次测试在共享工作树执行，候选树外的 T36 初始化注解和配置差异已单列保留，并未另建洁净副本运行测试；提交后的综合环境门禁由 T36 继续核验。没有改过的其余回归沿用前文 67 项证据，不将其冒充本次执行。真实浏览器与双节点门禁仍待 T36；固定 ent-acme 与独立覆盖回滚的原需求判断见 [T18/T19 边界复核](summer4-t18-t19-acceptance.md)。
