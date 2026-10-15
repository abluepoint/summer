# T28｜自动密钥轮换验收

日期：2026-10-15。父方案 #108，用户故事 35、37、38，Issue #136，上游 #131、#135 已完成。机器可读证据见 [JSON](automatic-key-rotation-136-result.json)。

## 设计、缺陷与修复

生产 Quartz 持久集群调度自动轮换，默认周期 90 天，可通过 `summer.sample.security.key-rotation.period-days` 配置。到期执行沿用数据库密钥版本、幂等键和轮换状态行锁，安全切换 ACTIVE key；旧 key 进入 GRACE，保留窗口由 retention-days 配置，覆盖当前令牌有效期与时钟偏差。已有手动轮换及过期公钥清理契约继续适用。

实际 MySQL INSERT 故障复现了失败状态丢失：原实现把失败记录放在已经因 JPA flush 失败而失效的同一事务内，提交时整体回滚。新增 H2 真实约束故障测试也出现相同红灯。修复为轮换事务完全回滚后，在独立新事务内锁定状态并持久化 FAILED、错误码和退避时间；重新检查其他节点是否已成功，避免旧失败覆盖新的成功计划。Java 密钥存储监视器先于数据库锁获取，与手动轮换顺序一致。

另一条真实 JDBC 断言复现管理端缺少可查询审计记录。成功轮换在轮换事务中写入审计流水；失败在记录重试状态的新事务中写入失败流水。审计主体系 system，资源为 `security:automatic-key-rotation`，每次操作生成关联 requestId；失败明细仅含固定 `SUMMER_KEY_ROTATION_FAILED`，不保存异常消息或私钥。沿用现有密钥页的自动状态告警和审计页，无新增生产前端修改。

## 分批实测

计数均为 tests / failures / errors / skipped，分别报告，不视为一次全仓测试。

| 批次 | 实际计数 | 覆盖 |
| --- | --- | --- |
| 修复前 E1 真实 MySQL/Quartz | 7 / 1 / 0 / 0 | 默认周期、实际任务轮换、GRACE、重复唤醒、插入故障；失败状态未持久化，旧 ACTIVE 保留 |
| 事务约束红灯 | 1 / 1 / 0 / 0 | H2 真实新 key flush 失败，重试状态不能提交 |
| 审计断言红灯 | 1 / 1 / 0 / 0 | 缺少可查询的自动失败审计 |
| 最终相关轮换 JUnit 四类 | 7 / 0 / 0 / 0 | 自动单元 3、自动 HTTP 1、手动事务 2、新增实际事务 1 |
| 关联审计 HTTP JUnit | 6 / 0 / 0 / 0 | 既有流水权限、查询和返回契约回归 |
| 修复后真实 MySQL/Quartz 基础批 | 7 / 0 / 0 / 0 | 与旧候选同脚本，失败状态、重试时间和旧 ACTIVE 均正确 |
| 修复后真实 HTTP/双 JVM 补验 | 10 / 0 / 0 / 0 | 自定义 7 天/保留 1 天、两个集群成员、403、1 小时退避、失败/成功审计、恢复仅轮换一次、新 kid、旧新 JWT 实际验签、重启保留状态和流水、五个相关 UI 输入无漂移 |
| Chrome 失败状态及流水 | 4 / 0 / 0 / 0 | FAILED 告警、失败次数、system 流水、关联信息抽屉 |
| Chrome 恢复及流水 | 4 / 0 / 0 / 0 | SUCCEEDED、ACTIVE/GRACE、成功流水及详情 |
| Chrome 未授权用户 | 1 / 0 / 0 / 0 | 权限拒绝页且无轮换按钮 |

三个 Chrome 进程均正常退出，未强制终止，无 pageerror。主会话查看了失败状态、恢复审计详情和拒绝页面截图。两批修复后真实运行均无敏感值检测命中，诊断异常列表为空，清理后回读确认自有 schema、账号、隔离 Redis 容器、JVM 和端口均不存在。

初次夹具使用 `/acceptance/` 路径被现有 CSRF 策略拒绝，结果为 2 / 0 / 1 / 0；只改测试插件路径到 `/api/acceptance/` 并按现有权限机制授权，未修改生产 CSRF。该批与原脚本保留，不当作产品缺陷或通过证据。

## 候选、输入与边界

修复后候选 SHA-256：`a9a6d65d782497e24164163b35f2f0680fbe7c587bb6c2384390723f1d0294c3`，构建退出 0，534 个构建输入未漂移，迁移资源与源码一致。修复前 E1 为 `e1a0380ffd187f95c4e517a92412f23d429c828647b7b4f5e4a498b856f4057d`。本票三个生产文件及相关页面的摘要记录在 JSON，提交前再次核对，未宣称并行会话中的全工作树冻结。提交检查仅删除 supplement.py 文件末尾一个多余空行，Python AST 完全一致；实际执行原稿已单独保存，执行及提交摘要均记录在 JSON。

测试插件独立编译成外部 JAR，仅前移 `next_rotation_at` 并唤醒真实生产 Quartz job；没有伪造 key、轮换结果或审计数据。类名 CountedTaskConfiguration 沿用已有隔离启动器契约，本插件不包含计数业务。故障使用隔离 schema 的真实 BEFORE INSERT trigger。未自然等待 90 天、7 天或整个公钥保留期；本票验证保留期限、窗口内旧新令牌实际验签，窗口结束清理沿用 T23 已验收证据。数据库、Redis、协议和 Chrome 未使用网络 mock。未重跑全仓 check，既有 688 项绿灯不代表包含本轮后续修复。

## 七条 AC 对应

| AC | 证据 |
| --- | --- |
| 配置周期与安全切换 kid | 默认 90 天、自定义 7 天、真实 Quartz 到期切换；另一节点实际签发新 kid 并验签 |
| 幂等及失败保留有效 key | 重复唤醒不额外轮换，真实 INSERT 故障保留 ACTIVE；恢复仅递增一个版本 |
| 旧公钥覆盖验证窗口 | GRACE 与保留期限，旧令牌及新令牌均用轮换后 JWKS 实际验签；T23 过期清理契约 |
| 管理端任务、状态和审计 | 已持久化 Quartz job；密钥页告警、失败次数和计划时间；审计页成功/失败记录及详情 |
| 迁移、HTTP、前端及故障 | 实际 MySQL/Flyway、双 JVM、真实 PKCE/HTTP、Chrome 成功/拒绝/数据库故障、重启恢复 |
| 公共契约及敏感信息 | 应用 JSON 使用 Unified response、Parameter contract，JWKS 保持协议 Raw；请求检查头/body requestId，拒绝使用 SUMMER_；审计不记录私钥或异常原文 |
| 故事—设计—实现—测试—缺陷—验收 | 本文、JSON、保留红灯及原始收据、输入摘要；只提交本票白名单，保留用户改动与真实索引 |

## 复现

需本地 Kubernetes MySQL、已授权 Secret 来源、redis:7-alpine 镜像、JDK 21、Node 22 和 Chrome。每次使用新的证据目录，两个脚本串行运行，固定候选及其 SHA；端口 26081–26083、26573、26580。

```sh
export SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR="$PWD/.git/t28-unique-basic"
export SUMMER_TASK_CONFIGURATION_CANDIDATE="/absolute/path/to/pinned-candidate.jar"
export SUMMER_TASK_CONFIGURATION_CANDIDATE_SHA256="<candidate-sha256>"
python3 scripts/acceptance/automatic-key-rotation/run.py
export SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR="$PWD/.git/t28-unique-supplement"
python3 scripts/acceptance/automatic-key-rotation/supplement.py
```

运行后核对实际退出码、runtime-result.json、各 browser JSON 和 cleanup.json。凭据仅内存、环境或 stdin 传递，原始私有证据保留于 JSON 所列本地路径，未加入 Git。
