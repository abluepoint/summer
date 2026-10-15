# T17｜基础行级数据权限验收

对应 [Issue #125](https://github.com/abluepoint/summer/issues/125)，父方案 #108 用户故事 29–33。2026-10-15 完成本票验收与修复交接；提交、推送和关票由协调方处理。

本次唯一生产修改是数据范围页面保存成功后清除先前的错误状态。后端没有修改。固定生产候选 SHA-256 为 `993ad757888f39802a7c03c1509d20a2f191a8616c5025eaff2712cf2eff320a`；16 个 DataScope、参数查询和服务相关源码与候选构建输入逐一匹配。

## 逐条 AC 与用户故事

| AC | 故事与实现落点 | 实际证据 | 结论 |
|---|---|---|---|
| 1 所有目标查询自动带租户边界 | 29、32；DataScopeExpression 无条件附加 TENANT，SystemParameterQueryService 统一注入 ScopeFilter，SQL 查询及独立 count 绑定同一条件 | 同键不同真实租户分别保存；当前租户详情、列表及计数不包含外租户记录；OR 与 SQL 字面量不能绕过租户 | 通过 |
| 2 本人、组织、部门、负责人等范围一致 | 29、30、32；同一表达式驱动 SQL/JPA/预览 | TENANT、SELF、CREATOR、OWNER、ORGANIZATION、DEPARTMENT、ORGANIZATION_TREE；列表、详情、独立 count、summary 多行读取；越界 PATCH/disable 返回 404，SQL 回读 version/value/status 未变；创建受拒绝规则约束 | 通过 |
| 3 未配置敏感范围默认拒绝 | 33；DataScopeService.filter 缺策略拒绝 | 真实第二租户，先清空其他租户组织上下文并重新登录，再验证缺策略 list/count/detail/create 均 403；配置 TENANT 后成功创建 | 通过 |
| 4 真实用户、真实租户无越权 | 29、30；真实身份、成员、角色绑定与组织分配 | 两个真实用户、两个组织、两个真实租户；真实表单登录 + PKCE；组织切换并重新登录；角色允许与资源范围相交，用户拒绝优先；重启后同样生效 | 通过 |
| 5 迁移、真实 HTTP、真实 UI，含失败路径 | 29–33；既有 V15/V19/V29 | 三条迁移 success=1；后端 279/0/0/0；最终共享工作区 Chrome 6/0/0/0；无效 JSON、服务端无效条件、无权管理、成功保存及刷新预览；UI 复验配套 HTTP 6/0/0/0 | 通过 |
| 6 公共协议与敏感信息 | 31、32；结构化条件、既有 Parameter/Unified/Raw/RequestId | 请求逐一检查状态、success、响应头/体 requestId；错误为 SUMMER_；原始 SQL、非法比较器、空条件树拒绝且版本/checksum 不变；运行日志敏感值检测 false | 通过 |
| 7 完整证据、实际数量、保留改动 | 29–33；本文与同名 JSON、独立验收脚本 | 各批次、夹具错误、有效红灯和最终绿灯分别留存；前后 SHA、源码备份、清理回读；未操作真实 index、提交、推送或 Issue | 通过 |

批量读取在现有列表接口的 `projection=summary` 多行读取路径验证；仓库当前没有独立的按 ID 批量读取接口。系统参数是本票选择的实际业务资源，符合原票“系统参数或字典”的范围。SELF、CREATOR、OWNER 的实际创建人和负责人由服务端身份生成；本轮没有通过 SQL 改写业务行或伪造 owner。组织上下文切换在重新登录取得新令牌后验证，不声称旧令牌即时更新。

## 有效红灯与最小修复

真实 Chrome 流程：输入不支持的比较器，PUT 返回 `400 / SUMMER_DATA_SCOPE_INVALID`；改为 TENANT 后 PUT 返回 200、策略版本增至 v12，但页面仍保留上一次错误警告，同时显示保存成功。浏览器该批次为 **6/1/0/0**，截图已人工目视。

`summer-ui/src/pages/data-scopes/index.tsx` 的保存成功回调增加 `setError(null)`。同一流程复验后旧警告消失；无效 JSON 不发送写请求，真实服务端错误仍展示 requestId，无权限用户不能加载编辑器。

最终 UI 使用当前共享工作区，记录 HEAD、绝对 cwd 及数据范围/菜单/主入口/布局/路由共 10 个相关源码 SHA。测试前后及交接核验全部一致。最终 Chrome 正常退出，`forced=false`。本轮未执行 JUnit；没有把历史 MockMvc 或零用例计入真实验收数量。最终共享工作区 TypeScript 检查退出码为 0。

## 批次与边界

以下数量均为执行脚本实际完成的命名断言检查，依次为 tests/failures/errors/skipped，HTTP/SQL 与浏览器分别列示。

| 批次 | 后端/配套 HTTP | Chrome | 说明 |
|---|---:|---:|---|
| initial-993ad757-01 | 20/1/1/0 | 未执行 | 夹具遗漏组织读取权限，context PUT 正确 403；没有进入业务矩阵 |
| initial-993ad757-02 | 204/1/1/0 | 未执行 | 跨租户时仍持原组织上下文；修正夹具后单独重验默认拒绝；该批不能用于证明缺策略拒绝 |
| initial-993ad757-03 | 279/0/0/0 | 6/1/0/0 | 后端最终有效证据；UI 独立提交快照发现旧错误残留。脚本当时漏将 browserExit 纳入进程退出门禁，故 exit 0 不代表整批通过；结果红灯原样保留，门禁已修正 |
| ui-fixed-993ad757-01 | 6/0/0/0 | 6/0/0/0 | 独立快照修复验证，单独保留，不作为最终共享工作区证据 |
| ui-shared-final-993ad757-01 | 6/0/0/0 | 6/0/0/0 | 当前共享工作区最终 UI 验收，typecheck 0，相关 SHA 无漂移，exit 0 |

原始结果、执行时脚本、截图、候选和 UI 来源记录位于 `.git/summer4-closeout-20261015/t17-live/` 的对应批次。所有批次的独立 MySQL schema/应用账号、Redis 容器、JVM 与使用端口均完成清理回读。最终使用 23081、23573、23580；不占用协调方端口。

早期检查曾观察共享 `menu/access.ts` 不存在，随后重新核验已存在；未恢复或覆盖该文件。按协调要求最终只使用共享工作区。菜单、密钥、参数 queryParser/count 和 CustomerController 均未由本票修改。没有将组织上下文隔离返回的 403 或夹具遗漏权限误报成产品漏洞。

复现入口见 `scripts/acceptance/data-scopes/README.md`；完整后端已绿后，本次仅重放 UI 修复，不重复整套后端。
