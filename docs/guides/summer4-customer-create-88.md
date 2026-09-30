# Issue #88：抽屉新增 Customer

## 需求追溯

| 验收要求 | 实现 | 验证 |
| --- | --- | --- |
| 共享 CustomerForm 在抽屉中创建 username、email、displayName | `summer-ui/src/pages/customer/CustomerForm.tsx`、`CustomerScreen.tsx`、`api.ts`；有 `customer.write` scope 才展示新增入口；创建调用 `POST /api/customers`，只发送三个字段 | 表单契约单测；真实 OIDC 登录后对 Sample H2 后端创建、GET 回读并 DELETE 清理测试数据 |
| 字段 400、重复 409、网络错误 | 400 的已知 `violations[].field` 落在字段下；未知字段错误在表单顶部；409 仅对服务端明确标识的字段定位，通用冲突显示原始消息；网络断开给出本地化提示 | 浏览器受控响应分别覆盖 400、409 和断网；API 元数据单测 |
| 重复提交、关闭确认 | 提交期间同步锁和禁用按钮；有内容时，取消、遮罩、关闭按钮均经过 Arco 确认 | 浏览器双击只观察到一次 POST；继续编辑保留输入，放弃后清空 |
| URL 状态、焦点和认证恢复 | 创建成功仅刷新列表数据，不重置 URL；抽屉关闭后焦点回到新增按钮；#86 草稿接口在认证跳转前保存，重新登录后恢复，但不自动重发写请求 | 浏览器保持 `page=2&size=10&active=false&sort=email&direction=desc`；真实 401→PKCE→返回后保留输入，POST 次数为 1 |

## 验证记录

- 纳入 #92 后，`yarn test`：13 tests、0 failures、0 skipped；`yarn typecheck`、`yarn lint:style`、`yarn build` 通过。`yarn lint` 为 0 errors、40 条既有 Arco 示例警告。
- 使用本 worktree 的 Sample 8081 与 Vite 5174，真实 OIDC、真实创建与持久化回读通过。400/409/网络中断/401 是浏览器网络边界的受控响应；401 后的 PKCE 重启使用真实授权服务。测试 Customer 随后通过真实 DELETE 返回 204；临时口令、配置、截图和联调进程已清理。
- 桌面与 390px 视口截图经目视检查，抽屉字段、按钮及关闭入口均可见，窄屏无横向溢出；截图不入库。
- `./gradlew test --rerun-tasks`：32 份 JUnit 报告，共 141 tests、0 failures、0 errors、0 skipped；46 个 Gradle task 实际执行。
- 基于包含 #92 的 `summer4` 再次运行真实浏览器：`test:e2e:customer-create` 通过且临时 Customer 删除返回 204；`test:e2e:delete` 的末页回退、404、username 复用及 401 不重放通过。#92 合并时保留删除成功后的末页回退，普通列表读取不再自动改写页码，以满足本票据的筛选与分页状态保留。

## 审阅记录

以 `e5f3a3c`（#87 集成点）为原始固定点，最终在 `eca7b73`（已含 #92）之上审阅 #88 的 UI、认证草稿和测试差异。标准轴核对 `AGENTS.md`、`CONTEXT.md`、ADR 0014/0015、Arco Pro 现有组件用法、前端类型和 lint；需求轴逐项核对 GitHub #88。用户要求本票在单独会话完成且不用子智能体，因此两轴均在本会话完成。审阅发现并修复了网络错误直接显示 Axios 原文、关闭后焦点回到 `body`、401 后路由重新挂载消费草稿这三项问题，浏览器复验通过。与 #92 的冲突按两个 Issue 的验收语义逐项合并，共享 scope 判断和同页两个操作入口。

- **标准轴**：未发现阻断性规范偏离；业务响应只在 `/api/**` 解包，认证沿用 #86 helper，新增入口按实际 scope 显示，删除的原始 204 响应语义保留。`CustomerScreen` 同时承载列表、详情、创建和删除的状态，属于可能的职责聚集；本票未把既有页面整体重构作为交付条件。
- **需求轴**：#88 四组验收要求均有实现与测试对应。409 后端当前只有通用冲突，没有字段名；UI 仅在响应含已知字段 violation 时做字段定位，其余显示服务端消息。真实浏览器复验创建和删除两个流程，未发现合并后的功能回归。

已知现状：`CONTEXT.md` 与 ADR 0016 对 Customer username 的文字约束（小写、允许点、首尾字母数字）和当前后端 `CustomerParameterConfiguration` 的实际正则不一致。本票的创建前端校验按当前后端规则实现；后端契约修正须由独立票据处理。本票不修改 Customer 持久化、登录主体或其他创建模式。

## 回滚

回退 #88 的独立提交即可移除抽屉创建、表单和相关脚本，并恢复 #87 的列表页；无需数据库迁移。此前已创建的 Customer 是业务数据，回滚前按业务决策处理。#86 认证恢复与后续已合入的独立基线测试修正不随 #88 回滚。
