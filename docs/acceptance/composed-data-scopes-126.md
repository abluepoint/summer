# T18 组合数据策略与有效权限预览验收

对应 [Issue #126](https://github.com/abluepoint/summer/issues/126)、用户故事 30–33，依赖 #125 已关闭。2026-10-15 最终结论：**READY，七项 AC 通过**。实际发现并修复签发器遗漏活动租户/组织声明的缺陷，新候选双租户流程已通过；原始失败和独立绿色批次均保留。

首轮候选 SHA-256：`a9a6d65d782497e24164163b35f2f0680fbe7c587bb6c2384390723f1d0294c3`。修复后候选：`9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af`。本次唯一生产修改为 `LocalConsoleTokenConfiguration` 的既有白名单增加两个声明；新增专用 `LocalConsoleTokenContextTest`。DataScope 后端、UI、迁移未改，未暂存、提交、推送或操作 Issue。

新候选经协调窗口及共享 Gradle 锁构建，534 个主源码/资源/配置/构建输入前后无漂移，迁移逐字节匹配，旧 JAR 已保存。相对 a9 有两个生产输入变化：本票声明修复及 T29 所有者的 `TaskProgressEventService` 修复；T29 变更不归本票，不将该制品称为只有声明修复。10 个 DataScope 主源码未改，复用 T17 的 16 个后端输入也与两个候选及当前源码逐项匹配。最终仅声明和 DataScope 相关输入保持冻结，不扩称并行全仓始终不变。

## 原票七项 AC

| AC | 状态 | 实际证据 |
| --- | --- | --- |
| 1. 条件树版本、校验和及审计 | PASS | PUT 后版本递增；相同内容 checksum 相同但版本递增；从 MySQL 保存字节按原算法计算 SHA-256，与响应一致；审计版本、checksum、操作者、requestId 匹配；审计 INSERT 故障时策略版本/checksum/审计条数回滚，同 JVM 恢复 |
| 2. 拒绝原始 SQL 或未验证字段 | PASS | 原始 SQL、未知条件字段/属性/运算符、field/type 冲突、两个树输入、错误 IN 形态均真实 400；非法请求后版本/checksum/审计不变。T17 的 SQL 字面量与租户边界证据继续适用 |
| 3. 用户显式拒绝优先且可解释 | PASS | 两个已分配角色范围并集再与资源策略相交；USER 拒绝最后相减；SQL list/count、Repository detail 和逐行目标主体 preview 一致。返回 USER_DENY、角色和用户绑定来源；实际页面保存用户拒绝并显示解释 |
| 4. 管理端指定用户和租户的最终范围 | PASS | 新 token 包含实际选择的活动租户和组织；租户菜单 GET200；同一目标用户在 A 租户显示 USER_DENY，切 B 租户显示 POLICY_ALLOW 且无继承绑定，切回 A 恢复拒绝；旧预览随切换清除 |
| 5. 迁移、实际 HTTP/MySQL/Chrome 与失败路径 | PASS | 既有 T17 迁移及基础范围证据复用；本次首轮 123 项后端检查、独立 18 项配套/故障检查通过，单租户 Chrome 6+2 项通过；新候选配套 HTTP22、真实双租户 Chrome5 全绿 |
| 6. 公共契约、错误与敏感信息 | PASS | 实际请求核对 Unified success、请求/响应 requestId 和 SUMMER_ 错误码；凭据只在内存/env/stdin，运行日志检测未命中敏感值；无网络 mock；补声明保持原 OAuth scope 和客户端/身份来源限制 |
| 7. 故事—设计—实现—测试—缺陷—证据 | PASS | 本文及同名 JSON、原失败、运行时脚本副本、源码/候选 SHA、截图与清理收据完整保留；七 AC、精确文件清单和最终冻结摘要一致 |

设计依据：[ADR 0028](../adr/0028-enforce-effective-configuration-scopes.md) 的统一条件语义及 [ADR 0031](../adr/0031-resolve-configuration-tenants-and-version-overrides.md) 的显式租户规则。实现落点为 `DataScopeExpression`、`DataScopeService`、策略/绑定/审计实体与 Repository、`DataScopeController` 及 `summer-ui/src/pages/data-scopes/`。基础范围实测见 [T17 报告](data-scopes.md)；本次没有重复其 279 项后端矩阵，也不将历史 JUnit 算成本轮执行。

## 有效红灯与最小修复

通过真实管理 API 将管理员分配到组织并选择 ent-acme 上下文，PUT 返回 200；重新表单登录并执行 PKCE。脱敏诊断证实 access token 有两个 ACTIVE 成员租户，menu.dataScopes.read 和 platform.menu.write 均已授予，但没有 active_enterprise_id 和 organization_context。Chrome 的平台菜单发布 PUT200，却没有发出租户菜单 GET，最后进入拒绝页，条件树编辑器没有加载。

`LocalConsoleIdentityClaims.resolve` 已生成这两个声明，`LocalConsoleTokenConfiguration.BUSINESS_CLAIMS` 白名单却未复制。发现时共享身份/OAuth 超出授权边界，先保存红灯；协调方明确扩展精确两文件授权后，备份源码、编译类及既有 UI dist 共 147 文件，再取得专用有效红灯并修复。只补两项服务端声明，不改变客户端、身份来源、scope 或其他 OAuth 条件，不增加任意租户 fallback。

专用单元回归初次 import 包名错误导致编译失败、零用例，原结果保留；纠正后有效 **4/1/0/0**，修复后 **4/0/0/0**。以真实 JwtEncodingContext 和受控 resolver 验证声明复制、scope 不扩张、失效上下文不复用及其他客户端/身份来源不复制；该单元验证不冒充真实签发。新候选真实表单/PKCE/资源服务器/Chrome 的 **22 项配套 HTTP 与 5 项浏览器检查**全部通过。

最初将拒绝页归于夹具未选活动上下文；第二轮补齐后仍失败，第三轮布尔诊断确认真实缺陷，撤回此前不完整解释。首轮本就存在租户歧义，不能单独用它证明声明复制缺陷。精确红灯与候选信息保存在 `.git/summer4-closeout-20261015/t18-live/`。

## 实际执行批次

数量依次为 tests / failures / errors / skipped。HTTP/MySQL 表中的 errors 包含上层浏览器编排失败，不能把整轮 exit1 写成通过。重复初始化不累计为独立业务覆盖。

| 批次 | HTTP/MySQL/配套 | Chrome | 结论 |
| --- | --- | --- | --- |
| attempt-01-a9a6d65d | 123 / 0 / 1 / 0 | 1 / 1 / 1 / 0 | 已完成后端检查均通过；浏览器拒绝页，整轮 exit1 |
| attempt-02-ui-a9a6d65d | 21 / 0 / 1 / 0 | 1 / 1 / 1 / 0 | 补真实活动组织上下文后仍拒绝，未重复后端矩阵 |
| attempt-03-ui-diagnostic | 21 / 0 / 1 / 0 | 1 / 1 / 1 / 0 | 脱敏声明与菜单请求诊断确认共享签发缺陷 |
| attempt-04-single-tenant-ui | 18 / 0 / 0 / 0 | 正常 6 / 0 / 0 / 0；故障 2 / 0 / 0 / 0 | 独立完成单租户页面及故障路径，exit0 |
| attempt-05-multi-tenant-fixed | 22 / 0 / 0 / 0 | 5 / 0 / 0 / 0 | 9f0b 新候选：上下文→新 token→菜单→指定用户/租户预览→切回，exit0 |

前三轮失败路径采用有界强制回收；第四轮两次 Chrome 及第五轮 Chrome 均正常退出，forced=false、pageErrors=0。五轮自有 schema、应用账号、Redis 容器、JVM/Vite/Chrome 及 21081/21082/21083/21573/21580 均已核验清理，运行敏感检测均为 false。原始结果不重写，各轮脚本保存在对应 executed-scripts 目录。没有把各批必要初始化跨批盲加成一次执行，也没有重跑已绿的 123 项后端矩阵。

审计故障通过隔离库 summer_data_scope_policy_audit 的受控 BEFORE INSERT 触发器制造，真实写入返回 500；删除触发器后同 JVM 成功保存。没有通过 SQL 插入业务行、策略、绑定或授权数据，没有 mock 故障响应。回滚检查核对策略版本、checksum 和该资源审计条数，不扩称验证了全部审计/outbox 表。

拒绝提示与故障 requestId/草稿截图已目视。第四轮 preview-user-deny.png 为视口截图，详情位于下方，不能独立证明完整预览卡片。第五轮新增 multi-first-tenant-deny.png、multi-second-tenant-allow.png，逐张目视确认目标主体、租户、USER_DENY/POLICY_ALLOW 和绑定来源；第一张保留滚动时固定栏位置，第二张有下拉关闭动画残影，不将其当静态版式作品。

## 复验入口

脚本位于 `scripts/acceptance/composed-data-scopes/`。前提为固定候选、Java21、Node22、真实 Chrome、已有 UI 依赖、docker-desktop/local-base MySQL 和本机 Redis 镜像。每次使用新证据目录，避免共用专用端口。

```sh
export SUMMER_COMPOSED_SCOPES_CANDIDATE="$PWD/.git/summer4-closeout-20261015/t18-live/frozen-build-01/candidate.jar"
export SUMMER_COMPOSED_SCOPES_CANDIDATE_SHA256=9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af
export SUMMER_COMPOSED_SCOPES_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t18-live/replay-ui"
export SUMMER_COMPOSED_SCOPES_MULTI_TENANT_ONLY=1
python3 scripts/acceptance/composed-data-scopes/ui-run.py
```

run.py 包含完整 T18 增量矩阵；ui-run.py 仅执行必要初始化及页面。MULTI_TENANT_ONLY 选择最终双租户最小重放，不设时包含保存和审计故障。SINGLE_TENANT=1 仅用于隔离共享缺陷，不能替代 AC4。实际首轮后端/单租户证据绑定 a9，双租户最终证据绑定 9f0b，不声称整套矩阵在新候选重跑。
