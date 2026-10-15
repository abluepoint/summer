# T16 菜单与动作权限真实验收

使用协调方提供的不可变 Spring Boot 候选、真实 MySQL、隔离 Redis 和 Chrome。运行前确认专用端口 23081、23082、23083、23573、23580 可用；需要本机 JDK 21、Node 22、Chrome、docker 和明确的 kubectl docker-desktop/local-base 上下文。凭据只从环境或 Kubernetes Secret 读取并在内存使用。

## 运行

从仓库根目录执行。EVIDENCE_DIR 必须是没有 result.json 的新目录，候选路径和 SHA 必须来自已核验的构建记录。

```sh
SUMMER_MENU_MANIFEST_EVIDENCE_DIR="$PWD/.git/t16-new-evidence" \
SUMMER_MENU_MANIFEST_CANDIDATE="/absolute/path/to/candidate.jar" \
SUMMER_MENU_MANIFEST_CANDIDATE_SHA256="verified-64-character-sha256" \
python3 scripts/acceptance/menu-manifest/targeted-run.py
```

默认批次验证首次及既有覆盖竞争、胜者审计/outbox、JVM 重启、真实事务尾部故障回滚与同幂等键重试，以及客户动作权限和停用路由。故障仅通过临时数据库账号缺少 outbox INSERT 权限注入；业务身份与授权均经管理 HTTP API 创建，SQL 不写业务数据。

只验证后续 UI 改动时，增加 `SUMMER_MENU_MANIFEST_UI_ONLY=1 MENU_PARENT_ROUTE_CHECK=1`，并换一个证据目录。该模式不执行并发、重启或数据库事务故障检查；它验证客户按钮、API 拒绝、停用客户菜单及父分组不能接住停用子路由。

`run.py` 和 `browser.mjs` 是初始完整验收入口。历史结果保留在各独立目录；不要覆盖失败回执，也不要将不同批次重复断言相加作为覆盖量。

## 组件和退出检查

在 `summer-ui` 工作目录运行以下脚本，输出路径通过所列环境变量指定：

- `MENU_COMPONENT_RESULT=/absolute/result.json node ../scripts/acceptance/menu-manifest/verify-customer-component.mjs`：真实 React/Arco 配合显式 HTTP adapter，分别验证只读、新增、修改、删除按钮。
- 同一环境变量运行 `verify-menu-routes.mjs`：真实 React 路由 hook 和 React Router 匹配规则的组件回归。
- `MENU_LIFECYCLE_RESULT=/absolute/result.json node ../scripts/acceptance/menu-manifest/verify-browser-lifecycle.mjs`：真实 Chrome 正常关闭和 SIGSTOP 后有界回收，业务用例为零。

组件 fixture 不替代真实后端验收。浏览器使用独立子进程所有权；超时只终止本次启动的 Chrome，不匹配或终止其他浏览器。

## 证据与清理

每个批次写入原子 result.json、browser-result.json、候选 SHA、执行脚本副本、源码摘要和截图。结束时核验隔离 schema、账号、Redis 容器以及五个专用端口已释放，并删除本轮 Vite 缓存。详细结论见 `docs/acceptance/menu-manifest.md` 与相邻 JSON。
