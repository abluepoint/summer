# T21 数据库签名密钥验收

GitHub Issue #129 的三处故障缺陷已在固定候选上完成真实复验，状态为 `READY_FOR_COORDINATOR_CLOSEOUT`。主会话负责最终全仓检查、提交、推送和关票。本验收不代表这些集成动作已经完成。

机器可读记录见 [验收结果](security-keys-129-result.json)，包含逐项检查、候选摘要、原 AC 映射和清理结果。

## 候选与证据范围

最新候选 SHA-256：`442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453`。

2026-10-15 的定向复验使用真实 MySQL、生产迁移、网页登录和 OAuth2 授权码 PKCE，不模拟网络。通过临时改名本轮独立数据库中的密钥表制造故障，恢复表名后重新登录签发。此次仅重放原失败及恢复；此前成功的密文、双节点、重启和 Chrome 场景保留其原候选证据。

| 验证批次 | 候选 | 结果 | 范围 |
| --- | --- | --- | --- |
| 最新实际故障复验 | `442e609f` | 9 项通过，0 失败 | 三处原缺陷、失败关闭及恢复 |
| 保留的实际功能验收 | `9976bf12` | 23 项通过，0 失败 | HTTP 成功、拒绝、无效输入，MySQL 密文，双 JVM、重启、生成后验签 |
| 保留的实际 Chrome 验收 | `9976bf12` | 6 项通过，0 失败 | 登录、元数据、算法与健康、无私钥编辑器、受限生成、拒绝页 |
| 本地审计与 MVC 回归 | 修复源码 | 19/0/0/0 | tests/failures/errors/skipped |
| 新增密钥失败审计测试 | 修复源码 | 9/0/0/0 | 真实 Spring/H2 故障、真实编码器失败、安全审计边界 |
| 既有密钥兼容回归 | 修复源码 | 10/0/0/0 | 签发验签、轮换、引用密钥配置 |

各批次分别计数，不将本地测试与实际验收累加为同一轮。保留的 23 项排除了原批次初始登录准备项及故障检查；原始运行时结果 `31/28/3`、早期定向结果 `6/4/2` 均未覆盖或改判。原批次另有一条“故障必须返回 500”的过严断言，已按合法 401/403 失败关闭契约纠正。

## 三处缺陷的实际复验

| 原问题 | 新候选结果 |
| --- | --- |
| 认证失败审计把原始 Bearer 当作 principal | 已知临时 Bearer 和 JWT 模式扫描均无敏感命中；认证失败仍关闭访问 |
| JWK HTTP 500 经错误调度被包装为 `success:true` | 保留 HTTP 500 的 Raw 错误体，无成功信封；`X-Request-Id` 头存在 |
| 密钥读取或签名失败缺少专项审计 | 实际故障期间均观察到 `key_read_failure` 与 `key_sign_failure`；恢复后真实 PKCE 签发成功 |

JWK 是 Raw 协议端点，其故障体不要求应用层 `SUMMER_*` 错误码或 body `requestId`。关联头保留；受保护的管理接口仍使用应用错误契约。

失败事件只携带固定操作、失败类别、安全 keyId 和 requestId，主体固定为 `system`。不向审计接口传递令牌、claims、私钥、密文或原始异常正文。审计实现若依赖故障数据库并抛异常，则使用既有 SLF4J 通道兜底，同时保留原业务失败。成功的 JWK 读取和签发不新增失败日志。

## 原验收条款映射

| AC | 验证依据 | 判定 |
| --- | --- | --- |
| 1 MySQL 存密文及密钥元数据，主密钥不落库 | 实际迁移与列结构检查；仅在内存解密并与公开 JWK 匹配；无主密钥或明文私钥列 | PASS |
| 2 无私钥明文读取 API | 管理元数据字段白名单、公开 JWKS 字段、页面无私钥输入或回显 | PASS |
| 3 重启沿用有效 kid 并可签发验签 | 原双 JVM、重启同 kid；页面生成后另一节点使用新 kid 签发并验签 | PASS |
| 4 读取或签名失败具备失败响应、审计和脱敏日志 | 最新实际表故障、读取与签名专用审计、无 Bearer 泄漏、恢复登录 | PASS |
| 5 迁移、真实 HTTP 和 SummerUI 的成功、拒绝、无效或故障路径 | 保留功能 23 项及 Chrome 6 项，最新故障与恢复 9 项 | PASS |
| 6 Parameter、Unified/Raw、correlation、SUMMER 与敏感数据边界 | 原应用错误契约；最新 Raw 500 无成功包装且关联头存在；安全审计字段测试 | PASS |
| 7 故事、实现、测试、缺陷与证据可追踪，计数准确并保留已有修改 | 故事 3/34/35/36/37、受控脚本、源码摘要、原红灯、本地回归和真实复验均保留 | PASS |

## 复现与清理

入口为 `scripts/acceptance/keys/fault.py`，完整功能及浏览器入口为同目录 `run.py`。执行前应使用固定候选、全新的证据目录，并确保本会话专用端口空闲。运行环境需要既有本地 MySQL/Kubernetes 访问、Docker、JDK 21；脚本创建随机独立 schema、数据库账号和 Redis 容器，凭据只在内存、环境或标准输入中消费。

从仓库根目录运行定向复验，候选路径必须指向上述摘要对应的不可变 JAR：

```sh
SUMMER_IDENTITY_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t21-live/new-fault-replay" \
SUMMER_IDENTITY_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/candidate-442e609f/candidate.jar" \
SUMMER_IDENTITY_CANDIDATE_SHA256=442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453 \
python3 scripts/acceptance/keys/fault.py
```

本次唯一新目录为 `.git/summer4-closeout-20261015/t21-live/attempt-06-442e609f-three-fixes`。其中 `fault-result.json`、`cleanup.json`、`candidate-provenance.json` 和 `final-integrity.json` 分别保存检查、清理、候选来源及摘要核验。原始运行日志和凭据不归档。

清理回读确认临时 schema、数据库账号、Redis 容器已不存在，自有 JVM 已退出，21081、21082、21083、21573、21580 均无监听。现有共用 MySQL 实例及其他会话资源保留。

联合文件白名单与 SHA-256 位于 `.git/summer4-closeout-20261015/t21-live/FILES.json`；6 个生产文件与候选的 532 项构建输入逐项核对一致。本轮实际复验没有修改主源码或 JUnit，也未操作 Git 索引或 Issue。
