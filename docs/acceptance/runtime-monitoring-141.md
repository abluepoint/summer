# T33 运行监控与链路定位验收

关联 [Issue #141](https://github.com/abluepoint/summer/issues/141)，用户故事 2、11、45、52。依赖 #111、#112 已关闭。实现、定向 JUnit、真实候选的 MySQL 升级、HTTP、Chrome 与故障恢复已完成。原生主批为 111 / 1 / 0 / 0，唯一失败是验收正则将 security 中的 uri 字母误判为 URI 标签；保留该批，并以同一候选的 5 / 0 / 0 / 0 定向补验关闭此误报。本记录不表示全仓门禁或关票完成。

## 设计与实现

诊断页继续使用 `/internal/v1/diagnostics/runtime` 的 Unified response，保留 requestId，补充真实 OpenTelemetry span 的 traceId、HTTP 聚合与模块指标。MySQL、Redis、迁移及审计分别展示状态；认证、缓存、实时连接的 `AVAILABLE` 表示已有指标接入，不等同于端到端服务健康。未接入或没有数据时显示相应状态或空值。

HTTP 统计覆盖进程启动以来已完成的请求，平均延迟是总耗时除以请求总量，错误率是 `(4xx + 5xx) / 请求总量`。无完成请求时延迟与错误率为空；页面显示进程开始时间和统计口径。认证及缓存计数也是进程累计值；实时连接为当前节点的开放 WebSocket 数；审计来自持久化账本，聚合结果最多缓存 5 秒，重启不清零。

Micrometer、Prometheus 与 OpenTelemetry 使用现有 Spring Boot 4.0.7 BOM 管理的依赖。`/actuator/prometheus` 保留 Raw 文本响应，`/actuator/metrics` 提供指标目录。三个监控入口同时要求诊断 scope 和现有 AuthorizationService 的持久授权判定，缺失企业上下文、显式 deny、角色移除或授权读取异常均拒绝。健康端点维持原契约。

请求日志使用现有 RequestLogSink 扩展，只消费不可变日志记录，输出 requestId、traceId、方法、路径、状态及耗时，不记录请求体。响应通过 `X-Trace-Id` 暴露当前真实 span 标识。V38 增量迁移仅增加 nullable trace_id 列及索引；新审计在追加时取当前 span，旧审计不回填、不改写。未配置外部 OTLP Collector，因此本票覆盖进程内真实 span、W3C 传播及日志和审计关联，不声称完成外部收集器导出验收。

## 标签与敏感信息边界

指标过滤器移除 URI、requestId、traceId、用户和密钥等标签键。自定义指标只使用固定 result、tier 标签值。实际运行测试中的 name/id/action 标签来自线程池、连接池与 JVM 元数据；标签键白名单不能保证未来任意自定义 name/id/action 值的基数有界，新增指标仍需审查其值来源。

验收凭据仅经内存、环境或标准输入传递，结果只保留必要的状态、计数、脱敏关联元数据与文件摘要。运行日志用动态敏感值探针检查，不保存完整响应体或原始进程日志。requestId 和 traceId 用于日志及审计关联，不作为指标维度。

## 验收标准与证据

| 原票标准 | 实现与测试依据 | 当前状态 |
| --- | --- | --- |
| MySQL、Redis、已接入与未接入模块分别标识 | 原诊断及迁移测试，AuditObservation，模块接入状态 | JUnit 与真实候选通过 |
| HTTP 延迟、错误率、数据库与 Redis 状态、审计及已接入模块指标 | RuntimeMetricSummaryTest、缓存与连接钩子测试、诊断页 | 实际读写与 Chrome 通过 |
| API 错误关联日志与审计 | 真实 OTel span 的 409 响应头、请求日志、H2 审计关联测试 | 真实 MySQL 及重启通过 |
| 输出不含敏感请求或凭据 | 无正文日志、指标标签测试、动态敏感值探针 | 真实响应、日志与标签补验通过 |
| 实际迁移、HTTP 与 Summer UI 成功、拒绝和故障流程 | 旧候选审计生成、新候选 V38 升级、PKCE、Redis INFO 故障、Chrome | 已完成 |
| 复用参数、Unified/Raw、请求关联和 SUMMER 错误 | 原参数契约；Prometheus 原文；401/403/409 关联断言 | 真实 HTTP 通过 |
| 故事、设计、实现、测试、缺陷、证据与准确计数 | 本文、同名结果 JSON、冻结清单及每批原始结果 | 已完成 |

## 定向回归与已修复问题

以下计数依次为 tests / failures / errors / skipped，各批独立，不能相加当作一次全量测试。

| 批次 | 实际计数 | 含义 |
| --- | --- | --- |
| native-audit-attempt01 | 52 / 10 / 1 / 0 | 旧候选实际验收，暴露缺失指标、Prometheus、trace 关联；浏览器故障夹具误禁 PING 导致超时，Boot 实际检查 INFO |
| junit-red01 | 3 / 3 / 0 / 0 | 实现前 HTTP 指标及 Prometheus 红灯 |
| junit-green01 | 18 / 1 / 0 / 0 | 原诊断、审计、缓存与模块边界 15 项通过；新 Prometheus 测试配置未暴露端点 |
| junit-green02 | 4 / 0 / 0 / 0 | 修正独立测试 exposure，含真实 span 关联 |
| junit-green03、04 | 编译失败，各 0 项执行 | 测试错误地把 SimpleMeterRegistry 当作 AutoCloseable，随后修正 |
| junit-green05 | 16 / 3 / 0 / 0 | 错误码断言与 sealed health 类型 mock 夹具错误；标签、缓存、连接及既有 WS 回归 9 项通过 |
| junit-green06 | 7 / 0 / 0 / 0 | 修正夹具后 HTTP 与聚合数学测试通过 |
| revocation-red01 | 3 / 3 / 0 / 0 | 有效安全红灯：旧 JWT 在持久 deny 后仍可读三个监控入口 |
| revocation-green01 | 编译失败，0 项执行 | 现有 E2E 新认证夹具缺少 jwt 引用，随后改为完整限定调用 |
| revocation-green02 | 23 / 0 / 0 / 0 | 持久 deny 与角色移除拒绝旧 JWT；诊断、迁移、原 metrics、OTel、标签来源与模块边界通过；输入摘要无漂移 |
| native-implementation-attempt01 | 111 / 1 / 0 / 0 | 后端 99 项、Chrome 12 项；110 项通过，唯一失败为 security 字母片段误报，无生产缺陷；旧记录保持原计数 |
| prometheus-boundary-attempt02 | 5 / 0 / 0 / 0 | 修正为标签名称分段匹配，同候选真实 HTTP、trace 传播及 Prometheus 隐私边界补验通过 |

安全修复只在 sample 的 MonitoringSecurityConfiguration 调用既有公开授权服务，未修改 AuthorizationService、CustomerController、任务或 SSE 行为。缓存和 WebSocket 仅增加观测钩子；旧缓存、WS 超时及撤权回归保留。

## 复现与资源边界

`scripts/acceptance/t33/run-t33.py` 在独立 MySQL schema、账号及隔离 Redis 容器运行，业务授权和夹具均通过真实 HTTP 写入。后端候选由协调任务统一构建，验收核对 JAR SHA 和 16 项相关生产输入摘要。专用端口为 20081、20082、20083、20573、20580。

脚本读取 `SUMMER_T33_CANDIDATE`、`SUMMER_T33_CANDIDATE_SHA256`、`SUMMER_T33_EVIDENCE_DIR`。证据目录必须使用新路径；旧批次不覆盖。清理只处理该次创建的 schema、账号、容器及本聊天进程。共享工作区的已有暂存内容、其他任务改动与用户改动均保留，提交、推送及关票交由协调任务完成。

## 固定候选的实际验收

候选 SHA256 为 `9f2446f79736b13a28da00cfd9409bbdc08b138eb5f74b270480fdabd1f350e2`，统一构建输入 541 项稳定、迁移资源与源码一致。主批 16 项相关生产输入与候选匹配，运行期间跟踪输入无漂移；标签补验同样核对该冻结清单。前端类型检查及本次三个 UI 文件的 ESLint 均退出 0。

真实旧版本 9f0b 候选先生成审计记录，新候选执行 V38；原记录所有旧列逐项保持一致且 trace_id 仍为 NULL。新客户创建 SUCCESS、重复创建 409 FAILURE 均保存 traceId；同一 409 的 requestId、入站 W3C traceId、响应头、安全日志与审计 SQL/HTTP 查询一致，重启后仍可查到。

实际参数读取产生缓存 requests=2、hits=1、loads=1；真实 WebSocket 打开与关闭使连接数 0→1→0。审计聚合与数据库 5 条事件一致，其中成功 4、失败 1。一次 HTTP 快照为 24 个完成请求、2 个 4xx、1 个 5xx、平均延迟约 152.15 ms、错误率 12.5%；这些数值只描述该次验收进程，不作为性能基准。授权通过实际 HTTP 修改，旧 JWT 在持久 deny 后访问三个监控端点均 403，恢复授权后同一 JWT 恢复访问。

Chrome 12 项均通过：真实 PKCE、请求关联、组件状态、延迟与错误率、审计、统计窗口、trace、已接入模块、Redis INFO 故障 DOWN→恢复 UP、无权限页及无未处理页面异常。正常、故障和拒绝截图已人工检查。两次运行均核实 schema、专用账号和容器删除，五个专用端口没有监听；没有网络 mock 或业务 SQL 写入。

逐项原始断言、执行脚本副本与摘要、截图和清理回执保存在本地 `.git/summer4-closeout-20261015/frontend/t33-141/` 的各独立批次目录。同名结果 JSON 保留可随仓库提交的计数、断言、关联与清理摘要。历史失败记录未改写。
