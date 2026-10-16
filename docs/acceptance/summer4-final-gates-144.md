# Summer4 最终后端与前端门禁核对

2026-10-16，供 [T36 #144](https://github.com/abluepoint/summer/issues/144) 收口审阅。**后端完整 check 和前端门禁通过；当前候选的 T36 原生综合验收仍待完成，本报告不据此关闭 #144。**

## 后端结果与执行边界

**backend05：exit 0，180 suites，767 tests / 0 failures / 0 errors / 0 skipped。** 独立解析归档 XML 后，逐 suite、逐 case 状态与运行器回执全部一致。backend04 的 733 条原记录全部保留，按展示名及重复次数比较，无缺失。多出的34条为事件/平台scope回归14条，以及backend04在Sample失败后未执行到的既有Security模块20条，并非34条新编写测试。

| test任务 | 状态 | XML tests | suites |
|---|---|---:|---:|
| summer-common | FROM-CACHE | 1 | 1 |
| summer-common-autoconfigure | FROM-CACHE | 1 | 1 |
| summer-data-jpa | FROM-CACHE | 7 | 3 |
| summer-data-jpa-autoconfigure | FROM-CACHE | 2 | 1 |
| summer-mvc | FROM-CACHE | 82 | 11 |
| summer-mvc-autoconfigure | FROM-CACHE | 18 | 1 |
| summer-sample | EXECUTED | 636 | 155 |
| summer-security | FROM-CACHE | 4 | 2 |
| summer-security-autoconfigure | EXECUTED | 16 | 5 |
| 合计 | EXECUTED 652；FROM-CACHE 115；UP-TO-DATE 0 | 767 | 180 |

5个starter模块为NO-SOURCE，无测试记录。7个缓存模块均找到与本批去输出块后XML完全匹配的原本地Gradle缓存条目，并记录其cache key、来源build ID与Gradle 9.0.0元数据；各模块捕获的源码输入在backend04、backend05和当前文件中一致。缓存键的输入指纹由标准Gradle判定，本次未另行重算。修改过的Sample测试均属本轮实际执行的 `summer-sample:test`。

环境门控的7类MySQL测试共24条、Docker门控的9类Testcontainers测试共20条，均在本轮实际执行，failures/errors/skipped均为0。**767是JUnit汇总，不是767条新执行或全原生HTTP。** `IdentityChainE2EContractTest` 的 strict执行上下文和BFF旅程两条在默认关闭开关时提前返回；本批未提供开启证明，XML的PASSED不能视为独立BFF/strict链路验收。其他单测、H2/MockMvc、真实数据库与最小Tomcat也按各自边界解释。

729个后端源码、测试、资源及Gradle输入与运行前冻结完全一致；运行器执行后差异为零，本次再次全量摘要回读与运行前一致。运行器保存了执行后的差异列表，未另存完整 `source-after.json`；本次独立完整回读另存。执行时HEAD为 `53c4b015`，复核HEAD为 `6c024b527043`，结论依据实际输入摘要而非仅HEAD。

## 原失败与测试基础设施修复

| 批次 | tests/failures/errors/skipped | 解释 |
|---|---|---|
| 历史合并检查 | 525/0/0/0 | 旧检查点，含两条BFF/strict早退；不能替代当前候选 |
| backend04 | 733/7/0/0，exit1 | Count五项凭据前置失败、健康检查503一项、审计旧精确10列断言一项；原回执/XML保留 |
| Redis最终定向 targeted02 | 6/0/0/0，exit0 | 只在Count与EndToEnd两测试类绑定Redis环境变量、DB15及超时；全局test application.yml恢复原字节 |
| MySQL audit定向 | 1/0/0/0，exit0 | V38合法新增trace_id；验证11个必要列允许后续增列，客户写入/审计查询断言保持 |
| backend05 | 767/0/0/0，exit0 | 修复后完整check；不与前述定向或历史数字重复相加 |

根测试基础设施清单仅3条：`SystemParameterCountHttpTest`、`SummerEndToEndTest`、`CustomerAuditMySqlIntegrationTest`，当前SHA均匹配 `ROOT-TEST-INFRA-FILES.json`。用户CustomerController摘要保持 `d6a830…b416`，`.t25-dependencies.log`保留；本审查未操作真实索引，未占有或删除共享新增条目。

## 前端与固定候选

| 前端批次 | 实际门禁 | 结果与复用范围 |
|---|---|---|
| frontend02 | typecheck、test、test:mock-boundary、test:proxy、lint、lint:style、build | 七命令exit0；Node原生19+11+9=39条、零失败/跳过；330输入运行前后相同 |
| frontend03 | typecheck、lint、build | 仅角色页两处React key改为method/path后重验；330输入稳定且匹配当前；其余门禁按 `incremental-scope.json` 复用frontend02 |

源码契约、合成本机Mock/Chrome边界和代理检查不冒充真实后端业务浏览器验收；lint为0 errors、65 warnings。两次生产build均有事前dist备份；门禁日志SHA、退出码、源摘要及记录的自有进程/端口清理均已核对。

固定候选JAR SHA-256：`8a2ec5987b60d7356cf2207b964a0e878d8434f903e59e887500a866ad09fb7e`。543个构建输入全部包含于后端729清单，与本批及当前摘要逐项一致。JAR内40个迁移资源（MySQL39、通用1）全部与源码逐字节相同。原构建校验器误把总数要求成39，因此原 `candidate-build.json` 仍保留失败；Gradle构建本身成功，另以 `migration-count-correction.json` 记录更正并固定候选，未把原失败改成成功。资源校验不等于真实迁移/备份恢复验收。

**T36状态：pending。** 当前230xx窗口的真实综合、升级与恢复结果由其所有者交付后另行核对；本次未运行构建、Gradle、服务或浏览器，也未修改产品、测试、验收运行脚本、旧报告或Issue。

机器可读映射见 [同名JSON](summer4-final-gates-144.json)。本地原回执位于 `.git/summer4-closeout-20261015/final-gates/`，独立XML/源/缓存/前端复核位于 `.git/summer4-closeout-20261015/final-review/final-gates-05/`；JSON保存各原始结果摘要。历史525的边界见 [原合并审查记录](../guides/summer4-merge-review-20261015.md)。
