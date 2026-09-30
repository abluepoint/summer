# Issue #91：页面模式新增与编辑 Customer

## 需求追溯

| 验收要求 | 实现 | 验证 |
| --- | --- | --- |
| 创建和编辑使用可直接访问、可刷新的路由 | `/customer/page/create` 和 `/customer/page/edit/:username` 由页面模式承载，编辑总是向后端读取目标；两条路由均使用唯一共享 `CustomerForm` | `verify-customer-page.mjs`；真实 PostgreSQL 浏览器从地址栏直达并刷新 |
| 成功与取消返回完整发起列表 URL；无来源时回 `/customer/page` | 从列表/详情进入表单时把完整列表路径、query 和 hash 编码到页面 URL 的 `from` 参数；只接受 `/customer/page` 列表作为返回目的地 | URL 合同单测、真实浏览器创建/编辑返回分页筛选排序状态；直接深链接取消回默认列表；401 重登后仍保留来源 |
| 脏表单保护和键盘/窄屏 | 取消时用 Arco 确认框，SPA 导航用 history blocker，刷新/关闭用 `beforeunload`；提交期间禁止离开；卡片宽度随视口收缩，共享表单自动聚焦并在校验失败时聚焦首个错误字段 | 浏览器取消后继续编辑、后退/刷新选择留在页面、400 错误聚焦、390px 无水平溢出与键盘输入/Enter 提交 |
| 创建/编辑错误与抽屉一致 | 复用 `CustomerForm` 校验、差量 PATCH、字段错误映射和 `requestId` 复制；加载失败显示状态、诊断和重试；401 复用 #86 草稿提供者，恢复后要求人工重提 | 真实后端创建/PATCH/GET；受控 400、409、网络、编辑 404、401；401 后 PATCH 计数证明未自动重放 |
| 与其他模式保持复用边界 | 页面列表提供模式说明和 Drawer/Modal 入口；无第二个共享业务组件或新的服务契约 | 源码审阅、前端单测和真实浏览器回归 |

页面路由的 `from` 只允许本应用的 `/customer/page` 列表 URL，可保留该 URL 的原始 query/hash；其他值回退到 `/customer/page`。来源写入当前页面 URL，因此直接刷新与 401 导航后仍可返回发起列表。认证草稿仅通过 #86 允许的 Customer 字段保存；写请求只由用户再次点击提交触发。

## 验证记录

- `yarn test`：18 tests，0 failures，0 skipped；`yarn typecheck`、`yarn lint:style` 和 `yarn build` 通过。完整 `yarn lint` 为 0 errors，基线官方示例保留 40 条 warning。
- `yarn test:e2e:customer-page`：使用本 worktree 的 Summer Sample、独立 PostgreSQL 17 容器和 8091/5191 端口，Mock 未启用。浏览器通过真实 OIDC 登录和真实 Customer API，测试创建、编辑、完整来源返回、直达/刷新、脏取消/导航/刷新、窄屏键盘操作和持久化回读；受控错误注入覆盖 400、409、网络、404，以及创建和编辑两条 401 草稿恢复且不自动重放写请求。测试 Customer 已物理删除，数据库中测试前缀余量为 0。
- 本票先单独提交，再安全同步已合入的 #90 `417f989`。解决 `package.json`、locale、`CustomerScreen` 重叠后，`yarn test:e2e:customer-modal` 和 `yarn test:e2e:customer-page` 在同一 PostgreSQL 后端分别通过；两个模式的列表操作入口、说明和错误文案都保留。
- 共享表单与详情的 Drawer 回归 `yarn test:e2e:customer-edit` 同样在该 PostgreSQL 后端通过，覆盖列表编辑后二次读取、差量 PATCH、401 恢复、启停和删除；这不替代后续 #93 对 username 契约的修正。
- 页面创建桌面截图与 390px 编辑截图已目视检查：标题、字段、只读说明、保存/取消和主导航均完整可见，无裁切或水平溢出；截图是临时验收材料，不入库。
- `./gradlew check --rerun-tasks`：32 份 JUnit 报告，141 tests，0 failures，0 errors，0 skipped；本票没有改动后端生产代码。

## 双轴审阅

最终固定点为已合入的 #90 `417f9895840afbc71232dbfc8a118dfd881e50f8`，审阅 `git diff 417f989...HEAD` 的 #91 改动。用户要求每票独立会话且禁止子智能体，规范轴与需求轴均在本会话审阅。

- **规范轴：** 对照 `AGENTS.md`、`CONTEXT.md`、ADR 0014/0015/0016、既有 Arco 路由和 Customer 契约。页面使用 Arco Card/Alert/Modal 和既有唯一共享 `CustomerForm`，不新增业务 Mock、第二个共享组件或令牌持久化。
- **需求轴：** 对照 #91 与 #69 的 AC39–43、AC49–51、AC55、AC59。来源 URL、防外跳、草稿恢复和无自动重放均有单测或真实浏览器证据。审阅发现恢复的编辑草稿若遇目标加载 400/403，不应继续呈现可提交表单，已收窄显示条件。当前 Customer username 规则与 #69 文字规格存在已知偏差，按主任务要求留 #93 统一修正；页面沿用现有服务和表单规则。

## 回滚

回退 #91 独立提交可移除页面创建/编辑路由、返回地址处理、页面说明及本票测试/文档，不改变后端 schema。已创建或修改的 Customer 是业务数据，代码回滚不会撤销，需按实际业务记录另行处理。生产回滚应将前端静态产物切回上一已验证版本，同时保持与当时后端 issuer/redirect 配置一致。
