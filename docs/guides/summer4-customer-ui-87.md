# Issue #87 Customer 列表与详情交付记录

## 2026-09-30 验收补正（基线 `0d343d1`）

复核 #69 AC04/AC24 时，`/customer/list` 落入 wildcard 403；Customer 组件的
`getAuthenticatedUser()` 与列表或详情 GET 各自异步执行，已登录但缺少
`customer.read` 的 token 仍会发出读取请求。修正后的 `layout.tsx` 在 Customer
页面挂载前检查实际 token scope，缺少 read 时直接显示权限提示；无 token 时沿用
PKCE 单次恢复。`/customer/list` 在路由表中显式 replace 到
`/customer/drawer`，保留 query/hash。后端 `customer.read` 与
`customer.write` 的独立授权仍为最终裁决。

`yarn test:e2e:customer-scope` 在独立 PostgreSQL 16、当前 Sample bootJar、
Mock 关闭的 Vite 同源环境验证：write-only scope 进入 Drawer、Modal、Page、
详情、页面编辑、页面创建均显示 403 提示，Customer GET 计数为 0，未重新发起
OIDC 授权；别名保留 query/hash，后退回到原页面；read-only scope 可读取真实
Customer 列表，创建入口不显示。测试只替换浏览器内存中的 scope 视图，读取请求
和登录仍连接真实后端，不把它当作后端 write-only 授权证明。原有
`test:e2e:auth` 与 `test:e2e:customer` 在同一环境通过，分别验证 PKCE 安全
分支、真实列表/详情及受控 401/403/500。

本次 `yarn test` 为 19 tests、0 failed/skipped；`yarn typecheck`、
`yarn lint:style`、`yarn build` 通过；`yarn lint` 为 0 errors、40 条既有示例
warnings。`./gradlew check :summer-sample:bootJar` 成功，Gradle 部分任务来自
缓存；本次没有后端源码修改。以 `0d343d1` 为固定点做双轴审阅：标准轴核对
`AGENTS.md`、`CONTEXT.md`、ADR 0014/0015/0023 和既有代码风格；规格轴逐项核对
#87 及 #69 AC04/AC22–24/AC43–44。两轴均无未解决发现；无新增自定义共享
业务组件、协议端点改造或 token 持久化。回滚此补正只需回退本次 Customer
路由门禁、别名和浏览器测试；下方历史交付记录保持可追溯。

后续 #93 将公开详情深链接统一为 `/customer/detail/:username`，Sample 种子用户名
改为 `summer.demo`；以下表格保留 #87 交付时的历史验收记录。

## 需求追溯

| 验收要求 | 实现 | 验证 |
| --- | --- | --- |
| 真实列表、URL 分页/过滤/排序 | `summer-ui/src/pages/customer/CustomerScreen.tsx`、`contract.mjs` 和 `api.ts`；列表请求固定 `projection=detail`，页码与后端一致从 0 开始 | `verify-customer.mjs`；浏览器通过真实 OIDC 与 Sample H2 后端验证列表、筛选参数和空结果 |
| username 查询和详情深链接 | 三个 Customer 模式都支持 `/customer/{mode}/{username}`；返回列表保留查询串，公开路由不使用 `customerId` | `verify-customer.mjs`；浏览器点击 `demo`、返回列表及查询不存在的 username |
| 突出 username、email、name、active | Arco Table、Descriptions、Tag；窄屏以逐条 Descriptions 展示全部字段 | 浏览器桌面和 390px 截图目视检查；窄屏断言无水平页面溢出 |
| 401/403/404/500 与 requestId | 统一 `SummerApiError` 保留 HTTP 状态、业务码、原始消息、violations、requestId、timestamp；Arco Alert 显示分类提示及 requestId 复制按钮 | 后端匿名 `GET /api/customers` 返回 401；真实详情缺失返回 404/requestId；浏览器受控 401/403/500 响应验证恢复与提示，403/500 展示 requestId |

## 验证边界

`yarn test` 包含 URL、详情路由和信封解析测试，9 tests、0 failures、0 skipped。`yarn typecheck`、`yarn lint`（0 errors、40 条基线警告）、`yarn lint:style`、`yarn build` 分别通过。浏览器命令为 `SUMMER_E2E_PASSWORD_FILE=<本机私有文件> yarn test:e2e:auth` 与 `SUMMER_E2E_PASSWORD_FILE=<本机私有文件> yarn test:e2e:customer`，使用本地 Vite 5173、Sample 8080 和真实 OIDC PKCE；Customer 的成功、过滤及 404 来自真实后端。403/500 和一次性 401 为浏览器网络边界的受控响应，只验证前端错误展示与 401 重启，不代表 Sample 默认账号会产生这些授权/服务故障。

完整 `./gradlew test` 在当前 `summer4` 基线的 `:summer-sample:compileTestJava` 失败：`CustomerPersistenceTest` 缺少 `CustomerView`、`CustomerSummaryView` 导入（5 个编译错误，测试未运行）。#87 没有修改后端或测试源码，该失败单独保留，不记为通过。

## 审阅记录

审阅固定点为包含 #86 的 `summer4` 提交 `9858412`；按用户要求在本会话分别审阅仓库规范与 Issue #87 验收。范围仅含 Summer UI Customer 读取页面、必要的窄屏布局、测试和本文；无 `apps/aict-identity`、Customer 写接口、数据库或协议服务变更。按仓库 `CONTEXT.md`、ADR 0014/0015、Issue #87 核对了 username 与 email 的区别、业务信封与 OIDC 边界、官方 Arco Pro 的 Card/Table/Descriptions/Select/Pagination 使用方式。代码审阅还检查了 URL 输入白名单、路径编码、过期请求结果丢弃、无令牌持久化、错误元数据保留以及窄屏布局。Customer API 复用 #86 的 `configureBusinessApiAuth`，无第二套 401 重定向；标准轴与需求轴均未发现阻断项。

## 回滚

回退 #87 的独立提交即可恢复三个 Customer 占位页及原前端布局；没有数据迁移和后端变更。保留 #83 列表 API、#85/#86 认证链路及已有 Customer 记录。回滚后删除本票据新增的浏览器脚本与测试，同时恢复 `package.json` 的测试入口。
