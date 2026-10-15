# #142 授权事件的企业持久授权补救

2026-10-16。源修复、专属回归及新候选真实 MySQL/PKCE/Chrome 定点验收已完成，可交主会话整合 #142。原始失败和分批边界完整保留，Issue 关闭由主会话执行。

## 问题与变更

此前事件数据与重试使用查询参数 B，但持久角色拦截仍按固定 ent-acme 判定，控制器对 B 仅检查当前成员状态、JWT 能力和 scope。原生 native05 正式撤销 B 读取权限后，旧 JWT 仍收到 200，已在前一 UI 补救报告保留。

现在只对 `AuthorizationChangeEventController` 使用明确企业查询参数进行持久 decision。控制器的默认值与拦截器验证共用同一约定：省略或单个空值继续使用 ent-acme；非法标识或多个 enterpriseId 值在业务调用前拒绝。控制器原有成员、JWT 能力、scope 检查及事件所属企业约束保持。其他全局端点不采用此查询参数选企业。

生产修改限于 `AuthorizationRequestInterceptor.java`、`AuthorizationChangeEventController.java`。未改 AuthorizationService（由另一所有者处理 PLATFORM scope）、UI v2 五文件及旧统计报告。

## 测试分层

- red01：10/7/0/0；夹具重复创建 A 同码能力，部分失败不具有效授权前提，原记录保留。
- red02：修正夹具后 **11/5/0/0**，五项有效失败分别为同 JWT 的 B 读撤权、B 写撤权、B 角色移除，以及 A 拒绝误挡合法 B 读/重试。
- green01：**18/0/0/0**，其中专属 11、既有拦截器 6、既有事件 HTTP 1。独占共享 Gradle 锁与唯一 XML 输出，无自有源码漂移。

专属测试主要使用真实 H2 持久授权、合成 JWT 与 MockMvc；`anotherGlobalControllerIgnoresEnterpriseQueryAndKeepsSampleBoundary` 明确 mock AuthorizationService，仅验证非事件全局 Controller 的边界。既有拦截器测试也使用 mock。整 18 项不能称为真实 TCP、MySQL 或 PKCE。

断言覆盖 requestId/无数据、拒绝 retry 的 outbox 状态无变化、跨租户 eventId、现有成员/能力/scope 要求，以及缺省、空值、非法和重复企业参数。原 UI 54 项的 A 门禁补验不作为本后端修复证据。

## 新候选原生验证

固定 candidate02 SHA-256 `8a2ec5987b60d7356cf2207b964a0e878d8434f903e59e887500a866ad09fb7e`。543 候选输入、授权 Java 与实际源码匹配。主会话构建校验器最初把总迁移数误要求为 39；正式 `migration-count-correction.json` 更正为 MySQL39＋通用1＝40，资源逐字节一致，原失败记录保留。

首批 `native-01`：**117/0/2/0**。117 个 HTTP、夹具及进程归属断言通过，覆盖固定同一 JWT 的 B 读写撤权、角色移除、A/B 相反授权、跨企业 eventId、缺省/空/非法/重复参数和 requestId。三次拒绝重试及非法/跨企业重试前后 outbox 状态无变化。但空库尚未发布菜单清单，首次直接以 reader 登录的 Chrome 被真实路由守卫拒绝，0 项浏览器断言、1 个 TimeoutError，外层再记 1 个 error；不能写成整批绿。

补批 `native-02-browser-supplement`：**42/0/0/0**，其中 **5/0/0/0 真实 Chrome**。通过现有导出器从当前 routes 生成 version7/54 项真实 manifest，由管理员正式 PUT API 返回200后普通用户登录。没有跳过路由守卫、伪造网络或修改 token。浏览器验证 B 列表、持久撤销 B 后403/无数据/requestId、旧行清空及同一 JWT 保持，pageErrors=0、closed=true、父/Chrome exit0。reader 自动尝试发布 manifest 的403仍是合法拒绝，管理员预置才是本批菜单来源。

补批只重建必要夹具和 Chrome 撤权场景，没有重跑117项撤权矩阵。两批都无捕获源码漂移，JVM与UI监听PID归属核验通过，测试schema/账号/Redis容器均删除，五端口释放。正向和拒绝截图逐张目视通过。不得将117与42合并冒称一次全矩阵通过，也不把旧 UI A门禁54绿算作 B撤权证据。

## 复现与交接

脚本为 `scripts/acceptance/enterprise-page-integration/event-authorization.py`、`event-authorization-browser.mjs`。从仓库根设置新的 `SUMMER_T34_EVIDENCE_DIR`、固定候选绝对路径 `SUMMER_T34_CANDIDATE` 和真实 `SUMMER_T34_CANDIDATE_SHA256` 后执行 Python 脚本；`SUMMER_EP_BROWSER_ONLY=1` 仅运行必要夹具和 Chrome 补验。脚本使用正式 API 配置权限和事件，无业务 SQL 写入；SQL 仅只读比对 outbox。各轮执行脚本先归档，使用新目录并核验候选输入及监听PID，不可复用旧目录或旧 JAR。

源冻结、红绿 XML、旧 native05 结果/清理索引、两批新原生回执与最终清单位于 `.git/summer4-closeout-20261015/enterprise-event-authorization-fix-01/`。原 UI v2 五文件和 frontend03 保持，前一报告新增闭环链接，不改写其六批原始记录或旧统计11批。
