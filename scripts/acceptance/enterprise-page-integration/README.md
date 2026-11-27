# #142 角色及授权事件的活动租户集成验收

> 2026-11-20：授权事件页面、列表/重试和授权解释接口已退役，见 [ADR 0041](../../../docs/adr/0041-retire-authorization-event-management.md)。本目录涉及旧页面或 `/api/authorization-events` 的脚本保留为历史验收材料，不能用于当前版本验收；内部事件写入、事务一致性与投递测试继续保留。

`run.py` 使用独立真实 MySQL schema、独立 Redis 容器和固定候选 JAR，通过正式管理 API 创建两个租户与测试成员。`browser.mjs` 在 Google Chrome 中完成严格 PKCE 登录，操作实际 React/Arco 页面。无业务 SQL 写入、无网络替身、无修改 JWT 或服务端组织上下文来模拟租户选择。

在仓库根运行。设置 `SUMMER_T34_EVIDENCE_DIR` 为新的仓库内证据目录、`SUMMER_T34_CANDIDATE` 为不可变 JAR 绝对路径、`SUMMER_T34_CANDIDATE_SHA256` 为其真实 SHA-256，然后执行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 scripts/acceptance/enterprise-page-integration/run.py
```

运行前释放本任务专属端口 20081、20082、20083、20573、20580。需要已有 docker-desktop/local-base MySQL、环境助手所需的 Secret、已有 `redis:7-alpine`、项目 Java 21、Node 22 与 Chrome。凭据只在内存、子进程环境和 stdin 内传递；不写入证据或命令参数。

输出包括执行脚本副本、源码 SHA、候选来源、HTTP/Chrome 断言、实际截图、清理回执。授权 Java 输入必须与候选的 `candidate-inputs.json` 一致；执行期间输入变化判失败。每轮用新目录，保留失败批次。退出前清除测试账号/schema、Redis 容器和自有监听端口。

运行器暂用 `scripts/acceptance/business-statistics/environment.py` 的基础设施管理及 `foundation/ui-server.mjs`；不修改这两个既有资产或统计旧验收记录。所有 fixture 用户和业务数据在测试库中，测试结束删除。

另有独立的合成组件验证，明确不代替真实 HTTP：

```sh
cd summer-ui
SUMMER_ENTERPRISE_PAGE_CACHE_DIR=/绝对路径/仓库内缓存目录 /opt/homebrew/opt/node@22/bin/node --test scripts/verify-enterprise-page-integration.mjs
/opt/homebrew/opt/node@22/bin/node --test scripts/verify-roles-component.mjs
```

前者使用真实共享 hook、React 17、Arco 与显式合成身份/axios adapter，覆盖 A/B 切换、dirty/busy、403 清空、迟到响应、跨读取通道竞争和 412。后者显式固定合成身份为 ent-acme，仅验证既有连续版本保存与 412 保留输入；不宣称验证真实共享身份。

`duplicate-catalog.mjs` 是额外的真实 React/Arco 合成传输探针：目录中的不同 HTTP 方法合法共享授权码，翻页后模拟 403，要求所有旧行移除。它从既有组件夹具加载内容并只替换测试目录数据，不写页面源码。运行时 cwd 为 `summer-ui`，设置 `SUMMER_T34_EVIDENCE_DIR` 为新的已创建证据目录。默认验证实际源码；`SUMMER_EP_PROPOSED_KEYS=1` 仅用于明确标记的内存修复对照，不能当作实际源码通过。

`SUMMER_EP_SUPPLEMENT=1` 真实补验模式通过正式 API 预置 B 中角色和绑定，跳过此前已通过的 Chrome 创建/绑定操作，只验证角色 403 清理及授权事件、跨页切换。报告中的 seeded persistence 明确属于 HTTP 夹具回读，不冒称本批 Chrome 创建。每批记录自己的执行副本与原始失败；不能用补验覆盖旧失败或合并成一次完整矩阵。
