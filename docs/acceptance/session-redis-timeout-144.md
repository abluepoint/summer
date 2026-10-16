# #144 / T25–T36：Session Redis 命令超时缺陷补救

2026-10-16。会话故障分类已做最小修复，定向回归最终 **9/0/0/0**。两个 Java 文件已冻结供主会话集成；candidate03 的真实 Redis pause 复验由 T36 负责，本文不预填通过，也不宣称 T36 已完成。

## 触发、根因与改动

candidate02 的原生批次03记录 Redis 暂停后的状态断言失败，但未保存实际响应状态；批次04/05明确捕获 Cookie 请求 `/internal/v1/release/readiness` 返回500。批次05的结构化回执明确为 `success=true`、`error=null`，`data` 含 Boot 的 timestamp/status/error/path，耗时约2128ms；恢复后200。异常类集合包含 QueryTimeoutException、RedisCommandTimeoutException，但未存原生同链栈，不能据此声称已经捕获真实 throw-site。

最小回归通过真实 LettuceExceptionConverter 构造实际转换结果，经 Spring Session 的 SessionRepositoryFilter 读取 Cookie 会话，重现异常穿过 SessionFailureConfiguration 的行为。另一用例使用真实随机端口 Tomcat、Boot 错误分发及生产响应 advice，准确复现 HTTP500/success=true/data.status500。原因是原分类识别 Redis 连接/系统故障，却漏掉了被转换成通用 QueryTimeoutException 的 Redis 命令超时原因。

生产仅在现有有界 cause 遍历中增加具体 RedisCommandTimeoutException：沿用既有 HTTP503、`SUMMER_SESSION_STORE_UNAVAILABLE`、`success=false`、`data=null`、头体一致 requestId 与 `Cache-Control: no-store`。没有泛捕 QueryTimeoutException；JDBC SQLTimeout cause、无 Redis cause 的超时原样传播，已提交响应也不被重写。

修改仅为 `SessionFailureConfiguration.java`，新增 `SessionFailureConfigurationTest.java`。这次在上游识别到的会话超时处阻止错误分发，**不代表全仓所有 Servlet/error 的成功包装问题均已修复**。业务缓存的 `SUMMER_DEPENDENCY_UNAVAILABLE` 与本会话故障语义分开。架构契约见 [ADR0029](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。

## 原始失败与定向回归

计数顺序为 tests/failures/errors/skipped，各批独立保留，不相加为一次全量门禁。

| 批次 | 计数 | 精确边界 |
| --- | --- | --- |
| red01 | 2/1/0/0（原XML） | 一个有效会话读取失败、一个JDBC反例通过；runner错误地只接受成功Task标记，未形成正常最终result。recovered-result另存，不伪造正常终态。 |
| red02 | 7/4/0/0 | 有效红：SessionFilter读取及直接/Servlet/Runtime包装三种Redis超时链失败，负对照通过。 |
| red03 | 8/5/0/0 | 原四项仍为有效红；新增HTTP夹具漏Tomcat错误页自动配置，返回默认HTML，新增解析失败属于夹具错误，不能作为目标症状证据。 |
| red04 | 8/5/0/0 | 补齐测试装配后的最终有效红，真实HTTP明确500/success=true/data.status500。 |
| green01 | 8/0/0/0 | 最小生产修复后，新八项全部通过。 |
| green02 | **9/0/0/0** | 相同新八项加既有会话索引故障HTTP用例一项，exit0；捕获源码无漂移。 |

新八项包含真实 SessionRepositoryFilter 读取、三种异常包装、JDBC负例、无Redis cause负例、committed response，以及真实HTTP错误分发。成功故障映射均断言503、失败信封、requestId和no-store。

Tomcat用例在 SessionRepository seam 注入异常，**不是实际 Redis pause、PKCE或完整 readiness 原生验收**；既有一项用 memory Servlet Session 和不可用Redis端口验证索引故障，也不覆盖暂停中的Spring Session Redis。测试使用固定JDK21、共享Gradle锁、单测试fork和独立XML；未在本补救中构建JAR或执行全量check。

## 追溯与待验收范围

源码 SHA、原始红绿 XML、执行副本与清单位于 `.git/summer4-closeout-20261015/session-redis-timeout-fix-01/`，公开摘要见 [结果JSON](session-redis-timeout-144.json)。任务起止661个受保护文件SHA未变；前一#142交付、ReleaseGate、AuthorizationService、UI及共享测试配置未修改。

主会话已接手candidate03构建和完整后端门禁。T36需使用新候选重放原05 Cookie/PKCE/Redis pause：503及统一失败信封、页面清空旧结果、恢复后200及完整readiness结果；另核对纯Bearer隔离和业务缓存受控503语义。若失败，仅补采异常类、cause层级及脱敏栈帧。新候选真实补验由T36另行记录，旧candidate02原生红灯继续保留，不改判为绿。
