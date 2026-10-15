# T32 受控降级实际验收

使用真实 MySQL、独立 Redis 容器、生产 JAR、正式 PKCE 和 Summer UI。运行器不伪造成功响应，不用业务 SQL 写入替代 API。MySQL 应用库/账号、Redis、JVM、浏览器和端口均只回收本次创建的资源。

## 前提与运行

从仓库根运行。需要已授权的本地 Kubernetes `docker-desktop/local-base`、MySQL 本地转发 `127.0.0.1:3306`、Docker、JDK 21 与 Node 22。运行器从已有 Kubernetes Secret 在进程内取凭据，不得将凭据写进命令、日志或验收文档。

先准备固定的生产候选 JAR 和 SHA-256；以下环境变量仅包含制品及证据路径，路径须换成本机实际值。证据目录必须是新的，不能覆盖失败批次。

```sh
export SUMMER_TASK_CONFIGURATION_CANDIDATE=/absolute/path/candidate.jar
export SUMMER_TASK_CONFIGURATION_CANDIDATE_SHA256=actual_sha256
export SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR=/absolute/path/new-evidence-directory
python3 scripts/acceptance/controlled-resilience/run.py
```

`run.py` 复用 `task-configuration/environment.py`，因此沿用其 `SUMMER_TASK_CONFIGURATION_*` 制品变量。完整模式使用本次自有的 26081/26082 两个后端端口及 26573 回调来源；启动前先确认端口空闲。正式 API 创建身份、成员、角色、DataScope、参数和发布字典。

真实故障通过暂停自有 Redis 客户端处理、低并发/限流配置及自有数据库审计触发器注入；触发器只制造审计失败，验证写事务回滚，不改既有用户数据库。完整模式覆盖超时 fallback、30 秒后过期拒绝、熔断恢复、并发隔离、同键限流 fallback、冷键拒绝、旧令牌撤权和无伪成功写入。每个 JSON 请求检查 Unified response、SUMMER 错误码和关联 ID。

```sh
SUMMER_T32_PHASE=circuit python3 scripts/acceptance/controlled-resilience/run.py
```

`circuit` 定向模式把熔断开放等待设为一分钟，在开放期采样 OPEN，检查真实重试计数字段，并在 Redis 仍延迟时通过正式 API 更新参数版本及字典发布指针，验证旧 fallback 不会跨版本使用。它不重跑完整模式的 TTL、恢复、并发隔离和写故障矩阵。完整模式的两秒开放期可能已进入合法 HALF_OPEN，不能只因采样不为 OPEN 就判定没有熔断。

运行器在最后核对 schema/账号/容器和五个自有端口已经回收，检查敏感日志命中与相关源输入是否漂移。用例计数、错误、清理和退出码分别保存；零用例、仅 HTTP200、仅进程结束均不能作为验收成功。

实际 Chrome 入口及环境变量见同目录 `browser_probe.py`、`browser_environment.py`；它使用独立的 28081/28573 等端口，与原生矩阵隔离。浏览器逐项比对实际服务端诊断，不填造指标。最终批次、源码边界和已知限制见 [中文验收报告](../../../docs/acceptance/controlled-resilience-140.md)。
