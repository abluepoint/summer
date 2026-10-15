# T24 OAuth 客户端与授权持久化实际验收

2026-10-15，[Issue #132](https://github.com/abluepoint/summer/issues/132)，用户故事 1、34。原票依赖 #123、#129 在领取时均已关闭。本次结论为 READY：七项 AC 均有实际证据，未发现需要修改 OAuth 生产代码的缺陷。本次只增加验收脚本和报告，未执行 Gradle/JUnit，也未提交或关闭 Issue。

固定后端候选 SHA-256：`917307813cd8d81476b3e28f36c9b9c0f7465185d55ba053cebb893dfbdbc0d2`。证据目录为仓库内 `.git/summer4-closeout-20261015/t24-live/`；机器可读摘要见 [验收结果](oauth-persistence-132-result.json)。此 SHA 是实际运行的 JAR，不等同于交接时正在推进的仓库 HEAD。

## 实际执行数量

下表依次报告 tests / failures / errors / skipped，不把补验称为完整重跑。

| 批次 | 数量 | 结果与证据 |
| --- | --- | --- |
| 首轮真实 HTTP、协议与持久化 | 48 / 1 / 0 / 0 | `attempt-01-91730781/result.json`；47 项通过，1 项过期时间夹具错误，原结果保留 |
| 首轮真实 Chrome 正常及拒绝路径 | 15 / 0 / 0 / 0 | `attempt-01-91730781/browser-normal.json`，exit 0 |
| 首轮真实 Chrome 数据库故障路径 | 2 / 0 / 0 / 0 | `attempt-01-91730781/browser-fault.json`，exit 0 |
| 过期夹具、默认种子重启及拒绝写入补验 | 13 / 0 / 0 / 0 | `attempt-02-seed-expiry/result.json`，exit 0 |
| 已有实际撤销审计的离线核对 | 1 / 0 / 0 / 0 | `attempt-01-91730781/consent-audit-validation.json`；不计作新增 HTTP 执行 |

按检查名采用最新结果，运行时共 59 个不同检查，全部通过；这只是跨批去重后的覆盖汇总，不是单次 59 项执行。Chrome 共 17 项，两个批次 pageErrors 均为 0，无网络 mock。既往组件测试或 JUnit 数量不计入本次。

## 原票 AC 与证据

| AC | 结论 | 实际验证 |
| --- | --- | --- |
| 1. public client 重启后配置和协议保持一致 | PASS | 两 JVM 重启后自建客户端配置和状态不变、待兑付授权码可兑换；补验通过真实管理 API 修改并停用配置种子 `summer-ui`，重启及新节点均未覆盖，协议拒绝；重新启用后 S256 授权恢复 |
| 2. 注册、修改、停用具有权限和审计 | PASS | 注册 201、重复 409、编辑 200、版本冲突 412；双节点同版本更新一成一败 200/412；无权读取、创建、编辑、停用、撤销均 403；创建、更新、停用及撤销实际审计含操作者和 requestId |
| 3. 同意与授权码生命周期符合 ADR | PASS | 专用客户端实际同意页生成记录，重启保留，Chrome 管理页撤销 200；授权码跨重启兑换、单次使用、五分钟 TTL、合法过期夹具返回 400 `invalid_grant`；access/ID token 十五分钟，无 refresh token |
| 4. PKCE、issuer、redirect、scope | PASS | 真实 S256、state、nonce、issuer 和 scope；错误 verifier、缺失 PKCE、plain、错误 redirect、未知 scope 均拒绝；修改后的 redirect/scope 实际生效；refresh_token 与 client_credentials grant 均 400 |
| 5. 迁移、真实 HTTP 和 Summer UI | PASS | MySQL OAuth 表迁移应用；独立非 root 账号、Redis、双 JVM 与真实 Chrome；覆盖成功、无权限、七种非法输入、版本冲突及真实数据库更新故障 |
| 6. 契约、响应、关联与敏感信息 | PASS | 管理接口沿用 Unified 与 `SUMMER_` 领域错误，错误 body/header requestId 一致；token 协议响应保持 Raw；管理投影无密钥，页面无 secret 编辑器；浏览器存储和 cookie 检查未见 token；运行敏感检测零命中 |
| 7. 故事—设计—实现—测试—缺陷—证据 | PASS | 本报告、逐项 JSON、原始失败与修正说明、源码 SHA、候选来源、截图和清理收据完整保留；本次未改生产源码及已有用户改动 |

设计依据为 [ADR 0015](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md)。实现对应 `OAuthClientManagementController`、`JdbcSummerOAuthClientStore`、`JdbcOAuthStorageConfiguration`、`JdbcSummerAuthorizationStore`、`JdbcSummerConsentStore`，迁移 `V24__oauth2_persistent_storage.sql`，以及 `summer-ui/src/pages/oauth-clients/` 页面、API 和词条。验收入口为 `scripts/acceptance/oauth-persistence/run.py`、`seed-probe.py` 与 `browser.mjs`；协议和环境支持位于同目录 `protocol.py`、`oauth_environment.py`。

## 夹具、失败与证据边界

Summer UI 首方客户端仍无需同意页。为覆盖同意持久化，仅在自有 schema 中将单独注册的 `t24-consent` 客户端的 require-authorization-consent 设置为 true；同意记录由真实授权协议页面提交产生，没有通过 SQL 插入同意、授权码或 token。

首轮过期夹具只把 expiresAt 改到过去，却保留较晚的 issuedAt，违反 Spring `AbstractOAuth2Token` 的 expiresAt 必须晚于 issuedAt 约束，因而得到 500。这是验收脚本错误。原执行脚本保存在 `attempt-01-91730781/executed-run.py`，原因记录在 `harness-correction.json`。运行结束后修正为 issuedAt 六分钟前、expiresAt 一分钟前，保持合法五分钟区间；补验实际得到 400 `invalid_grant`。这属于显式时间夹具，没有自然等待五分钟，也没有修改生产时钟。

数据库故障通过自有 schema 的 UPDATE 触发器制造，Chrome 实际收到 500 并保留草稿和 requestId。已核对注册、编辑、停用、撤销和非法输入的审计；没有据此声称数据库故障都产生了领域失败审计。无权撤销使用不存在的对象验证授权先于查找，不将其夸称为既有同意记录完整性测试。

五张截图均已目视核对：`oauth-metadata.png`、`oauth-conflict.png`、`oauth-edited-and-revoked.png`、`oauth-denied.png`、`oauth-fault.png`。撤销截图捕获成功提示及列表刷新中状态，撤销成功以实际 DELETE 200、成功提示和对应审计共同确认，不能只凭该帧声称记录已从列表消失。

首轮监测 251 个输入，唯一变化为其他任务拥有的 `summer-ui/src/pages/data-scopes/index.tsx`，因此原收据 `inputsStable=false` 原样保留。T24 的五个后端及三个 UI 输入从基线至交接均匹配；最终五个脚本另有 SHA 冻结。不能把本票稳定扩展为全仓稳定。

两轮均核验自有 schema 和应用账号删除、Redis 容器销毁、JVM/Chrome/Vite 退出及 21081/21082/21083/21573/21580 无监听。已清除本票 Python/Vite 缓存，保留其他任务缓存和所有原始验收结果。密码、token、授权码、verifier、私钥不写入报告、原始日志或截图。

## 复验入口

须使用仍可用且 SHA 匹配的固定候选、本机既有 MySQL/Redis/Java/Chrome/Node 环境及空闲专用端口；每轮使用新的证据目录。脚本会创建并清理自有 schema、非 root 应用账号和 Redis 容器。

```sh
export SUMMER_IDENTITY_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/frozen-build-03/candidate.jar"
export SUMMER_IDENTITY_CANDIDATE_SHA256=917307813cd8d81476b3e28f36c9b9c0f7465185d55ba053cebb893dfbdbc0d2
export SUMMER_IDENTITY_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t24-live/replay-full"
python3 scripts/acceptance/oauth-persistence/run.py
export SUMMER_IDENTITY_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t24-live/replay-seed"
python3 scripts/acceptance/oauth-persistence/seed-probe.py
```

当前 `run.py` 已修正过期夹具；本次实际执行的是保留的原版本加针对性补验，没有执行修正后完整重跑。
