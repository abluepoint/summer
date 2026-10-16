# Summer4 最终门禁复核 v2

2026-10-16。Session超时修复后完整backend06通过，候选03真实故障补验通过；提交推送与Issue关闭由root另行回读。本报告接续[backend05历史报告](summer4-final-gates-144.md)，保留其当时T36 pending状态及所有原始失败。

## 完整后端检查

**exit0；181 suites；775 tests / 0 failures / 0 errors / 0 skipped；839.4秒。** 逐XML、逐case与回执一致；730输入运行前、运行后、当前摘要完全相同。按suite和显示名的重复次数比较，backend05原767项无缺失，新增8项全部为SessionFailureConfigurationTest。

| test任务 | 本轮状态 | XML tests | suites |
| --- | --- | ---: | ---: |
| summer-common | FROM-CACHE | 1 | 1 |
| summer-common-autoconfigure | FROM-CACHE | 1 | 1 |
| summer-data-jpa | FROM-CACHE | 7 | 3 |
| summer-data-jpa-autoconfigure | FROM-CACHE | 2 | 1 |
| summer-mvc | FROM-CACHE | 82 | 11 |
| summer-mvc-autoconfigure | FROM-CACHE | 18 | 1 |
| summer-sample | EXECUTED | 644 | 156 |
| summer-security | FROM-CACHE | 4 | 2 |
| summer-security-autoconfigure | FROM-CACHE | 16 | 5 |

合计本轮实际执行任务644项，8模块FROM-CACHE共131项；5个starter为NO-SOURCE。25份缓存XML与backend05逐字节一致，其中security-autoconfigure16项在05实际执行、本次缓存恢复；其余7模块的原缓存出处见05报告。Gradle输入指纹由Gradle判定，未自行重算缓存键。

**775是JUnit记录，不是775条新执行或全原生HTTP。** 默认关闭的BFF/strict两方法会提前返回，不能据其PASSED宣称外部链路验收。Sample确实执行；其中环境门控MySQL24项、Testcontainers20项均保留且零skip。H2/MockMvc、最小Tomcat和实际数据库边界按各自测试解释。

新增八项包含Redis原因链三种包装、真实SessionRepositoryFilter、JDBC/无Redis原因负例、已提交响应、真实Tomcat错误分发。Tomcat以repository注入异常，不能冒称实际Redis暂停；原生补验另列。[Session缺陷红绿与边界](session-redis-timeout-144.md)。独立Astra会话对两Java分Standards/Spec审查均0项发现；额度中断的Sol启动未产生审查结论。

## 前端及原生制品

frontend02七命令exit0、Node19+11+9=39项；frontend03在角色页两处React key变更后重验typecheck/lint/build，其他未变门禁有显式复用清单。330输入冻结且之后无前端源码变化；lint0 errors、65既有warnings，构建均有dist事前备份。精确分批记录见05报告，不称全部重新执行。

候选03 SHA256 `e9d1ff6ef33b74c1c8ffef9bc6417b06554d92490f8b1e814c5dec49d3d2f28e`，543构建输入，40迁移资源（MySQL39、通用1）。与02比较987运行时条目，集合相同、仅SessionFailureConfiguration外层和匿名filter两class变化；外层反汇编语义相同，唯一生产逻辑增量为Redis超时原因链识别。其他业务class、依赖和迁移资源字节相同，因此明确复用02不受影响的scope93、迁移54、cache故障76和综合原批前213项；原综合214/1/1/0及各失败批不改判为通过。

**候选03真实补批85/0/0/0、Chrome另计5/0/0/0，exit0。** 独立Redis暂停时两节点Cookie分别约2077/2320ms返回503、success=false、无data、SUMMER_SESSION_STORE_UNAVAILABLE、no-store及头体一致requestId；纯Bearer仍200/NOT_READY。恢复后两类请求均READY。Chrome十业务行、503清旧结果、恢复、403清旧结果和跨节点WebSocket投递/重放均通过。root目视核对正常、故障、无权截图；所有自有库/账号/Redis容器及5端口清理通过。Chrome有界强制回收但processExited=true、pageErrors=0，如实保留。

[结构化原生验收](summer4-release-upgrade-144-result.json)保留十批原始结果及清理；[父方案六段追踪](summer4-parent108-trace.md)覆盖52故事、36票、252AC。未增加微服务拆分或生产Kubernetes发布承诺。

## 证据及范围

[同名JSON](summer4-final-gates-144-v2.json)记录计数、模块状态和原始回执SHA。私有原XML/source-before/source-after/events在`.git/summer4-closeout-20261015/final-gates/backend-06/`，root独立核对在`final-review/final-gates-06/`。backend04的733/7、backend05的767绿、Session red01–04及所有原生失败均保留，不跨批累加。用户CustomerController及真实索引按独立提交保护；本报告不宣称工作区清空。
