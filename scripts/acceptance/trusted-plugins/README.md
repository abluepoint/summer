# T35 真实插件验收

使用 Summer Sample 随包交付的 `customer-insights`，不注入测试插件，不 mock HTTP。业务设置通过正式接口；SQL 仅用于隔离资源、只读验证和受控审计故障。

前提：项目内现有依赖、JDK 21、Node 22、Chrome、Docker，以及已授权的 docker-desktop/local-base MySQL。脚本使用 21081、21082、21083、21573、21580，每轮先核验空闲。数据库凭据和临时测试密码只留在内存、环境或标准输入。

在仓库根目录执行，每次必须指定新的证据目录：

```sh
SUMMER_TRUSTED_PLUGINS_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t35-live/new-focused-run" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE="$PWD/.git/summer4-closeout-20261015/standards-fixes/frozen-build-01/candidate.jar" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE_SHA256=4dd825e8df88aa35cb5f6b16563d39a659c26034513889d17f43c7be8fd944cf \
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


## Standards 定向补证

当前脚本的插件业务错误断言已更新到 `SAMPLE_PLUGIN_*`；原始执行目录保存旧脚本和旧候选的真实结果。上方示例改用包含本次补救的新候选，不能用旧 `9f2446` 候选运行新断言。旧 105/Chrome 9+1/组件 6 不重新计数。

只复验请求关联与插件错误契约时，在仓库根目录执行：

```sh
SUMMER_TRUSTED_PLUGINS_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/standards-fixes/new-runtime-run" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE="$PWD/.git/summer4-closeout-20261015/standards-fixes/frozen-build-01/candidate.jar" \
SUMMER_TRUSTED_PLUGINS_CANDIDATE_SHA256=4dd825e8df88aa35cb5f6b16563d39a659c26034513889d17f43c7be8fd944cf \
SUMMER_STANDARDS_CANDIDATE_INPUTS="$PWD/.git/summer4-closeout-20261015/standards-fixes/frozen-build-01/candidate-inputs.json" \
python3 scripts/acceptance/trusted-plugins/standards_probe.py
```

预检要求候选 SHA 及三处当前源码与候选输入清单一致。脚本通过正式 PKCE/角色接口配置权限，核对缺头与规范化头在 ALLOW/DENY 下的响应头、信封和持久审计；再核对三类业务错误、框架 403/500 及同节点恢复。SQL 不写业务行，只在临时 schema 创建/删除故障注入触发器。结束必须核对退出码、九项结果、敏感检测、主源稳定及 cleanup VERIFIED。

实际 runtime-01 为 9/0/0/0、退出0。公开脚本与实际冻结执行副本只在预检如何取得三处源码 SHA 上不同，其余 AST 相同；该发布副本未另行重跑，详见私有 `probe-publication-provenance.json`。相关 JUnit 红/绿与原始独立去敏感 XML 单列在验收报告，不与实际九项相加。
