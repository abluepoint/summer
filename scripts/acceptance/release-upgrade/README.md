# T36 原生升级验收

这些脚本使用真实应用 JAR、MySQL、独立 Redis、PKCE 登录和 Chrome。业务授权、参数、任务和插件配置全部走正式 HTTP；SQL 仅用于自有基础设施和只读核对。每次运行必须使用 `.git/summer4-closeout-20261015/t36-final/` 下未使用过的子目录，原结果不覆盖。

本次#144的七项依赖已逐票回读CLOSED/COMPLETED。候选02原生scope、迁移、滚动后端、备份恢复、Chrome正常/撤权/恢复/WS及缓存故障分批完成；Redis会话超时500/success=true缺陷由root有限修复；候选03受影响补验85/0/0/0、Chrome5/0/0/0通过，Issue最终关闭由root执行。脚本不会构建 JAR、提交代码或修改 Issue。

## 运行要求

- root 提供的不可变新候选、SHA256 及同目录 `candidate-inputs.json`。
- 真实不同代码的旧候选及 SHA256。
- 本机已有 Java 21、Node 22、Chrome、Docker、kubectl；使用 `docker-desktop` context 的 `local-base/mysql-0`，应用使用随机独立数据库账号。
- MySQL root 凭据经 Kubernetes Secret 进入进程内存和 stdin；应用主密钥、登录密码及备份口令随机生成，不能复制到命令行或报告。
- 23081/23082/23083、23573/23580 未占用。Redis 使用独立容器及随机主机端口。

先取得含scope及会话Redis超时补救的candidate03，设置 `SUMMER_RELEASE_UPGRADE_CANDIDATE` 与 `SUMMER_RELEASE_UPGRADE_CANDIDATE_SHA256` 为root提供的实际路径和摘要；候选01（6fbb）保留作历史，不能用于最终验收。再运行：

```sh
export SUMMER_RELEASE_UPGRADE_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t36-final/native-new-unique-run"
export SUMMER_RELEASE_UPGRADE_CANDIDATE="$PWD/.git/summer4-closeout-20261015/t36-final/frozen-build-03/candidate.jar"
export SUMMER_RELEASE_UPGRADE_CANDIDATE_SHA256=e9d1ff6ef33b74c1c8ffef9bc6417b06554d92490f8b1e814c5dec49d3d2f28e
export SUMMER_RELEASE_UPGRADE_OLD_CANDIDATE="$PWD/.git/summer4-closeout-20261015/t34-live/frozen-build-01/candidate.jar"
export SUMMER_RELEASE_UPGRADE_OLD_SHA256=28a48d6cda86996d2d1b4d396c854e3d51d671ccc7994a41029daa567df02d18
python3 scripts/acceptance/release-upgrade/run.py
```

`run.py` 验证十项预检、失败信封、未执行门禁不伪报、Cookie 会话、混合版本缓存/事件/任务/SSE、签名密钥轮换、插件兼容性、滚动期间持续读取、持久权限撤销、加密备份与新库恢复，最后通过真实 Chrome 检查预检成功/依赖故障/拒绝/恢复，以及 WebSocket 跨节点消息和重放。

历史迁移演练使用另一个新证据目录，并把旧候选改为：

```sh
export SUMMER_RELEASE_UPGRADE_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t36-final/migration-new-unique-run"
export SUMMER_RELEASE_UPGRADE_OLD_CANDIDATE="$PWD/.git/summer4-review-20261007/t36-live/rolling-old.jar"
export SUMMER_RELEASE_UPGRADE_OLD_SHA256=546510c7646131b34fbb5fa5e8088074de913a4e919dac0f74862a4363dc9b20
python3 scripts/acceptance/release-upgrade/migration_probe.py
```

`migration_probe.py` 先在旧应用写入参数和权限，再执行真实加密备份/恢复，由新应用在恢复库执行 31→39 份迁移。它检查 V32 先增加租户复合唯一约束再移除全局唯一约束、V36 保留既有受管用户的有效成员身份，并通过新应用读写恢复的业务值。原31迁移源库保持不变。本地历史候选不代表任意生产旧版本的升级兼容性。

`revocation_probe.py` 是范围更窄的真实旧令牌撤权回归。所有脚本结果均报告 tests/failures/errors/skipped；编译或语法检查不计入真实验收。`cleanup.json` 核对自有库、账号、Redis 容器和监听端口均已清理。加密临时备份在结束时删除，明文 dump 始终只在内存/管道中。

`scope_probe.py` 专门验证同码scope边界：正式创建read/write各自的PLATFORM与ENTERPRISE能力，仅移除PLATFORM授权并保留ENTERPRISE同码授权，复用真实旧JWT检查读写403、错误码及requestId，再恢复权限。它使用单独的新证据目录和同一候选02；候选02已实际执行93/0/0/0、exit0，清理通过。

`ui_probe.py` 是独立Chrome补批：仅配置正式身份/能力、启动两节点并验证十项预检、Redis真实故障及恢复、持久拒绝清旧结果、WS跨节点投递/心跳/重放。故障契约失败会保留红灯并继续独立恢复场景，最终仍非零退出；不将500 success=true接受为受控降级。它不会重跑已完成的滚动/备份矩阵。

`cache_probe.py` 是独立缓存故障补批：实际预热L2、暂停自有Redis、正式API读写、验证当前值或503/SUMMER_DEPENDENCY_UNAVAILABLE且无数据、受限用户403；恢复后在45秒窗口内核两节点最终收敛，不假定首读强一致。候选02本批76/0/0/0通过，与浏览器会话故障独立。

候选02迁移最终54/0/0/0通过；综合批214/1/1/0、UI02–05及迁移首批的原始失败全部保留，逐批详情见[当前验收报告](../../../docs/guides/summer4-t36-final-acceptance.md)。后续候选必须由root提供新不可变路径、摘要及输入清单，不能覆盖旧制品或用新结果改写旧失败。

候选03仅实际运行受影响`ui_probe.py`补批，额外检查两节点纯Cookie503/no-store/无数据和纯Bearer200 NOT_READY、两类恢复；未重跑02的全业务矩阵。复用依据与原始各批计数见报告；脚本说明中的完整run.py命令是可复现入口，不能当作已经对03执行。
