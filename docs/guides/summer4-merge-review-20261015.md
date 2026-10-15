# Summer4 提交审查与任务分支合并记录（2026-10-15）

## 范围与结论

用户确认以 `76dbed58481a4a7fb22d57ffc6c72da2e51d47e0`（T01–T36 开始前）为比较基准，将任务分支的遗漏提交合入 `summer4`。审查冻结 HEAD 为 `263f93e2a577cc38f4fbb24ff91eb3bdecce658d`，范围为 60 个提交、430 个文件。需求来源为 [技术方案 #108](https://github.com/abluepoint/summer/issues/108) 及关联任务，尤其 #123、#124、#144；标准来源为根 AGENTS、CONTEXT、docs/agents 与适用 ADR。

Standards 与 Spec 分别审查、分别记录，下面保留修复前问题和修复结论，不将 Git 合并等同于全部生产验收完成。

## Standards

1. **P1：数据范围读取的角色列表与持久格式不一致。** `DataScopeService.effective` 按换行拆分 `role_ids`，但角色绑定按 JSON 数组写入，不符合 #124 的多角色范围并集及持久投影约定。已支持 JSON 与旧换行格式；损坏 JSON、非字符串项拒绝，不使用部分有效前缀。回归覆盖多角色、禁用/异租户角色、显式 DENY、AND/OR 及租户上限。审查中曾怀疑资源 DENY 被忽略，复查表达式及测试后确认原实现已扣除，未据此改写表达式。
2. **P2：角色授权与菜单审计未持久化。** `AuthorizationService` 仅保存在进程 Map，`MenuManifestService` 入 outbox 前未写审计，不符合 ADR 0027 “Audit rows are written before the outbox event is enqueued”。已引入持久审计 Store 与 V31 迁移；变更审计和 outbox 同事务，授权决策独立事务保留，支持服务实例重建读取及回滚验证。

本轴 2 项，最严重 P1，均已修复；不将纯代码风格判断混作硬性违约。

## Spec

1. **P1：无角色用户可退回 scope 获取业务访问。** `AuthorizationService.decision` 对未绑定主体无条件 LEGACY_SCOPE，违反 #108 默认拒绝约束；拦截器还会在无企业上下文时跳过决策。已默认拒绝、校验成员和租户；仅对未托管本地管理员保留有限初始化入口。旧 scope 兼容显式开启且不适用于本地/托管用户。加入企业前的申请、受邀消费和自身会话例外限定真实控制器与精确路由，继续执行主体与资源归属检查。初始化链同步修复管理接口语义权限码、内置权限持久化和平台能力范围。加载已有数据库时，持久能力的 ID、状态与版本优先于同码内存种子，禁用状态不会因启动被恢复。
2. **P1：业务接口目录无法供前端配置角色。** 业务资源被标为不可配置，而角色页只展示可配置 BUSINESS 项，与 #123 的扫描后配置接口权限不符。已修正分类和可配置标记，保留协议、基础设施保护。
3. **P1：扫描模板与运行时权限码不一致。** 拦截器以实际 URI 生成权限码，目录则保存路由模板，路径参数使已配置授权/DENY 无法命中。现使用 Spring 匹配模板，支持 String/PathPattern、合并显式权限注解和相同规范化；缺失模板拒绝。
4. **P1：前端空权限及错误回退为管理员。** `main.tsx` 在可见菜单为空或加载失败时生成 admin 权限，且 manifest 发布失败阻止普通用户读取自己的授权。现独立尝试发布和读取，空结果/读取失败保持空权限，按可见项合并动作。角色与目录菜单对齐后端语义权限码，manifest 提升到版本 2，避免修改版本 1 元数据造成发布冲突。

本轴 4 项，最严重 P1，均已修复。

## 验证记录

- 后端：JDK 21，`./gradlew check --continue --no-configuration-cache --no-parallel --max-workers=2` 完整检查通过，JUnit 汇总 **525 tests / 0 failures / 0 errors / 0 skipped**，其中 Sample 为 403 项，九个含测试模块均有结果。**其中两项既有 BFF/strict 契约在开关关闭时提前返回，未执行实际链路；不能按 525 条已执行外部行为验收理解该数字。**
- 实际 MySQL HTTP：ConfigurationMySqlHttpTest 与 ConfigurationProjectionMySqlHttpTest 各 1 项，均真正执行并通过。两个随机独立 schema 各应用 32 条迁移到 V31，完成 HTTP 范围及投影断言，事后精确查询确认两个 schema 均已删除。
- 审计迁移：另一个独立 MySQL schema 完整应用 32 条迁移；V31 历史 ALLOW/DENY 回填、新审计写入和回滚、11 列实体契约、Flyway validate 与重复 migrate（0 条）通过，临时 schema 删除。未修改运行 schema。
- 前端：Node 22，`npm test`、`npm run typecheck`、`npm run build` 均退出 0；新增菜单 5 项错误/动作回归及 2 项初始化导航运行契约通过。

修复前红灯和修复后定向结果分别保留：权限/数据范围 23 项中 14 失败；审计 4 项均失败；同码种子升级 2 项均失败；bootstrap PKCE 1 项失败；伪菜单后缀与自身会话用例也取得预期红灯。最终完整检查包含全部新增回归，不以源码正则或零用例替代真实执行。

测试凭据仅从本地 Kubernetes Secret 读取到进程内存；Redis 同时提供测试专用与 Spring 标准环境变量，MySQL 仅用于独立测试 schema。健康检查保持启用，未打印或提交凭据。

此外修复完整回归中已失效的测试契约：Customer 命令反射签名、跨企业 fixture、测试 OAuth 客户端所需 scope、定时任务按幂等键统计执行；Modulith 补齐实际依赖声明，保持边界和循环检查，未关闭验证。

## 分支提交核对与合并方式

23 个原始任务提交在冻结 HEAD 中缺少原 SHA，但 20 个已有完全相同 patch 的提交；另 3 个挑选提交存在适配，逐项 range-diff 复核如下：

- `239e5d4` → `5abfbe7`：PKCE 测试使用已存在的 loginPassword fixture，导入位置适配。
- `ec816b7` → `bbbd9b7`：仅移除旧审查次数记录，功能变更保留。
- `d3c9d7f` → `eca7b73`：package.json 测试脚本上下文适配，删除功能实现保留。

未发现遗漏的功能 patch。因此先提交本轮审查修复，再用相同修复后 tree 合并 16 个最大任务分支 tip，补齐 Git ancestry；不重复套用旧 patch，避免覆盖后续修复。未修改来源工作树、任务分支或远端引用。用户原有 CustomerController 格式化修改、暂存区和诊断日志不纳入本次提交。

| 原始提交 | 已纳入提交 | 核对方式 |
| --- | --- | --- |
| `c698f2002767` | `3679854c3cba` | 相同 patch |
| `f06650830f06` | `e1c811922369` | 相同 patch |
| `99cbeddd9703` | `aab2e9fdb607` | 相同 patch |
| `62aca0cf2eae` | `23ecc2685851` | 相同 patch |
| `763d88706b8c` | `2976185af404` | 相同 patch |
| `d53c90b2c2b8` | `fce60d35ab2a` | 相同 patch |
| `1a6271969a4b` | `d5e04a1ba7c9` | 相同 patch |
| `ec816b7f7095` | `bbbd9b7c4a5b` | 适配复核 |
| `d3c9d7fb4de2` | `eca7b7315817` | 适配复核 |
| `948a203b8b30` | `417f9895840a` | 相同 patch |
| `3c68117bc88a` | `09d5d295b819` | 相同 patch |
| `c4dd595c9324` | `fc534179d92f` | 相同 patch |
| `4a256beceafb` | `e5f3a3c77108` | 相同 patch |
| `573cdb84dfdb` | `473b179291a1` | 相同 patch |
| `7b7fe1ad5560` | `9e2c736df831` | 相同 patch |
| `2ebd0ef16184` | `0d343d16ac3f` | 相同 patch |
| `c06598c656df` | `c88e54c7e29b` | 相同 patch |
| `07e29f16e5f1` | `04796be97f87` | 相同 patch |
| `eec8ae804ce5` | `d228bb2a532d` | 相同 patch |
| `bdbd94a1a210` | `d6fb651cdacc` | 相同 patch |
| `fe3eaf512d87` | `08b202d794a0` | 相同 patch |
| `239e5d4b6d6e` | `5abfbe73d059` | 适配复核 |
| `7cd4e5e28f88` | `505ec55ed5c4` | 相同 patch |

合并父 tip：

`7b7fe1ad5560a3b8a08f8c8cde11ae3d348bd91b`, `573cdb84dfdb3b4a3e68aabfeb2ed9c362d892e7`, `239e5d4b6d6e6279c27185ed1c1d8568cfab76da`, `4a256beceafbf9b6dcc2827b23a2ae080aaf5a6c`, `ec816b7f70950760650984be9ed55f004e06a2ed`, `2ebd0ef161843f605724a783a82bcce2f2308d01`, `1a6271969a4b4c4e0919262758a661bbfbdda495`, `d53c90b2c2b85e0d17471883c0271d05f9ad2be0`, `763d88706b8ccbf6fe3c2339312311bb52d4e6e5`, `fe3eaf512d870f15281d10193cb7e26a8f089b9f`, `99cbeddd9703504339ccee0152888d3a36b76f5e`, `c4dd595c9324b066c320df0f8c72b9f0c4bfdf8a`, `c698f20027679f768869ba60f1d3f6e3ff3263dd`, `3c68117bc88a149c8235fda6ce79f8ef43b8d72b`, `948a203b8b30171c76bbf236a48fd26d324b050b`, `d3c9d7fb4de2413db73ae864492ec0f49fd0a0a8`。

## 尚需独立提供的验收证据

本次不把所有 T01–T36 Issue 标记完成。首次完整回归中 ConfigurationMySqlHttpTest 与 ConfigurationProjectionMySqlHttpTest 因缺少独立 MySQL 环境变量跳过，最终执行结果见上方验证记录；IdentityChainE2EContractTest 原有 BFF 与 strict 连接服务契约在相应属性关闭时直接返回，JUnit 的 PASSED 不代表实际调用链已执行。本次未执行浏览器全部业务流程、真实 BFF/独立连接服务、集群故障切换或负载验收。前端菜单编辑页面的完整管理交互也应按技术方案单独验收。

机器证据保存在执行工作树 `.git/summer4-merge-review-20261015/`：固定基准与提交清单、patch 映射和 range-diff、修复前红灯、修复后 JUnit、完整检查、前端命令及 MySQL/Flyway 门禁结果。该目录含本地执行证据，不作为功能源码提交。
