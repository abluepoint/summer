# 旧 DATA 策略等价迁移

本流程续接 V160 的同一 `snapshotId`。升级人员先用身份迁移工具捕获并完成 INTERFACE / MENU / 用户步骤，再处理 DATA。`readFrozenInput` 是可信冻结输入；旧 `readDataInput` 仅作历史读取。首次安装、域登记、生产切换和 contract 由集成升级流程负责，本工具始终不修改 `engine_mode`。

## 证明与支持边界

冻结证据中的资源上限、全部 ROLE 范围、全部 USER 行级拒绝与组织状态逐行盘点。旧表达式直接调用保留的 `DataScopeExpression` 编译器；对已关联主体计算 `upper ∩ union(role) ∩ not union(user deny)`，保留 USER 表达式内部的 DENY。全局存在 ROLE binding 时，无匹配角色的主体范围为空；全局不存在 ROLE binding 时不能臆造这个默认拒绝。

证明枚举表达式常量、当前主体、一个不同常量及 NULL 形成的相等关系类别，覆盖未来合法行，而不是只比较当前几行。只有新预设并集与旧集合完全相同才生成权限；集合相减、交集无法表达时保留具体反例。表达式类别超过上限时阻断，不采样放行。匿名、无绑定和未来主体另按 NULL、策略字面值主体和不同主体类别证明默认范围为空。保留旧角色绑定的停用主体，以及引用停用角色的活动主体不是无绑定主体；可能复活 DATA 的持久绑定明确生成 `PERSISTED_ROLE_REACTIVATION_UNPROVED` 阻断。

自动子集包含可证明的 TENANT_ALL、按实际 `owner_id` 的 SELF、活动指定部门精确集合及必要并集。旧 SELF / CREATOR 使用 `created_by`，不能改写成 owner。依赖单一组织上下文或动态子树的旧策略不能按新模型所有任职部门近似转换，当前生成来源明确的阻断。指定失效部门不能通过删除旧 ALLOW 假装成功。

字典和参数的旧策略没有 action 字段，但实际消费者决定操作含义：配置 read / create / update 分别证明；字典草稿删除未发布条目还映射 delete。创建路径在业务构造器中将 creator / owner 固定为操作者，不能用历史行的任意 owner 组合替代创建输入；字典 draft / publish / disable / rollback 的额外 actor 投影写门禁还必须证明冗余。字典 delete 保留实际条目范围、父 update 及完整维护门禁。参数 delete 与配置 export 没有旧消费者，不能复制 read 权限制造它们。applications 没有旧 DATA 消费者；新 DATA 入口要求 MANAGED，无旧映射；发现该资源旧策略时阻断。资源注册和业务安全上限始终保留。

每个主体的完整 INTERFACE / MENU / DATA 向量决定目标角色，只在完整向量相同时共享角色。原始 sourceRoleIds 只用于解释与追踪，不把同名旧角色广播给其他主体。PLATFORM 生命周期与不识别的资源、策略、绑定类型均不自动接受。

## 操作入口

显式配置 `summer.sample.authorization.data-migration.command` 后启动应用执行一次命令，正常启动不执行迁移。所有配置在同一 `summer.sample.authorization.data-migration.` 前缀下；敏感数据库与登录配置仍由受控环境注入，不写进命令参数或报告。

| command | 必填配置 | 结果 |
|---|---|---|
| dry-run | snapshot-id, report-file | 只读可信快照，列出矩阵、权限、完整角色向量与阻断 |
| prepare | snapshot-id, source-fingerprint, expected-revision, actor, report-file | 写入独立证明及外键步骤；保留原三份 JSON |
| stage | 同 prepare，加 plan-hash，可选 batch-size | 域锁→原快照锁下按检查点应用 DATA；每批可续跑 |
| verify | 同 prepare，加 plan-hash、runtime-evidence-file | 验证可信操作者导入的真实运行比较回执 |
| report | snapshot-id, report-file | 历史读取，旧表 contract 后仍可追踪 |

`report-file` 必须是新文件。prepare / stage / verify 只接受现存 LEGACY 域、准确 frozen source fingerprint 与当前 expectedRevision；写入时复核旧持久事实、注册语义及所有原资源 incarnation。冲突后先读报告和当前事实，不改原快照或覆盖旧证明重试。stage 必须在 V160 身份步骤全部完成后执行；批大小为 1–10000。事实、审计、outbox、revision、应用检查点在同一事务提交，回滚不留下半条授权。重放已应用步骤仍核对目标 DATA 详情、角色向量与用户赋予事实。

## 真实运行比较回执

`summer-data-http-comparison/1` 回执绑定 sourceFingerprint、planHash、实际运行输入摘要 runtimeInputsFingerprint 及逐 cell 比较。每个 PROVED 主体（含 DEFAULT）/资源/action 必须覆盖 read 的 list、total、detail，或 create / update / 字典 delete 的 write；导出等无旧消费者项不冒充升级比较。真实 runner 应另保存请求路径、实际状态、分页总数、候选和允许 ID 集合、双节点、旧令牌与源码摘要，摘要必须来自这些实际运行输入。回执不是应用自行执行 HTTP 的证明，也不能由合成单元测试回执冒充验收。

只接受 2xx 成功或 403 / 404 受控拒绝，成功与拒绝不能混配；拒绝不带 ALLOW ID。401、重定向、格式错误、业务冲突、5xx 等都是无结论的凭据、夹具或依赖失败，不可按空集合接收。旧新集合必须精确相等，缺 cell、未知操作、未证明 cell 或多余/丢失 ID 均拒绝；保留原始状态码，不要求 403 与 404 文本一致。

全部步骤完成只得到 APPLIED。只有阻断清零、完整回执通过才得到 VERIFIED / dataConversionAccepted。该标记仅接受 DATA 转换，`cutoverAuthorized` 始终 false；它不授权生产模式切换、关闭旧核对通道或删除旧表。阻断快照应保留用于说明，修正源策略后重新捕获新快照，不改写旧证据。

## 导出验收的关联边界

旧字典和参数没有导出消费者，迁移清单将 export 标为 NOT_APPLICABLE，并保留冻结旧 Controller 来源及实际安装路由的证据；不自动制造导出权限。applications 的 DATA 入口要求 MANAGED，没有旧 DATA 映射，其旧策略或绑定仍阻断转换。

AC 中的导出范围关联已接受的 [#207 验收](https://github.com/abluepoint/summer/issues/207)，提交 `4fc74f956f071dc19fd0c3332a22a3bdc5e29266`。其 attempt03 / run `c27ef7c3a4` 在两节点验证 read ALL 与 export SELF 独立，以及撤销 export 后 read 保留，共 8 条相关检查通过；该整批原结果为 184 项、1 失败，业务 phase01 / phase02 与浏览器子阶段已接受，不能把整批改写为全绿。对应 808 个输入路径快照 SHA256 为 `c083f58780003f38bff3f3b812e982a7552b3a40b2e126f536389b3d81f74db4`。这是前票关联证据，不是 #212 新执行的旧新导出比较；最终集成任务必须针对最终源码补统一导出回归。

## 账本与恢复

V170 的 `summer_authorization_data_migration` 和 `summer_authorization_data_migration_step` 均外键指向原 V160 快照。原 snapshot_json / plan_json / evidence_json、原步骤类型与映射历史保持；新增 DATA_POLICY / DATA_VECTOR / USER_DATA / ROLE_DATA 映射继续使用同一映射表。DATA proof JSON 和 planHash 不可覆盖。

报告不读取实时旧表，因此 contract 后仍能说明来源、向量、指定部门、阻断与运行回执。写入与核验则必须在旧模型保留期间完成；无法核对旧事实时关闭写入口，不降级为历史 JSON 可读即放行。生产 contract 前还必须完成集成任务的安装、恢复、fleet fence 与最终运行态验收。
