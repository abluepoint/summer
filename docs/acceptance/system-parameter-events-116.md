# T08｜参数变更的可靠事件投递（#116）

状态：**可交 root 复核**。原始票关联故事 9、15、49。本轮未提交、推送或关闭 Issue；原始失败记录保留。

生产修改仅 `SystemParameterEventStreamListener` 与 `SystemParameterEventConsumer`。监听器先分批处理自己的 pending，再接管空闲至少 30 秒的遗留消费者消息，最后读取新消息；每批最多 100 条。新节点从保留的 Stream 起点追赶。单条失败不会中断本批其他消息，已持久化的消费死信会确认并保持终态。各节点仍先清除本地缓存，再检查共享幂等回执。

设计依据：[ADR 0025](../adr/0025-versioned-two-level-parameter-dictionary-cache.md)、[ADR 0031](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。参数事务、outbox 元数据契约、V10/V11、HTTP/RawResponse 与管理端已有流程沿用。无新迁移或依赖。

## 实际证据与计数

| 执行 | tests | failures | errors | skipped | 说明 |
|---|---:|---:|---:|---:|---|
| 完整旧包实际验收 attempt04 | 117 | 6 | 0 | 0 | MySQL、独立 Redis、真实 HTTP/PKCE、多个 JVM；包含 17 项 Chrome 绿灯 |
| 冻结恢复脚本，旧包 red05 | 61 | 6 | 0 | 0 | 仅恢复场景及必要初始化 |
| 同一恢复脚本，新包 green01 | 61 | 0 | 0 | 0 | 消费恢复、重启、接管、加入节点全部转绿 |
| 新回归首批红灯 | 5 | 4 | 0 | 0 | 真实 Redis 与持久化回执 |
| 消费死信终态补充红灯 | 1 | 1 | 0 | 0 | 旧实现把 DEAD_LETTER 改成 PROCESSED |
| 最终事件定向 JUnit | 12 | 0 | 0 | 0 | 6 项新增回归及 6 项既有测试 |

计数按独立执行报告，不相加冒充独立业务覆盖。既有 JUnit 包含 mock/MockMvc，真实 HTTP/Chrome 验收未使用网络 mock。消费者业务处理失败使用受控 handler、真实 Redis 和 H2 回执验证；原生故障恢复使用真实 MySQL。

旧完整验收与失败包：`993ad757888f39802a7c03c1509d20a2f191a8616c5025eaff2712cf2eff320a`。
新候选：`b96d929682d48d2f0a548072f170d7dc9390f0d45eb0809fda0864340561d647`。相对 E1 仅上述两个生产类变化，534 个构建输入无漂移，迁移资源与源码一致。

## 原始七项验收闭环

| AC | 故事—设计—实现—测试—结果 |
|---|---|
| 1 原子写入 | 故事 9/15/49 → 参数事务及 outbox → `SystemParameterService` / `SystemParameterEventService` → 真实 HTTP 写入后参数与 outbox 同时可见 → 通过 |
| 2 回滚不投递 | 同一事务边界 → 独立 schema 临时重命名 outbox 表 → HTTP 500，恢复表后参数/outbox 均不存在、Stream 长度不变 → 通过 |
| 3 幂等/重试/死信 | 节点消费组与共享回执 → listener/consumer → 重复投递、数据库恢复、JVM 重启、遗留 owner 接管、新节点追赶、handler 重试与终态回归 → 有效红转绿 |
| 4 状态和敏感值 | 六字段事件元数据 → API 与事件表 → 状态/尝试次数/requestId 可查；真实 Redis 发布故障达到 DEAD_LETTER:3；正文、回执、日志无敏感 canary → 通过 |
| 5 真实链路与界面 | V10/V11 + HTTP/PKCE + Summer UI → 新 MySQL schema、独立 Redis → Chrome 17 项覆盖成功、无权限、真实数据库故障清空/恢复和管理员重试 → 通过 |
| 6 框架契约 | 既有 Parameter/MVC 与 ApiResponse/RawResponse → 成功/错误请求关联、SUMMER_ 命名空间和敏感信息检查 → 通过 |
| 7 证据与用户改动 | 原票、ADR、两处生产修复、回归与原始失败目录 → 实际计数、源码/dist 备份、白名单与清理回执 → 通过；T07 11 路径和 CustomerController 校验未变 |

## 缺陷与修复边界

1. 只用 `>` 读取新消息，Redis pending 不会自动重投。数据库恢复及重启仍有遗漏；新增 pending 读取及空闲消息接管。
2. 新消费组从最新位置开始，加入的节点跳过尚未处理的历史消息；改为从保留的 Stream 起点读取。
3. 消费 RETRY 后不能再次进入处理，死信无法到达或确认；修复后的循环可达到并确认终态。
4. 已有 DEAD_LETTER 回执遇到重复事件会再次执行 handler 并变成 PROCESSED；保持死信终态。

“空 Stream 的首条消息丢失”未复现，未作为缺陷。attempt01 的 Stream 必须不存在断言错误，保留 69/4/0/0 原记录；attempt02 的只读用户先进入空菜单环境导致 UI 初始化超时，保留 80/3/1/0。改为管理员先经真实界面初始化菜单后，attempt03 为 103/3/0/0，Chrome 17 项通过。后续聚焦脚本将诊断断言改为已保存事件的元数据不变，并以完全相同脚本完成旧包红、新包绿。

## 复现与边界

入口：`scripts/acceptance/t08/run-t08.py`。设置 `SUMMER_T08_EVIDENCE_DIR`、`SUMMER_T08_CANDIDATE`、`SUMMER_T08_CANDIDATE_SHA256`；`SUMMER_T08_RECOVERY_ONLY=1` 只执行恢复回归。真实授权全部通过业务 HTTP 建立；SQL 仅用于独立 schema 初始化、只读证据和表级故障注入。

遗留 owner 使用真实 Redis `XCLAIM ... IDLE 31000` 设置过期空闲状态，不冒称自然等待 31 秒。新节点只能重放 Redis 中保留的记录；保证至少一次和幂等，不承诺 exactly-once。UI 重试针对 outbox 投递，消费死信不会被重复消息隐式复活。截图只截事件表以排除参数值；错误和请求 ID 由 DOM 与真实 HTTP 收据验证。

原始证据位于 `.git/summer4-closeout-20261015/frontend/t08-116/`：`native-audit-attempt01` 至 `04`、`native-recovery-red05`、`native-recovery-green01`、三个 JUnit 目录、`pre-fix-backup` 与 `candidate-green01`。各轮独立 schema、账号、Redis 容器、JVM、Chrome/Vite 和自有端口均已核验清理；最终日志敏感值命中为零。

结构化证据：[system-parameter-events-116-result.json](system-parameter-events-116-result.json)。
