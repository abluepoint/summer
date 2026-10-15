# T35 真实插件验收

使用 Summer Sample 随包交付的 `customer-insights`，不注入测试插件，不 mock HTTP。业务设置通过正式接口；SQL 仅用于隔离资源、只读验证和受控审计故障。

前提：项目内现有依赖、JDK 21、Node 22、Chrome、Docker，以及已授权的 docker-desktop/local-base MySQL。脚本使用 21081、21082、21083、21573、21580，每轮先核验空闲。数据库凭据和临时测试密码只留在内存、环境或标准输入。

在仓库根目录执行，每次必须指定新的证据目录：

```sh
SUMMER_TRUSTED_PLUGINS_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t35-live/new-focused-run" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE="$PWD/.git/summer4-closeout-20261015/t20-live/frozen-build-01/candidate.jar" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE_SHA256=9f2446f79736b13a28da00cfd9409bbdc08b138eb5f74b270480fdabd1f350e2 \
python3 scripts/acceptance/trusted-plugins/focused_probe.py
```

- `run.py`：服务端主矩阵。已有 105 项绿灯按 14 个匹配输入复用，不为前端修复重复运行。
- `browser_probe.py` / `browser.mjs`：最初完整 Chrome 场景及两处红灯。
- `focused_probe.py` / `focused-browser.mjs`：本次修复的 9 项 Chrome 场景。设置 `SUMMER_T35_FOCUS=denied-route` 只验证最终拒绝页面，不重复完整矩阵。
- 每轮自动归档当时的脚本；失败目录保留。检查 `result.json`、`browser-result.json`、进程退出码和 `cleanup.json`，不能只看控制台 PASS。
- Chrome 清理的 `forced` 标志如实记录，`processExited` 与最终端口清理分别检查。

完整结果、七项 AC、失败原因及复用边界见 [T35 验收报告](../../../docs/acceptance/trusted-plugins-143.md)。

管理页迟到响应的组件因果回归在 `summer-ui` 目录执行：

```sh
SUMMER_PLUGIN_RACE_EVIDENCE_DIR="$PWD/../.git/summer4-closeout-20261015/t35-live/new-race-run" \
/opt/homebrew/opt/node@22/bin/node scripts/verify-plugins-race-component.mjs
```

此测试只启动自有 Chrome 和 21573 的内存组件资源服务，采用可控延期 Promise 的合成 HTTP adapter，不启动后端、MySQL 或 Redis，不作为新增真实接口验收。结果及实际页面/脚本副本保存在所选证据目录。
