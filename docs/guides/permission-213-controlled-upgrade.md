# 受控权限域安装、升级与恢复

本指南对应本地 operator 入口，不提供 HTTP 上传映射或切换模式的接口。发布验收尚在进行；完整生产比较、恢复、收缩和最终回归的完成状态以验收记录为准。不能把一次构建、DATA `APPLIED`、隔离比较或历史夹具回执视为生产切换许可。

## 域与支持边界

普通启动只验证显式配置的 MANAGED 域，不初始化、补 mode、重建角色或恢复撤销的关系。缺 mode、非法 mode、负 revision、已有 marker 或遗留授权事实均不证明“新安装”。

新安装与旧域升级使用不同入口：

| 操作 | 入口 | 依据 |
| --- | --- | --- |
| 首次 PLATFORM 安装 | `summer.sample.authorization.installation.*` | 明确新安装批准、本域无历史事实 |
| 真正新企业 | 正式创建企业 API | 企业、成员、MANAGED 状态、管理授权、审计和 outbox 同事务 |
| 旧 bridge 登记 | `upgrade.command=enroll` | 实际旧 JAR、完整 Flyway 历史、schema 指纹、完整 Domain 列表 |
| 冻结迁移与隔离比较 | `upgrade.command=stage-approved` / `cutover-approved` | 已完成旧 writer 围栏、冻结输入、批准目标 artifact |
| 生产切换 | `cutover-approved`，`PRODUCTION_CUTOVER` | 完整 DATA HTTP 比较、当前授权事实和实际程序的精确绑定 |

当前狭窄 RUNTIME recipe 仅支持非 bootstrap ENTERPRISE，且完整持久能力清单证明两个 messages 操作永不获得旧签名能力。缺失、字符串或 null 的 `legacyScopeCompatibility` 不等于 `false`。存在相关能力、别名、禁用历史能力、目标 RUNTIME 关系或改写的旧规则均阻断；原始 snapshot、plan 和 blocker 不会被改写，派生证明另存。

既有 PLATFORM 的跨企业成员生命周期，以及 `ENTERPRISE:ent-acme` 的 bootstrap/scope 规则，仍有独立阻断。普通企业的正向证据不能解除这些阻断；不得将其当空域重新安装、全撤权、强制重新登录或放宽为 PUBLIC。

## 批准与执行材料

批准文件必须是有界的普通文件，不能由组或其他用户写入；推荐目录权限 `0700`、文件权限 `0600`。操作使用批准文件原始字节的 SHA-256。报告路径必须全新，精确重放仍使用新报告路径。数据库、Redis、备份密码只从受保护运行环境传入，不能出现在命令行、批准文件、比较材料或日志中。

专用 operator JVM 使用已批准的发布 JAR。报告成功写出后结束该专用 JVM；普通业务实例不配置 operator command。不要在存活实例使用的 classpath 或 JAR 上重编、覆盖文件。

首次安装批准格式为：

```json
{
  "format": "summer-managed-installation/1",
  "purpose": "FRESH_INSTALL",
  "operationId": "install-platform-unique-id",
  "domainKind": "PLATFORM",
  "domainId": "platform",
  "actor": "existing-active-local-identity"
}
```

将路径、原始字节 SHA 和全新报告路径分别传给 `installation.approval-file`、`installation.approval-sha256`、`installation.report-file`。此入口在本地身份准备完成后运行。已完成操作的精确重放不补授权；缺 mode 或 marker 的损坏域仍拒绝。

## 旧程序登记与写入围栏

1. 通过 `upgrade.command=inspect` 检查实际 `upgrade.bridge-artifact`。程序必须命中 `authorization/trusted-upgrade-bridges.json` 的实际 JAR SHA、历史 SQL 内容和完整成功 Flyway 历史；未知程序和改写的历史脚本均拒绝。
2. 审阅只读报告，以 `summer-bridge-enrollment/1`、`ENROLL_EXPLICIT_LEGACY` 批准完整 Domain 列表，再运行 `enroll`。缺 mode 仅能由这一显式可信来源建立 LEGACY，不由普通启动推断。
3. 在实际旧程序上导出运行时清单并执行 capture：只读旧授权事实，同时持久化冻结 snapshot/evidence/plan 和映射账本。原 blocker 是历史证据，不通过修改 JSON 清除。
4. 使用 `scripts/acceptance/permission-management/attest_bridge.py` 对实际旧 PID、监听端口、JAR 和 class/schema 内容生成执行证明。source/schema 指纹是 operator 明确提供的绑定，不能冒称自动从进程发现。
5. 以 `summer-old-writer-fence/1`、`FENCE_DEDICATED_OLD_ACCOUNT` 批准本源 schema、专属旧账号和 host、执行证明 SHA、登记批准 SHA、snapshot 和 source 指纹，运行 `upgrade.command=fence`。operator 数据库连接须指向批准源库，使用独立的管理身份。

同一旧 username 多 host、继承角色、代理、全局权限、其他 schema 权限或可转授权限不自动清理；入口会拒绝。该 recipe 要求旧 writer 集群共享批准的专属身份，不能遗漏仍可写入的其他身份。

`REVOKE` 本身不能隔离已持有 MySQL 数据库级缓存权限的连接。正式入口将旧账号降为本源 schema 的 SELECT，并重新核对、排空该账号在本源库的已登记连接，保存 `FENCING` / `FENCED` 检查点。任何后续 stage/cutover 均重新核对实际源库权限和围栏账本。不得用共享服务重启、共享 FLUSH、改 mode 或关闭一个进程代替写入隔离。

## 加密备份与隔离恢复

使用 `encrypted_backup.py create` 从 SQL stdin 创建认证密文。`SUMMER_BACKUP_PASSWORD` 应为独立保管的随机密码，至少 32 字节；不得从应用 master key 派生。`SUMMER_OPENSSL` 应选择受信 OpenSSL 3。将 manifest SHA 在独立批准材料中固定。

支持的 dump recipe 为单 schema `mysqldump`，可使用 `--single-transaction --set-gtid-purged=OFF --no-tablespaces --skip-comments`；不要使用 `--databases`、`--all-databases`、数据库选择语句或客户端命令。SQL 中的二进制字段按原始字节传输，不先转 UTF-8 文本，不写明文临时文件。

正式恢复使用 `restore_backup.py`，不能把解密 stdout 交给拥有源库写权限的客户端。目标必须预先存在、为空且与源库不同。源查询 transport 只用于固定只读检查；实际导入使用独立的目标账号，并逐条校验其真实 `SHOW GRANTS`。该账号只能具有目标 schema 的直接权限，无继承角色、代理、全局权限或 grant option；单 schema dump 所需权限包括 SELECT、INSERT、CREATE、DROP、ALTER、INDEX、REFERENCES 和 LOCK TABLES。

CLI 以 `SUMMER_RESTORE_MYSQL_USER` / `SUMMER_RESTORE_MYSQL_PASSWORD` 指定独立导入身份。该客户端启用 binary mode。HMAC、批准 manifest、源绑定、密文字节和解密后 dump 指纹均通过后才导入；完成后比较实际源库与目标库的冻结 snapshot/evidence/plan 哈希、mode/revision 和登记来源，再写 `summer-verified-isolated-restore/1` 回执。

MySQL DDL 不假设整体回滚。失败目标可能已有部分表，不能原地覆盖重试，也不会得到 verified 回执；保留失败诊断，准备新的空隔离目标。清理仅限本次登记的 schema、账号和进程。

## 分批 stage、DATA 与比较

`stage-approved` 的批准格式为 `summer-managed-upgrade-stage/1`、用途 `STAGE_FROZEN_ENTERPRISE`，包含 actor、snapshotId、sourceFingerprint 和执行目标 JAR 的 `targetArtifactSha256`。每批重新检查围栏，使用冻结输入续跑；`maximum-batches` 和 `batch-size` 有界。stage 不切换 mode。

DATA prepare/stage/verify 复用独立派生计划，精确固定 sourceFingerprint、planHash 和 expectedRevision。原有身份步骤和 DATA 步骤都必须完成。`APPLIED` 只表示写入完成；真实验证回执仍须覆盖所有证明中的 subject/default/resource/action/operation，包含完整列表、total、detail 和有实际持久变化的写操作。401、业务冲突、错误依赖或空操作不能充当策略拒绝。删除须有真实非空项作为见证，拒绝写须证明记录、历史和业务 outbox 未变。

比较中的旧实例运行在由同一认证备份恢复的独立 LEGACY 副本，使用只可写该副本的身份；原生产旧 writer 继续保持围栏。新实例运行在另一隔离恢复库。两侧复用 stage 前真实 PKCE 签发的凭据，不以重新登录代替旧 Token 证明。

隔离目标必须通过 `summer-controlled-cutover/1`、`ISOLATED_COMPARISON` 明确批准。该事务更新隔离库的 mode、递增 revision、marker、审计、outbox 和操作回执，始终返回 `cutoverAuthorized=false`。比较副本已是 MANAGED，不支持在同库再提升为生产。

## 生产切换边界

当前 recipe **仅提升已围栏的源库**。先在仍为 LEGACY 的源库完成相同冻结 stage 与 DATA 验证，再以 `PRODUCTION_CUTOVER` 批准源库。批准须固定 snapshot/source、expectedRevision、目标 JAR、当前完整授权事实指纹、verified restore 回执、DATA plan/verification 以及完整 HTTP 比较输入的 SHA。

比较输入的 `runtime.controlledUpgrade` 必须绑定 sourceFingerprint、authorizationFingerprint、targetArtifactSha256、planHash、restoreReceiptSha256、oldArtifactSha256、sourceSchema 和原围栏执行证明 SHA。服务重新计算 canonical runtime input 指纹，不能用另一组事实的 VERIFIED 回执切换。

切换前重新验证资源 incarnation、全部权限/角色/用户/资源关联和 DATA 目标事实，拒绝额外或缺失目标授权。成功时 mode、单调递增的 revision、marker、审计、outbox 和回执在同一事务中提交。精确重放不重建授权、不重复审计或 outbox；已完成回执不能修复丢失 mode/marker，也不能撤销后自动恢复权限。

## 缺失 MANAGED mode 的独立恢复

恢复不调用首次安装入口。先用 `summer.sample.authorization.recovery.command=inspect` 与 `domain-key` 为健康域生成完整事实指纹；全域检查可使用 `inspect-all-managed`。在业务与权限写入静止时取得认证加密备份，以 `MANAGED_RECOVERY` 目的独立恢复至空隔离库，并保存 `summer-verified-managed-recovery-restore/1` 回执。备份查询身份只能读取该独立恢复库。

`recover-approved` 要求格式 `summer-controlled-mode-recovery/1`、用途 `RECOVER_MANAGED_DOMAIN_MODE_ONLY` 的受保护批准文件。批准固定 operationId、actor、requestId、完整 domainKey、目标与备份 schema、backupRevision、实际目标 JAR SHA、备份 manifest SHA、恢复回执 SHA 和 recoveryFingerprint。以 `approval-file`、`approval-sha256`、`restore-receipt-file`、全新 `report-file` 传入；`recovery.backup-url`、`backup-user` 和受保护环境中的 `backup-password` 指向独立只读恢复库。

该入口只恢复完全缺失的 mode 行，不覆盖已有或非法状态，不导入或重建授权。当前授权关系、历史、备份来源和实际制品须与批准一致；撤权后取得的备份才能证明撤权后的状态，旧备份不得恢复旧 ALLOW。成功时仅写 MANAGED mode、递增 revision 和恢复审计/事件/回执；同批准精确重放不重复写历史。其后再次丢失 mode 仍拒绝普通启动，也不能复用已完成回执自动修复。

## 退休凭据与物理存储收缩

使用 `summer.sample.authorization.contract.command=inspect` 生成全域 MANAGED 状态及 stateFingerprint。此检查枚举 mode、当前关系和历史所属域；有孤立授权/历史、未解决 LEGACY 域或域状态损坏时拒绝。不能只枚举现有 mode 行而漏掉已删除 mode 的域。

在写入静止后重新取得完整认证备份，以 `MANAGED_CONTRACT` 目的独立恢复，保存 `summer-verified-managed-contract-restore/1` 回执。该备份固定全部当前 Domain、授权关系、revision 和变更历史；单域恢复回执不能替代它。实际原生管理验收另提供 `summer-managed-native-contract-acceptance/1`，绑定同一目标 JAR 和 stateFingerprint，并逐项记录要求的页面场景。

执行 `contract-approved` 需要格式 `summer-controlled-authorization-contract/1`、用途 `RETIRE_LEGACY_AUTHORIZATION_STORAGE` 的批准。批准包含 operationId、actor、requestId、目标 schema/JAR、stateFingerprint、备份 schema/manifest/恢复回执 SHA、原生管理回执 SHA，以及精确的 retiredWriters 和独立 newRuntime 的 username/host。通过 `contract.approval-file`、`approval-sha256`、`restore-receipt-file`、`management-receipt-file` 和全新 `report-file` 输入；操作身份、退休业务身份与新业务身份必须分离，密码只在受保护运行环境中消费。

正式入口先逐一核对账号 host 唯一、直接单 schema 授权、无继承角色/代理/全局权限，再降低全部退休 writer 为 SELECT、排空其旧连接并持久记录 PREPARED。它按检查点移除旧资源身份表及角色上的 capability_ids、capability_codes、bundle_id。DDL 不具有整体事务回滚；中断后保持同一批准原始字节、operationId 和新报告路径续跑，不另造批准或恢复旧列。完成后才给新 runtime 账号授予当前业务所需的 SELECT/INSERT/UPDATE/DELETE；精确重放重新核对已移除存储和双方权限，不重复修改授权或历史。后续凭据轮换属于另一项独立操作。

验收须在围栏前完成一次真实业务写入并记录原 JVM 身份；围栏后用同一仍有效凭据核对该 JVM 的写失败和业务事实不变，另以精确退休数据库账号取得权限拒绝见证。401 不能证明 SQL 围栏。切换至新账号的两个节点须完成当前管理员成功、同一旧主体撤权仍拒绝及实际新写入；物理部分执行或仅健康检查不算完成。

## 尚未完成的发布门禁

当前发布状态以 [Issue #213](https://github.com/abluepoint/summer/issues/213) 的逐项验收记录为准。物理收缩、全 Domain 阻断、当前制品原生管理、最终源码冻结后的完整回归和全部追踪项，均须绑定各自实际来源与验收结论；已接受的原生管理及基础回归不替代尚待核销的高级场景、迁移比较或协议回归。

历史 RED、失败及部分结果保留，后续静态修正或新批通过不改写旧批结论；2026-11-09 因测试 worker 内存不足中断的首批全量结果也不计作完整覆盖。正在运行和待执行的批次须在结果、输入摘要及资源释放核对后单独结算，最终计数按实际结果回填。门禁完成前，本指南不构成生产发布或破坏性 DDL 的执行批准。
