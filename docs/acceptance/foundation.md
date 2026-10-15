# T02 / T03 真实基础验收入口

这些脚本面向本地 Summer Sample 的 MySQL、Redis、Chrome 和 Summer UI，使用真实 Spring 进程、SQL、OAuth PKCE 和浏览器请求。没有 HTTP 拦截、合成业务响应或零用例验收。脚本不提交、推送、关票或写 Git 索引。

## 运行前提

- macOS 本地 MySQL 可从 `127.0.0.1:3306` 访问；同实例也可通过 `kubectl exec -n local-base mysql-0` 管理。
- K8s `local-base` 中的 `mysql-auth/root-password`、`mysql-auth/app-password`、`redis-auth/password` 可读。脚本只在内存读取，MySQL/Redis 管理命令经 stdin 传递凭据；不会把凭据放入命令参数、源码或回执。
- 正常 Redis 是本地 K8s 转发 `127.0.0.1:6379`；故障演练只操作独立 Docker Redis 和独立网络黑洞，不修改正常 Redis 配置。
- JDK 21、Node 22、Chrome、Docker、kubectl、Python 3；前端依赖已安装。运行器中的 JDK/Chrome 默认位置对应此仓库的本地开发环境；Node 可用 `SUMMER_FOUNDATION_NODE` 覆盖。
- 端口 `20081/20082/20083/20573/20580` 必须空闲。脚本不会使用或停止用户的 `5173/8080`。
- 候选 JAR 已成功构建，提供完整 SHA-256。入口会核对后把同一份字节冻结到证据目录，避免其他任务更新共享构建产物。

从仓库根目录执行，三种验收**顺序运行**。不同运行使用不同证据目录；不要在同一前端目录并行启动 Vite。验收代理关闭 HMR，并使用各自证据目录下的 Vite 缓存，避免共享开发热更新和缓存干扰。

```sh
export SUMMER_FOUNDATION_CANDIDATE="$PWD/summer-sample/build/libs/summer-sample.jar"
export SUMMER_FOUNDATION_CANDIDATE_SHA256='<已核对的完整 SHA-256>'
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-t02-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/run.py
# 独立验证：只遗漏 datasource 配置，其余运行属性保持有效。
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-missing-config-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/missing-config.py
# 只在新建的隔离账号上验证禁止建库/授权。
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-permissions-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/permissions-probe.py
# 原票 scope：外部文件/环境优先级 + 实际数据库/迁移管理页面。
# scope_gaps 非空时返回非零，不能据其配置两项绿灯关闭T02。
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-scope-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/scope-probe.py

export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-t03-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/redis-run.py

# T04 UI/真实查询回放，不能代替后端组织上下文联动验收。
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-t04-ui-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/audit-run.py
```

每轮创建随机 `summer_foundation_*` schema、同轮专属 schema 账号、登录用户/角色/能力数据和会话 namespace。root 仅做该隔离环境的初始化、SQL 约束探测与清理；Spring 实际使用独立非 root 账号。不会调整现有应用账号或用户指定的 root 权限。此隔离账号配置不是生产部署账号迁移方案。

## 原始 AC 对照

| 票据 | AC | 真实证据 |
| --- | --- | --- |
| T02 #110 | 1：外部运行配置 / 本地 MySQL | Spring 以独立账号访问 `127.0.0.1:3306` 随机库；候选和资源回执 |
| T02 #110 | 2：Flyway 重复运行 / 数据保持 | 重启前后迁移 version/checksum/success 全量一致，修改后的停用客户仍可读取 |
| T02 #110 | 3：Customer 生命周期 | Chrome 创建、编辑、停用，重启后恢复、永久删除；用户名始终不变 |
| T02 #110 | 4：唯一 / 分页 / 排序 / 错误 | MySQL 直接插入重复用户名、邮箱均得到 1062；HTTP 409；升降序和跨页不重叠 |
| T02 #110 | 5：真实 HTTP / UI | 未认证拒绝、参数 400、真实页面校验、409 的 requestId 与草稿保留；真实 PKCE |
| T02 #110 | 6：框架契约 | 统一错误、SUMMER_ 错误码、头/体 requestId 一致；DELETE 空体 204；协议登录真实执行 |
| T02 #110 | 7：追溯 / 保留用户改动 | 候选 hash、命名检查结果、浏览器步骤、清理回执；未修改 CustomerController |
| T03 #111 | 1：地址/超时/凭据外置 | 配置只在子进程内存；正常 K8s Redis、隔离 Docker Redis；超时只改独立实例 |
| T03 #111 | 2：诊断 UI 四态 | Chrome 显示 UP / AUTHENTICATION_FAILED / UNAVAILABLE / TIMEOUT，再恢复 UP |
| T03 #111 | 3：脱敏 / 指标 | 响应只固定端点名与允许指标；每态扫描秘密/URI；健康只允许 status/groups |
| T03 #111 | 4：真实 Redis / 故障 | 真 Redis 错误密码、停止独立实例、网络黑洞；Spring 自行产生状态，无 HTTP mock |
| T03 #111 | 5–7：契约 / 安全 / 追溯 | 每态头/体 requestId；普通读者 403 另见 audit 回放；凭据续取单元回归、构建和清理证据 |

脚本中的检查数量不等于浏览器步骤数量，二者应分别报告。`run.py` 是 T02 独立入口；T03 不等待 T04 组织审计缺陷。`audit-run.py` 创建 27 次真实客户变更，验证流水第二页、主体/资源/动作/requestId 筛选及详情。未选组织时详情显示 `—`，不会把空组织伪造为默认组织。

## 回执、错误与清理

- `candidate-provenance.json`：确切候选；`resources.json`：无凭据资源清单。
- T02：`result.json`、`browser-before.json`、`browser-after.json`、`browser-extra.json` 和截图。
- T03：`redis-runtime-result.json`、`redis-browser-result.json`、各状态截图；必须同时检查恢复、浏览器退出、健康脱敏和清理。
- T04：`audit-runtime-result.json`、`browser-audit.json`、普通读者/页面错误检查和截图。
- `cleanup.json`：schema、专属账号、已知端口均释放。T03 还检查实际故障容器已删除。失败入口返回非零；保留失败证据，使用新目录重跑。
- Python 的 `finally` 会终止本轮子进程、关闭网络黑洞、删除隔离容器/库/账号；正常 Redis 内仅清理本轮随机 `summer:foundation:<run>:*` 会话/index namespace，不执行 FLUSHDB，也不清理其他前缀。若宿主进程被强杀，按 `resources.json` 只清理本轮随机 schema/账号/容器，不批量杀 Java、Chrome 或清理共享数据。

现有运行账号可以具有更高数据库权限。T04 的要求是应用审计追加与只读查询 API；本脚本不会把防 DBA 修改扩展成验收门槛，也不声称能防数据库管理员。

组织审计联动可独立回放：

```sh
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-context-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/context-probe.py
```

该探测先通过真实上下文 API 选择组织，再创建客户、读取流水，并探测不支持的审计写方法。应核对候选对应的持久成员与组织分配前提；不能使用过期 JWT 组织、请求参数或固定默认组织替代当前有效上下文。旧候选的已选组织丢失/不支持方法 500 红灯由后端负责会话修复，不能用 UI 回放绿灯覆盖。

## 2026-10-15 历史阶段复核（9430候选）

候选 SHA-256：`9430afd3b716ece9abe7423c90d8426c2e4d7fb2399b65be2d55738c6fdb86d4`。T02/T03 原始矩阵均无声明依赖；T03 不等待 T04。

- T02：20/20 命名检查，3 段真实浏览器流程（分别记录 4/4/3 个步骤，含各自登录），页面错误 0；另有建库/授权拒绝 2/2（MySQL 1044）及仅缺 datasource 配置的安全拒绝探测 1/1。客户在真实 JVM 重启后仍保持修改内容和停用状态，Flyway 历史一致。
- T03：真实页面依次显示 UP、认证失败、不可用、超时、恢复 UP，共 5/5；健康脱敏 5/5，页面错误 0，浏览器正常退出；实际隔离故障容器、随机库、专属账号及端口已清理。普通无角色用户的诊断 HTTP 403 和页面拒绝在同候选的审计回放中通过。
- UI/契约：npm 19 项命名测试及源码契约通过，另有 6 项导航、3 项诊断凭据契约；typecheck/build 退出 0。上述数量不跨 AC 重复累加。菜单中审计/Redis 权限码已与后端对齐，manifest 版本 4；诊断每次从内存读取当前 Bearer 凭据，保留原有服务端授权边界。
- T04 UI：27 次真实客户变更、8 项 HTTP/SQL 检查和审计筛选/分页/详情、普通读者、冲突页面回放通过。该结果不能覆盖已选组织丢失和不支持方法 500 的旧候选红灯；后端修复及新候选复核由主会话负责。

原始 JSON、截图与日志保留在本地 `.git/summer4-closeout-20261015/frontend/foundation/`；`T02-READY.json`、`T03-READY.json`、`AC-MAPPING.json`、`FILES.json` 分别提供状态、原始 14 条 AC 对照和精确文件摘要。总交付清单为上层 `combined-handoff-final.json`。不提交原始回执、候选 JAR、密码或会话快照。

原票配置文件优先级已在后续scope复核中通过；管理页的旧权限码和迁移状态缺失是已复现实现缺口，T02不得据客户CRUD绿灯关闭。现有 root 部署账号未调整，随机隔离账号用于证明应用无需建库或授权权限。CustomerController 的用户格式改动未修改。未提交、推送或关闭票据。

### T02 原票 scope 复核补充

同一9430afd候选两次真实JVM启动证明：外部properties文件（仅含环境占位符、无凭据值）覆盖包内配置；环境变量覆盖外部文件中故意不可用的URL/账号/密码，2/2通过。正常随机库和独立账号始终不变。

**T02仍为NEEDS_SCOPE_IMPLEMENTATION**：平台诊断读权限调用runtime API为200，访问运行监控页面却被旧`menu.diagnostics.runtime.read`菜单条件拒绝。在本轮隔离角色临时显式补该旧权限后，真实页面显示db=UP，但没有迁移状态；runtime API只有checkedAt/components/metrics/requestId，无迁移项。既有`/internal/v1/release/readiness`已真实返回database/migrations检查通过，可以供后续实现复用。临时夹具授权仅用于检查，不是产品修复；前端业务源码保持冻结。

具体边界：`summer-ui/src/routes.ts`的diagnostics/runtime项；`summer-ui/src/pages/diagnostics/runtime/index.tsx`及api.ts；`summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RuntimeDiagnosticsService.java`及RuntimeDiagnosticsModels.java。不把迁移表SQL读取或release readiness API绿灯替代所需管理页面。回执在`foundation/reusable-scope/`，两段浏览器pageErrors均0，资源及随机Redis namespace已清理。T03的独立READY不受此缺口影响。

### 最终固定候选回放

固定候选`4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae`已完成：T03五态/恢复5次、健康脱敏5次和正常退出；T04有效持久成员及组织分配前提下的上下文审计7/7、普通审计HTTP检查8/8、27次实际客户变更及对应页面回放。未选组织为null，选组织后五类操作均记录org-engineering，PATCH/DELETE为带Allow/统一信封的405，原事件未改变。全部无HTTP mock，页面错误0，独立schema/账号/故障容器/端口/随机Redis namespace均清理。

`final-t03/`、`final-t04-context/`、`final-t04-audit/`及`T04-REPLAY.json`区分最终回执与旧attempt失败。T04回放绿灯不能越过尚未闭合的T02依赖；T02原scope的两个UI实现缺口保持显式，不改已冻结前端业务源码。此前旧候选红灯保留用于追溯，不能继续当作最终候选结果。

### T02 范围补齐（2026-10-15 授权续批）

主会话已取消等待旧批次提交的限制。本次边界为 runtime 诊断服务与专用 HTTP 测试、运行监控页面/语言、对应路由和 manifest，以及本目录的验收脚本；不修改授权、会话、审计、资源目录或组织成员。

runtime 路由使用 `platform.capabilities.read`，与 `GET /internal/v1/diagnostics/runtime` 一致；manifest 升为5。接口保持 checkedAt/components/metrics/requestId 形状，在组件列表中增加 migrations；管理页面展示“数据库”“数据库迁移”和中英文状态。数据库组件继续使用实际健康检查。

迁移状态契约：`UP` 表示校验成功、无待执行且有已执行迁移；`NOT_ENABLED` 为显式关闭；`UNAVAILABLE` 为组件缺失或读取失败；`PENDING`、`VALIDATION_FAILED`、`NOT_APPLIED` 分别表示有待执行迁移、校验失败和无已执行记录。后五者均不视作成功。SDK 只用于产生固定状态，异常不回显，接口不返回 JDBC 地址、账号、迁移 SQL 或原始异常。原权限、统一信封和 requestId 保留。

新版 scope 探测不再授予旧菜单能力：两个真实 JVM 配置优先级检查；runtime 数据库/迁移健康、脱敏及 requestId；未认证拒绝；普通读者 API403；标准权限真实页面/刷新；普通读者页面拒绝；再只在本轮随机库中临时改变一条 Flyway checksum，验证 API/页面校验失败，恢复 checksum 后验证恢复，最后重启为显式关闭 Flyway，验证 API/页面“未启用”。共12项，均须通过，浏览器不得有 pageErrors，独立资源及随机 Redis namespace 必须清理。

```sh
export SUMMER_FOUNDATION_EVIDENCE_DIR="$PWD/.git/foundation-scope-$(date +%Y%m%d-%H%M%S)"
python3 -B scripts/acceptance/foundation/scope-probe.py
```

先按本指南设置新候选绝对路径与 SHA-256；不要把4c81或9430候选当作包含本次实现的新制品。完整 Customer 回放继续用 run.py；受限账号与缺失配置探测分别执行 permissions-probe.py 和 missing-config.py。旧 T02-GAPS、reusable-scope 及本次红灯 JUnit 均保留为修复前证据。新源码须另建固定候选；真实 scope 与完整 Customer 回放完成前，T02 保持未完成，T04 依赖亦未关闭。
