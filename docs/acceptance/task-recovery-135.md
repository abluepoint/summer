# T27｜任务重试与集群故障恢复验收

日期：2026-10-15。父方案 #108，用户故事 38、39，Issue #135，上游 #134 已完成。

本记录补齐 T27 的关票证据。既有[任务修复记录](../guides/summer4-task-progress-closeout-20261015.md)保留各阶段红灯与当时未完成门禁；本文只更新 T27 的验收判断，T29 的 SSE 连接边界等独立门禁仍待完成。机器可读收据见 [JSON](task-recovery-135-result.json)。

## 设计、实现与缺陷追踪

任务采用至少一次调度。`TaskSchedulerService` 持久化幂等触发收据，Quartz 持久集群负责派发和故障恢复；`TaskExecutionService` 用短事务领取任务、记录每次尝试和最终状态，并以唯一所有者约束拒绝旧节点覆盖接管结果。处理器在事务外执行，支持超时取消。失败次数按配置上限执行，节点中断记录 `TASK_NODE_LOST`，恢复执行保留同一业务幂等键。

`RegisteredTaskHandler` 只允许应用注册的受信任类型。处理器必须在自身业务事务中原子提交业务幂等收据和副作用；框架覆盖保留的租户、任务和幂等键参数，防止调用方伪造。该设计不承诺外部系统天然 exactly-once。

历史缺陷包括：长事务导致其他节点看不到运行进度；调度失败后的同键重试未重新入队；恢复只覆盖演示租户；业务处理器注册缺失；任务页使用浏览器时间覆盖服务端结束时间。原始红灯、修复及对应回归保留在既有修复记录和本地证据中，未把旧失败改写为通过。

## 分批实测

各批分别报告，不跨候选合计为一次全量执行。

| 批次 | tests / failures / errors / skipped | 覆盖与边界 |
| --- | --- | --- |
| 当前 E1 候选真实 HTTP 补验 | 5 / 0 / 0 / 0 | 两次重试产生三条完整尝试记录；实际处理器超时；真实 JVM 重启后两类历史完全一致；终态不再产生额外尝试 |
| 保留的 2859 候选双 JVM 业务场景 | 3 / 0 / 0 / 0 | 提交业务后抛错重试、双节点同键竞争、提交后杀死实际 owner 并接管；每种业务副作用均为 1，收据三列也核对了不可伪造上下文 |
| 同批真实 Chrome 任务流程 | 6 / 0 / 0 / 0 | PKCE 登录、非法 JSON、创建编辑、暂停恢复、实时进度与成功、失败重试历史 |
| 同批真实 Chrome 接管历史 | 1 / 0 / 0 / 0 | SUCCESS、两次尝试和已接管标记 |
| 已验收 T26 当前任务入口 | HTTP 13 / 0 / 0 / 0；Chrome 7 / 0 / 0 / 0 | 当前菜单和持久权限下的配置、审计、迁移、重启；只读身份写操作 403 及 requestId |
| 保留的相关 JUnit 六类 | 19 / 0 / 0 / 0 | H2/真实 MySQL 业务幂等各 3、执行恢复 3、重试 HTTP 5、调度 HTTP 4、派发恢复 1 |

当前候选 SHA-256：`e1a0380ffd187f95c4e517a92412f23d429c828647b7b4f5e4a498b856f4057d`。保留业务验收候选：`2859eaad1029a271db4e44e11911b322af1374b2adaf9dad539dc9c69307872c`。两者的 **21 个 task Java 源码与 4 个任务/Quartz 迁移文件**逐字节匹配当前工作树，因此保留三个业务场景及接管页面证据；当前权限/菜单链路使用 T26 的后续实测补足，不声称旧整包与当前整包完全相同。

三类实际运行均清理并回读确认自有 schema、容器、账号（适用批次）、JVM、浏览器和端口。计数业务插件及其测试表仅用于隔离验收，未打包进生产应用。本轮补验没有新增生产源码修改，也未重跑全仓 check；此前 688 项全仓绿灯不代表包含后续所有其他模块修复。

## 七条 AC 对应

| AC | 验收依据 |
| --- | --- |
| 同一触发不重复业务副作用 | 三个真实双节点计数业务场景均为一次，竞争时仅一条执行 |
| 策略重试并记录每次结果 | 新候选三次 FAILED 尝试，各有时间、节点、固定失败码，最终失败 |
| 节点故障后接管 | 杀死已经提交业务写入的实际 owner，另一 JVM 成功接管，业务计数仍为一次 |
| 锁竞争、重启、超时和终态 | 双节点竞争、owner 接管、新候选实际超时和 JVM 重启后的逐字段历史比较 |
| 迁移、真实 HTTP 和 Summer UI | 真实 MySQL/Flyway/Quartz、PKCE、Chrome 配置及接管页面，成功/拒绝/非法输入/故障分别覆盖 |
| 公共契约及敏感信息 | 应用 JSON 使用 Unified response，参数使用 Parameter contract；新补验每次请求核对响应头与 body 的 requestId；框架错误保持 SUMMER_；TASK_TIMEOUT 等是任务尝试结果码。凭据仅内存/env，公开材料无令牌或密钥 |
| 完整追踪及计数、保留用户改动 | 本文及 JSON、历史红灯记录、逐批原始回执、输入摘要；仅白名单验收资产提交，不重置真实索引或用户文件 |

## 复现本轮补验

在仓库根目录运行，需要可用的本地 Kubernetes MySQL、已授权 Secret 来源和现有 `redis:7-alpine` 镜像。端口复用已提交的 T26 隔离夹具：26081–26083、26573、26580。脚本只使用基础设施 DDL 和读取证据 SQL，业务配置与授权经真实 HTTP 完成。

```sh
export SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR="$PWD/.git/t27-unique-run"
export SUMMER_TASK_CONFIGURATION_CANDIDATE="/absolute/path/to/pinned-candidate.jar"
export SUMMER_TASK_CONFIGURATION_CANDIDATE_SHA256="<candidate-sha256>"
python3 scripts/acceptance/task-recovery/run.py
```

原始证据路径、各批候选和适用输入摘要均见 JSON。运行后核对进程退出码、`runtime-result.json` 的计数与 `cleanup.json`，不能仅凭启动成功判断验收完成。
