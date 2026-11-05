# 旧授权预演与分批迁移（#211）

本工具只执行 expand：盘点旧事实、生成逐项映射、创建新权限与收窄角色。正常应用启动不会执行迁移；没有迁移 HTTP 接口。工具始终保留 LEGACY 引擎、旧绑定和用户拒绝，不执行生产切换或 contract。DATA 由 #212 补齐，最终门禁由 #213 重新核对。

## 运行前

备份目标数据库并冻结旧授权写入。目标域必须经过明确受控的旧域登记，已有 `summer_authorization_domain` 的 LEGACY 行及有效 revision；首次旧域登记由升级 forward/operator 流程与 #210/#213 协调。capture 不创建或修复缺失模式行，MANAGED、未知或缺失状态一律拒绝，即使存在迁移账本也不能据其恢复 LEGACY。明确使用 `ENTERPRISE:<enterpriseId>` 或 `PLATFORM:platform`；企业 ID 恰为 platform 仍是 ENTERPRISE。准备最小权限的旧管理身份，在旧进程停止前导出能力目录、权限包及所有历史版本：旧权限包运行态不能靠重启后的空目录恢复。凭据仅从安全进程环境提供，不能写入命令行、报告或版本库。

```sh
python3 scripts/acceptance/permission-migration/export_legacy.py \
  --origin https://your-existing-service.example \
  --enterprise your-enterprise \
  --output /your-protected-directory/legacy-export.json
```

导出器读取环境变量 `SUMMER_MIGRATION_EXPORT_TOKEN`，拒绝重定向，创建权限 0600 的新文件。报告包含授权事实，应限制本机文件访问。没有完整来源、历史或精确资源对应时应保留阻断，不能补造公开规则。

## 操作者命令

在现有受控数据库/Redis配置下，以当前源码启动应用，在 `SPRING_APPLICATION_JSON` 中添加以下非敏感属性；不要覆盖已有数据库、会话、issuer配置。属性统一前缀 `summer.sample.authorization.migration.`。

| command | 必需属性 | 作用 |
|---|---|---|
| capture | domain-kind、domain-id、report-file；有旧运行态时 runtime-export-file | 锁定已明确登记的 LEGACY 域，读取真实旧表与当前已安装目录，持久化来源、计划和账本 |
| stage | snapshot-id、actor、report-file；可选 batch-size（1–10000，默认100） | 按权限、角色、用户顺序分批写入，并在同一事务内保存检查点、审计、revision和outbox |
| stage-frozen | snapshot-id、source-fingerprint、actor、report-file；可选 batch-size | 已批准 bridge 捕获后，在去注解代码中按同一冻结来源续跑；仍须旧表、明确 LEGACY 和原 revision |
| frozen-input | snapshot-id、report-file | 校验并读取持久化冻结输入，供 DATA/最终升级内部端口消费；不重开旧表 |
| report | snapshot-id、report-file | 回读持久化来源、映射、覆盖率、阻断和步骤 |

例如 capture 的增量配置：

```json
{
  "summer.sample.authorization.migration.command": "capture",
  "summer.sample.authorization.migration.domain-kind": "ENTERPRISE",
  "summer.sample.authorization.migration.domain-id": "your-enterprise",
  "summer.sample.authorization.migration.runtime-export-file": "/your-protected-directory/legacy-export.json",
  "summer.sample.authorization.migration.report-file": "/your-protected-directory/capture-01.json"
}
```

`report-file` 必须不存在。每次命令完成后回读该文件，再正常停止自有进程；命令不会关闭共享服务。逐批 stage 使用同一 snapshot-id，每次使用新的报告文件。来源指纹或域 revision 变化会拒绝继续；先调查漂移，不能修改账本、重置版本或强行覆盖既有目标授权。

## 去注解版本的冻结续跑

受支持顺序是：先部署经批准的 bridge 版本，导出旧运行态并 capture，安全备份数据库及证据；由 #213 确认所有旧写进程已隔离，再部署允许的去注解版本，保留旧事实表，使用 `stage-frozen`；#212/#213 完成 DATA、运行态、平台及默认值证明后才能切换，最后执行破坏性数据库 contract。跳过 bridge 捕获不受支持。capture 会检查旧语义锚点，不能在最终代码上重猜旧权限码。#213 负责完整版本白名单和进程隔离证明；本工具不替代该门禁。

`stage-frozen` 的 `source-fingerprint` 必须是原报告的完整 64 位指纹。格式 `summer-legacy-source/1` / 映射格式 `summer-authorization-mapping/1`、原 bridge 来源配方、完整原始证据摘要、快照域和身份、投影摘要及确定性计划必须一致。旧授权、拒绝、绑定、成员、组织、数据、菜单等持久事实仍须未变；每一个目标资源身份（包括已提交步骤）须在当前完整 Domain 内仍有效。当前接口发现目录、旧发现历史追加和已安装代码变化不进入“当前旧事实”比较，但其原值完整保存在原始证据指纹内，不能重写。该入口不读取当前处理器上的旧注解或接受外部上传映射。

Java 内部窄端口为 `readFrozenInput(snapshotId)`、`stageFrozen(snapshotId, expectedSourceFingerprint, limit, actor)`。读取结果同时包含原始 source、plan、evidenceJson、sourceRevision 和当前 expectedRevision；DATA 续接须沿用该输入，并按既定域锁→快照锁顺序单独记录 DATA 检查点。旧格式历史仍可 report / readDataInput；不能改写历史补格式使其通过冻结续跑。

MANAGED、缺失/未知模式、revision 漂移、任一硬阻断都拒绝 stage-frozen。破坏性旧表 contract 后只允许 report / readFrozenInput 读取持久历史，不能继续 stage。两种 stage 均不会切换引擎或接受 DATA 转换，RUNTIME、平台及其他未证明项保持阻断。

## 结果与边界

- `BLOCKED`：存在无法证明等价的项目，禁止 stage。包括特殊公开规则、缺失/冲突目录、权限包历史、非 MVC 未映射资源、既有目标授权等。精确注册的实时消息读写会单列 `RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED`：旧历史令牌的 capability claim 上界尚未证明可以去除，不能合成授权或默认公开。`resourceCoverage` 如实列出目录源数、计划权限数、阻断数和映射比例。
- `PLANNED` / `STAGING`：计划已保存或部分步骤已提交。中断后用 report 回读已提交步骤，再继续 stage；未提交事务整批回滚。
- `DATA_PENDING`：接口、菜单、角色已展开，但旧用户的数据默认值、策略或拒绝含义仍待 #212 证明。不能把没有显式策略当作无限制。
- `STAGED`：本计划步骤完整且无本票阻断，仍不表示可以切换。报告始终输出 `dataConversionAccepted=false`、`cutoverAuthorized=false`。

同一个旧 code 的 INTERFACE 和 MENU 生成不同权限；资源关联来自当前精确 locator/handler 和旧发现历史。只有旧拦截器中精确 GET 导航入口的活跃身份规则可转换为 AUTHENTICATED，其他空码/公开规则继续阻断。组只分类，不参与动态授予。权限包按每个旧角色已持久化的有效能力集合匹配历史版本，不自动套用最新版本。

收窄角色依据每个主体的旧多角色并集、code/resource 别名、用户拒绝、菜单祖先门槛和数据限制签名生成；不能直接复制旧角色 ID，也不能删除拒绝再扩大绑定。旧拒绝原记录保留。平台旧权限依赖跨企业任职及其生命周期，目前明确阻断；平台来源盘点保留原 enterprise_id，不能把企业 platform 的角色改标为平台角色。

## 核对与交接

保留 capture/stage/report 文件及快照 ID。在既有权限、角色管理页检查具体资源关联和新角色，在迁移前取得的令牌上比较实际 API 与导航菜单允许集合，覆盖多角色、别名拒绝、旧 bundle 各版本、非活跃成员和匿名请求。最小管理入口也必须可用。数据库中检查每个已提交步骤对应唯一审计与 outbox，重跑不得重复创建身份、推进版本或追加事件。

真实 MySQL 中断/恢复、双节点旧令牌矩阵、原生浏览器以及完整回归分别留证。#213 必须重新检查所有 BLOCKED/DATA_PENDING 项、实时来源与覆盖率；本票工具不提供强制忽略或切换参数。验收夹具 `PermissionMigrationFixture.java` 只供独立 acceptance 源集使用，不属于正常生产构建。
