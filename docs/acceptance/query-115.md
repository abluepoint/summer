# T07 #115 增强查询与参数筛选验收

状态：**READY_FOR_ROOT_REVIEW**。关联原票故事 10、14、29、31、32；依赖 #113 已由 root 确认关闭。本会话未提交、推送或关票，T08 尚未执行。

原查询/UI 证据固定生产 JAR SHA-256 `442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453`；新增独立 count 真实验收固定新候选 `993ad757888f39802a7c03c1509d20a2f191a8616c5025eaff2712cf2eff320a`。两批均使用真实 MySQL、PKCE 登录和 HTTP；旧批另有真实 Chrome。没有网络 mock 或业务 SQL 写入，机器收据见 [query-115-result.json](query-115-result.json)。

## 实际结果与计数

| 证据批次 | tests/failures/errors/skipped | 含义 |
| --- | --- | --- |
| attempt02 的 API/环境检查数组 | 375/0/0/0 | 真实 HTTP 与环境命名断言，保留后端绿证据 |
| attempt05-ui 的 Chrome 检查数组 | 19/0/0/0 | 修复后的完整原票 UI 流程 |
| 去重组合 | 394/0/0/0 | 分批组合；不是一次执行，也不是 JUnit 或 HTTP 请求数 |
| attempt05-ui 完整执行 | 131/0/0/0 | 112 项重复设置检查 + 19 项 Chrome，exit0 |
| 相关组件回归 | 5/0/0/0 | 合成身份/HTTP，单独计数；typecheck/build 均 exit0 |
| 新候选独立 count 真实补充 | 294/0/0/0 | count-actual-attempt01，exit0，含真实授权/夹具设置；单列，不与历史394相加 |
| count 专用与原列表 JUnit | 7/0/0/0 | green-attempt06，exit0，MockMvc/H2 与真实 Redis 就绪；不算实际HTTP |

组合不累加重复的 112 项夹具检查。attempt02 原整轮仍为 **375/0/1/0**：375 项后端检查通过，但当时浏览器漏选活动租户、零检查超时；原回执未被改成全绿。

## 原七条 AC 与证据

| 原 AC | 设计与实现 | 实际证据 |
| --- | --- | --- |
| 1 白名单组合过滤、排序、分页、投影 | Field contract → SystemParameterListQuery → 数据库 list/count SQL；UI 查询状态 | 24 行真实参数，9 组筛选/计数对照，6 种排序各 asc/desc，分页无遗漏/重复，summary/detail 与 UI 组合筛选 |
| 2 非法字段/排序与越权租户拒绝 | 参数白名单、请求租户一致性、成员/能力/数据范围授权 | unknown/sort/direction/projection/range/type/status/public/key 均 400；冲突租户、重复租户、异租户及显式读拒绝均 403 |
| 3 不依赖列序或隐式别名覆盖 | JpaSqlTemplate 使用 Tuple 与命名投影，别名归一化冲突拒绝 | 同一生产 JAR 加载独立 SQL 资源：详情/摘要列重排后结果相同；重复归一化别名均 400 SUMMER_PARAMETER_INVALID |
| 4 真实 HTTP 查询契约及隔离 | 显式租户 + 相同数据范围进入 list/count，详情独立核对 | 两个真实租户保存同键不同记录；各自列表与总数元数据隔离；24 条详情与列表逐条一致；9 组 totalElements 与 MySQL 只读 COUNT 一致 |
| 5 迁移与真实 UI 成功/拒绝/非法/故障 | 空的独立 schema 启动生产迁移；真实 API、Chrome | 筛选、分页重置、摘要切换、400 字段错误/requestId、403 清空、实际停后端/重启恢复均通过 |
| 6 公共参数/响应/关联与安全约定 | 既有 Field、Unified/Raw、Request correlation、SUMMER_ | 成功/失败信封与 X-Request-Id/body requestId 一致；凭据仅进程内/env/stdin，不写入收据或截图 |
| 7 故事—设计—实现—测试—缺陷—验收追踪 | 原票故事编号保留；原始失败与后续修复分别归档 | original-issue、AC-TRACKING、各 attempt、组件红绿、源码/产物事前备份、FILES/READY/摘要齐备 |

原范围要求“通过真实列表、详情和计数接口验证一致性”。旧批只验列表总数元数据，root 按原范围明确要求补独立 HTTP 计数；本轮已实现并在新候选完成实际验收，原 AC 文字未改写。

新增 `GET /api/v1/enterprises/{enterpriseId}/system-parameters:count` 与旧前缀别名 `/api/system-parameters:count`，数据严格为 `{totalElements}`。列表与计数共用解析器和 DataScope，Field 白名单、OAuth 和 ResourcePermission 相同；直接调用现有 `countSystemParameters` 命名 SQL，不读取列表行。SQL 资源、授权引擎及 CustomerController 未改；`:count` 路由保留合法 `/system-parameters/count` 详情键。

| 新候选独立 count 矩阵 | 实际结果 |
| --- | --- |
| 8 条本租户过滤夹具，10 组 key/type/status/public/组合及空结果 | 每组 count = 列表 totalElements = 夹具预期 = MySQL 只读 COUNT；响应无 items |
| page 0/1/8/99，size1，version desc，summary | count 始终8；列表当前页分别有1/1/0/0项 |
| 8 条详情与列表夹具、合法字面键 count、旧前缀别名 | 身份/值一致；count 仍走详情；别名总数8 |
| 第二真实租户保存同键不同记录 | 本租户8、另一租户1；另一租户列表/详情/计数一致，ID与本租户不同 |
| 12 类未知/非法参数 | 全部400 SUMMER_INVALID_ARGUMENT，已声明字段有字段错误 |
| 冲突/重复/query/header租户、实际异租户访问及显式读拒绝 | 全部403 SUMMER_FORBIDDEN；正常授权读者计数8 |
| 真实 API 写入 DataScope DENY，再恢复 TENANT | count/list均0、详情404；恢复后count8 |
| 所有成功/失败信封 | X-Request-Id 与 body requestId 一致，success与状态一致 |

本轮 **294/0/0/0、runner exit0、cleanup VERIFIED**，逐条名称唯一，未重跑旧375 API或19 Chrome。数量是含实际授权/夹具设置的命名断言，不是 HTTP 请求数；与旧批分列，不计算跨批总和。执行前后脚本相同，5个生产输入与候选534输入清单及运行前后摘要全部匹配。全仓926输入仅观察到 `MysqlSummerKeyStore.java` 同期变化，未触碰或归因；运行的是不可变新JAR，不宣称整个工作区稳定。

有效 JUnit 红灯为5/5/0/0，最终计数5项与原列表2项合计 **7/0/0/0，exit0**。测试身份漏管理能力声明已修正。详情测试曾触发测试环境200ms超时；中间5s设置已撤回，runner通过进程环境注入Redis凭据，并在断言前校验配置存在及真实PING=PONG，最终原200ms配置通过。生产详情、降级和超时未改，所有失败与中间结果保留。

## 本次最小修复

真实摘要页把缺失 `value` 显示成字面量 `undefined`。保留实际 Chrome 红灯后，组件取得 **3/2/0/0** 有效红灯，再仅修改 `summer-ui/src/pages/system-parameters/index.tsx` 的两处渲染：省略的值/版本显示“—”，后端摘要字段仍省略，详情中的 false、0、null 和空字符串保留。新增专用投影组件回归，相关组件最终 5/0/0/0，实际 Chrome 19/0/0/0。

源码与既有 dist 均事前备份；919 个冻结输入仅该获授权页面与最初基线不同，最终执行期间输入稳定。构建前后 241 个 UI 输入一致，该旧 UI 修复阶段 Java/JUnit 未改；本次 count 后端按授权修改5个生产文件及1个专用JUnit，用户 CustomerController 始终保留。页面随后完成 T10 历史刷新修复，并由 root 独立验证真实 Chrome 12/0/0/0；当前页面 SHA 为 `2e8304b8cecb5c452f50be45a96fa00e5e23bad1e374ef61d039e1ca1b6ef8e5`，此处旧 T07 原始回执保持原样。

## 复现与证据边界

新增计数专用入口为 `scripts/acceptance/t07/run-t07-count.py`。核实 Kubernetes/MySQL/Redis 后，设置新的 `SUMMER_CONFIG_EVIDENCE_DIR`、`SUMMER_FOUNDATION_CANDIDATE` 及其 `SUMMER_FOUNDATION_CANDIDATE_SHA256`，执行该 Python 文件即可。候选必须含新 count Controller，旧442会被入口拒绝；凭据仅在进程内从Kubernetes Secret读取，不能写进命令或文件。该入口只做真实API，不启动Chrome，也不需要重跑旧查询/UI绿路径。

受控入口：`scripts/acceptance/t07/run-t07.py`、`t07-browser.mjs`。沿用 `foundation/environment.py`、`foundation/ui-server.mjs` 与 `identity/grants.py`。先核对既有 Kubernetes/MySQL/Redis 依赖；设置 `SUMMER_CONFIG_EVIDENCE_DIR` 为新的独立证据目录、`SUMMER_FOUNDATION_CANDIDATE` 为候选 JAR 绝对路径、`SUMMER_FOUNDATION_CANDIDATE_SHA256` 为其摘要，然后运行 Python 入口。`SUMMER_T07_UI_ONLY=1` 只重复必要夹具和浏览器流程，不重跑已绿查询矩阵及 SQL 投影变体。

组件回归使用 Node22：`node --test --test-concurrency=1 scripts/verify-system-parameter-projection-browser.mjs scripts/verify-system-parameter-errors-browser.mjs`，从 summer-ui 目录执行并设置 `SUMMER_UI_PORT=20573`、独立组件缓存目录。合成组件证据不能代替实际联调。

所有 attempt 的自有 schema、账号、JVM、Chrome、UI/代理和端口均清理验证；Redis 仅清理随机精确 namespace，没有删除共享 Stream/group，也不声称清空全部 Redis。最终四张摘要、非法输入、恢复、拒绝截图已原生查看。证据位于 `.git/summer4-closeout-20261015/frontend/t07-115/`，原失败均保留：首轮漏建数据范围、漏选租户、误计 Arco 空行、一次截图超时及组件并行端口冲突均明确分类。
