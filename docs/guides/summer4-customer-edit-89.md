# Issue #89：抽屉编辑 Customer

## 需求追溯

| 验收要求 | 实现 | 验证 |
| --- | --- | --- |
| 抽屉复用 CustomerForm；username 明确只读；仅提交变更字段 | 列表和详情的编辑入口打开同一 CustomerForm；username 只读且有说明；`changedCustomerFields` 仅产生变化的 email、displayName，空变更时禁用保存 | `verify-customer-form.mjs`；真实浏览器检查只读属性、无变化禁用、PATCH body 和持久化回读 |
| active 只能通过明确操作修改，不做行内切换 | 详情页使用启用/停用按钮与确认框，单独 PATCH `{active: boolean}`；列表只显示状态标签 | 真实浏览器停用再启用，逐次检查 PATCH body 与 GET 持久化状态 |
| 脏表单、404、删除中的草稿 | 取消/抽屉关闭时确认；路由离开与刷新前拦截；PATCH 404 显示目标消失但保留输入；目标被其他操作物理删除时草稿仍保留至明确放弃 | 浏览器取消后继续编辑、浏览器后退取消、受控 404、真实 DELETE 后提交 404 和草稿保留 |
| 401 后恢复但不重放 PATCH | 复用 #86 请求拦截器；认证前保存编辑目标、原始值和当前草稿；重新登录后恢复抽屉，用户再次点击才提交 | 真实 OIDC 重登，浏览器计数确认自动 PATCH 仅一次，恢复后手动提交并 GET 回读 |
| 保持 #88/#92、列表状态、焦点、权限和窄屏 | 保存后刷新当前列表而不重写 query；关闭后焦点返回编辑入口；写入口依实际 `customer.write` scope 显示，后端继续裁决；390px 抽屉全宽 | 编辑浏览器检查列表 query、焦点、窄屏输入边界与截图；#88 新增和 #92 删除浏览器套件各回归通过 |

## 验证记录

- `yarn test`：16 tests，0 failures，0 skipped；`yarn typecheck`、`yarn lint:style`、`yarn build` 通过。`yarn lint --quiet` 为 0 errors；完整 lint 保留官方示例的 40 条既有 warning。
- `./gradlew check --rerun-tasks`：32 份 JUnit 报告，141 tests，0 failures，0 errors，0 skipped；其中 `summer-sample` 36 tests。
- `yarn test:e2e:customer-edit` 在本 worktree 的 Sample H2 后端和 Vite 独立端口上通过真实 OIDC 登录、真实 PATCH/GET/DELETE。受控注入用于复现 PATCH 404 与 401；删除后的 404 来自真实后端。脚本覆盖列表/详情编辑、无变化禁用、字段差量、脏表单、路由离开、状态启停、认证恢复、窄屏和浏览器未捕获异常检查。测试 Customer 已物理删除。
- 同一后端运行 `yarn test:e2e:customer-create` 和 `yarn test:e2e:delete`，#88/#92 两条回归均通过；共享 email 合同修正后再次运行新增与编辑两条浏览器套件，均通过。390px 编辑截图目视检查：用户名、邮箱、名称、保存/取消和关闭入口可见，字段未裁切；截图不入库。

## 审阅证据

固定点为 #88 基线 `d5e04a1`，审阅 `git diff d5e04a1...HEAD` 所对应的 #89 独立改动。用户要求每票单会话且不用子智能体，规范与需求两轴均在本会话逐项复核。

- **规范轴：0 项阻断。** 对照 `AGENTS.md`、`CONTEXT.md`、ADR 0014/0015/0016、现有 Arco Pro 组件与 #86 请求认证边界。未新增共享业务组件、令牌存储或业务 Mock；权限仍以后端 `@PreAuthorize` 为准。页面私有 `CustomerScreen` 继续聚合列表、详情和多项操作，属于可观察的职责集中；当前只在既有屏幕内补齐本票交互。
- **需求轴：0 项阻断。** 对照 #89 和父规格 #69 的 Drawer、CustomerForm、错误及恢复条款。复核时发现初版无变化编辑仍可点击、脏编辑未保护浏览器离开，已修复并重跑浏览器验收。`active` 采用 #89 指定的明确确认操作，不使用行内 switch。401 后未自动重发写请求。

当前前后端 username 格式与 `CONTEXT.md`、ADR 0016、#69 文字规格不一致；本票复用 #88 表单和当前后端规则，不引入第三套规则，合同对齐留给 #93。审阅时同步修正共享新建转换的 email trim：新建和编辑现在都按原输入校验 email，不再静默改写。当前 Sample 浏览器联调使用 H2；PostgreSQL 约束、并发更新与完整三模式验收不由本票浏览器测试证明。后端无版本/ETag 并发保护，后写入可覆盖先写入的同一字段。

## 回滚

回退 #89 独立提交即可移除抽屉编辑、启停操作、草稿扩展和本票测试/文档；无需数据库迁移。已完成的 Customer 修改与启停状态是业务数据，代码回滚不会撤销，应按业务记录另行处理。#86 认证恢复、#88 新增和 #92 删除保持各自独立交付边界。
