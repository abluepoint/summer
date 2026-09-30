# Issue #90：弹窗新增与编辑 Customer

## 需求追溯

| #90 / #69 条款 | 实现 | 验证 |
| --- | --- | --- |
| #90 共享表单与服务；#69 AC48、AC51 | Modal 创建和编辑均使用既有 `CustomerForm`，沿用同一 API、字段校验、错误映射、差量 PATCH、无变化禁用和 `customer.write` 门禁；成功后关闭并刷新当前列表，不改写 URL query | `yarn test` 表单契约；`test:e2e:customer-modal` 真实 POST/PATCH/GET、请求 body、焦点与 URL 断言 |
| #90 遮罩、键盘、焦点、关闭确认；#69 AC50、AC59 | Arco Modal 显式启用 focusLock、autoFocus、maskClosable 和 escToExit；脏表单的取消、遮罩、Esc、路由离开与刷新经过确认；关闭后返回触发按钮 | 浏览器 Tab 焦点约束、Esc/遮罩继续编辑、取消确认、后退拦截、关闭后的焦点检查，桌面与 390px 视口 |
| #90 三模式一致性；#69 AC39–AC44 | Modal 直接复用 Drawer 已用的 `CustomerForm`；`CustomerScreen` 中两模式共享草稿恢复、成功/失败处理和列表数据源。页面模式的最终实现由 #91 接入同一表单 | 浏览器验证客户端错误、受控 400 字段与表单级错误、409 冲突、创建和编辑的 401 草稿恢复且写请求不自动重放；后端真实持久化回读 |
| #69 AC55 | Modal 页面显示六项评价维度和 Drawer、页面模式入口 | 桌面截图目视检查与浏览器导航 |

## 验证记录

- 浏览器脚本使用独立 8090/5190 端口；核对监听进程、后端工作目录、健康端点及 OIDC issuer 都指向本 worktree。真实 Sample H2 后端处理 OIDC、Customer POST/PATCH/GET/DELETE；401、400、409 由浏览器网络边界受控注入，用于验证前端错误与认证分支。测试 Customer 最终以真实 DELETE 204 清理。
- 浏览器检查桌面及 390px 截图：字段、操作按钮和关闭入口完整可见，弹窗未横向溢出；Tab 焦点停留在弹窗内。页面无未捕获异常。截图、临时密码和临时端口配置不入库。
- `yarn test`：16 tests、0 failures、0 skipped；`yarn typecheck`、`yarn lint:style`、`yarn build` 均通过。`yarn lint` 为 0 errors、40 条既有官方示例 warning；生产构建有既有 chunk 大小等 warning，不影响退出状态。
- `./gradlew check --rerun-tasks`：47 tasks 执行；32 份 JUnit 报告合计 141 tests、0 failures、0 errors、0 skipped，BUILD SUCCESSFUL。
- 当前浏览器联调的 Sample 使用 H2。完整 Gradle 检查覆盖现有 PostgreSQL Testcontainers seam，但本票浏览器测试不把 H2 路径声称为 PostgreSQL 浏览器验收。全三模式端到端门禁须在 #91 集成后完成。

## 双轴审阅

固定点为 #89 基线 `09d5d29`；审阅本票 `git diff 09d5d29...HEAD`。用户要求本会话独立且禁止子智能体，因此规范与需求两轴在本会话分别审阅。

- **规范轴**：对照 `AGENTS.md`、`CONTEXT.md`、ADR 0014/0015/0016、Arco Modal 当前类型与前端 lint/typecheck。没有新增共享业务组件、令牌持久化、业务 Mock 或后端权限绕过。Modal UI 只复用现有 `CustomerForm` 与 `CustomerScreen` 的状态/处理路径。既有屏幕文件职责较多，但本票未复制第二份业务表单。
- **需求轴**：#90 四条验收项分别对应上表。浏览器复核发现最初测试的遮罩定位器点击了退出动画中的旧 mask，改用真实视口点击；校验断言最初误期待已填 username 报错，改为检查缺失 email。修正后整套真实浏览器测试通过。#69 的页面模式完整行为属于 #91，username 文字规格与当前后端正则偏差留给 #93。

## 回滚

回退 #90 独立提交可移除 Modal 创建/编辑入口、交互保护、测试和文档；不需要数据库迁移。此前通过弹窗创建或修改的 Customer 属于业务数据，代码回滚不会自动撤销，应按业务记录处理。#86/#88/#89/#92 的已交付功能保持各自边界。
