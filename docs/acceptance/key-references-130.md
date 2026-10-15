# T22 引用型密钥验收

Issue #130 的环境变量、受控文件及 Kubernetes Secret 引用已完成真实验收，状态为 `READY_FOR_COORDINATOR_CLOSEOUT`。上游 T21 #129 已关闭为 COMPLETED。最终全仓检查、提交、推送和关票由主会话处理。

固定候选 SHA-256：`442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453`。逐项结果见 [JSON 验收收据](key-references-130-result.json)。

## 实际结果

| 批次 | 结果 | 验证内容 |
| --- | --- | --- |
| 真实运行时与 HTTP | 45 项通过，0 失败 | 三类来源、四种注入路径的启动、PKCE 签发、公开 JWK 验签、元数据、拒绝、关联 ID、失败关闭及敏感输出扫描 |
| 真实 Chrome | 6 项通过，0 失败 | 登录、引用元数据、来源/算法/kid、存在性与 READY、无私钥编辑器、无权限页面 |
| 无效引用启动 | 已包含在上述 45 项内 | 13 个场景均退出码 1，耗时 7.87–12.5 秒，含固定 `SUMMER_KEY_REFERENCE_*` 错误码 |

运行器和浏览器均退出 0，浏览器 page error 为 0。本阶段没有执行 Gradle/JUnit，也没有修改主源码、JUnit 或 UI。启动进程次数不额外计为用例，运行时与 Chrome 数量分开报告。

首轮在初始环境引用启动时观察到健康探针等待 100 秒超时，进程仍存活，没有引用错误码。该次未完成验收，根因未确定，原始 1 项失败及清理记录保留。补充脱敏诊断后第二轮完整通过，期间没有产品代码修复；不把首轮失败改写为通过。

## 来源与失败边界

有效环境变量、受控目录内的 PKCS8 文件、Secret 环境变量注入、Secret 挂载文件材料分别完成真实网页登录及授权码 PKCE 签发，再使用公开 JWK 验证签名。管理 HTTP 仅返回元数据字段，页面没有密钥原值或私钥编辑器。

Kubernetes 验证创建本轮唯一 Secret 和临时 Pod，通过 `env.valueFrom` 与 Secret volume 完成真实注入，再仅在内存中读取注入结果，供本机候选 JVM 的环境变量或受控文件引用使用。JVM 本身运行在本机，未宣称将应用部署到 Kubernetes。临时 Pod 使用集群已有的固定 Redis 镜像，仅运行等待命令，不连接或修改共享 Redis。

13 个失败关闭场景为：

1. 环境变量不存在。
2. 环境变量引用名格式错误。
3. 将私钥原值错误地作为引用名。
4. 环境变量中的私钥格式错误。
5. 引用文件不存在。
6. 使用绝对文件路径。
7. 相对路径逃逸允许目录。
8. 符号链接指向允许目录外。
9. 未配置允许目录。
10. 文件不可读，且先确认验收进程确实没有读取权限。
11. 文件中的私钥格式错误。
12. Secret 注入引用不存在。
13. Secret 注入材料格式错误。

这些启动失败不会提供 HTTP 服务，因此以真实进程退出和固定配置诊断验收，而非伪造 HTTP 错误响应。正常管理接口使用 Unified response，拒绝接口返回 `SUMMER_FORBIDDEN` 并保留匹配的 requestId；JWKS 使用 Raw response。最终数据库密钥表仍无生成记录，未发生数据库随机密钥回退。

引用材料在启动时解析；本次失败检查针对启动配置与来源，不宣称支持运行中自动重新加载外部密钥。共享管理页面仍显示通用轮换控件和 UNKNOWN 轮换计划，本票只验证引用元数据及拒绝流程，未验证或宣称引用密钥轮换能力。

## 原验收条款映射

| AC | 实际依据 | 判定 |
| --- | --- | --- |
| 1 引用名与真实值分离，配置提示格式错误 | 合法名称引用成功；非法名称、内联私钥误作名称、非法材料均明确失败 | PASS |
| 2 有效引用签名验签，失效引用快速失败 | 四条有效注入路径的真实 PKCE 与公开 JWK 验签；13 个无效场景退出 | PASS |
| 3 管理端显示安全元数据而非原值 | HTTP 字段白名单；Chrome 来源、算法、kid、READY；API `exists=true`；无私钥内容 | PASS |
| 4 环境、文件、Secret 的失败关闭 | 缺失、格式、目录、绝对路径、符号链接及权限边界均为真实 JVM 验证 | PASS |
| 5 所需迁移、真实 HTTP、Summer UI 及成功/拒绝/无效或故障 | 复用 T21 已验收的迁移基础，本票无新增迁移；生产候选在真实 MySQL 上启动，45 项运行时和 6 项 Chrome 检查，无网络 mock | PASS |
| 6 Parameter contract、Unified/Raw、correlation、SUMMER 与敏感边界 | 管理成功/拒绝信封、关联 ID、Raw JWKS、固定引用错误码；敏感扫描无命中 | PASS |
| 7 故事、设计、实现、测试、缺陷与证据追踪 | 对应故事 3/35；原票、来源实现、专用脚本、首轮未完成记录、最终证据和源码摘要保留 | PASS |

## 复现

从仓库根目录运行。环境需具备现有 `docker-desktop/local-base` MySQL/Kubernetes 访问、Docker、JDK 21、Node 22 和 Google Chrome。脚本使用独立 MySQL schema/账号、Redis 容器和 21081/21082/21083/21573/21580 端口；执行前应保证端口空闲，并为每次运行选择新的证据目录。

```sh
SUMMER_IDENTITY_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t22-live/new-reference-replay" \
SUMMER_IDENTITY_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/candidate-442e609f/candidate.jar" \
SUMMER_IDENTITY_CANDIDATE_SHA256=442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453 \
python3 scripts/acceptance/key-references/run.py
```

私钥由本轮进程生成，环境模式只在内存传递。文件模式所需材料仅临时写入本轮 0700 目录和 0600 文件；权限失败夹具临时设为 000。Secret payload 通过标准输入或进程内捕获输出传递。回执不包含原始 JVM 日志、Token 或私钥。

## 证据与清理

最终唯一目录为 `.git/summer4-closeout-20261015/t22-live/attempt-02-442e609f-diagnostics`，包含 `result.json`、`browser-result.json`、`cleanup.json`、候选来源、源码前后摘要及已目视检查的元数据/拒绝页截图。首轮目录单独保留。

清理回读确认临时 schema、数据库账号、Redis 容器、Kubernetes Secret、注入 Pod 和私钥目录全部不存在，自有 JVM、Chrome 与 Vite 已结束，五个自有端口均无监听。共享 MySQL、Redis 及其他会话资源保留。

15 个 key 生产输入与固定候选一致，密钥页面前后不变。宽范围 UI 摘要仅发现其他会话的 `system-parameters/index.tsx` 变动，未据此重复无关验收，也未宣称整个 UI 全程无变化。本票联合文件白名单和 SHA-256 见 `.git/summer4-closeout-20261015/t22-live/FILES.json`。
