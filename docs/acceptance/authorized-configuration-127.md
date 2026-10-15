# T19 参数与字典的授权读取（#127）

[任务 #127](https://github.com/abluepoint/summer/issues/127) 对应故事 5、6、7、8、29、33。前置 #118、#119、#126 已回读为 CLOSED。2026-10-16（Asia/Shanghai）完成本票增量验收，交主会话复核、提交和关票；本会话未操作 Issue、暂存或提交。

结构化结果见 [JSON 收据](authorized-configuration-127-result.json)。本轮只新增验收脚本与报告，无生产 Java、JUnit、页面、迁移或构建修改。固定运行候选 SHA-256：`9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af`。

## 设计与实现

[ADR0028](../adr/0028-enforce-effective-configuration-scopes.md) 定义独立动作授权、持久显式拒绝、数据范围及组织覆盖优先级；[ADR0031](../adr/0031-resolve-configuration-tenants-and-version-overrides.md) 替代其固定 ent-acme 和无独立覆盖历史的旧边界。

配置动作由 `ConfigurationActionAccess` 与接口权限共同检查；参数、字典管理及公开读取使用同一持久 DataScope。`ConfigurationOverrideService` 按组织、租户、基础值选择，已发布的高优先级覆盖若私有、停用或不可见，返回 NOT_FOUND，不读取低优先级值。覆盖草稿不改变运行时值；发布、停用、回滚分别授权并验证聚合版本，保留不可变修订与审计。

## 实际结果

计数顺序为 **tests / failures / errors / skipped**。这里的 tests 是命名断言，包含夹具设置；不是 JUnit 数量。各批单列，不累计重复设置或把历史失败改成成功。

| 批次 | 计数 | 结论 |
| --- | --- | --- |
| attempt-01 | 76 / 0 / 1 / 0 | 十二类管理动作持久拒绝、两类公开读拒绝和单项消费拒绝均通过；随后脚本字段名错误中止，整批仍有 error |
| attempt-02 | 156 / 0 / 0 / 0 | 覆盖生命周期、独立动作拒绝、组织优先级、隐藏列表/详情/历史/单项、非法输入、陈旧版本、真实审计故障与恢复 |
| browser-attempt-01 配套 HTTP | 36 / 0 / 0 / 0 | 真实身份和字典夹具、故障回滚核对、持久读写拒绝 |
| browser-attempt-01 Chrome | 7 / 0 / 0 / 0 | 实际登录、发布覆盖、非法 JSON、500 保留草稿及 requestId、同页恢复、403 写拒绝及读拒绝清空状态 |
| tenant-attempt-01 | 38 / 1 / 1 / 0 | 夹具字典项遗漏 sortOrder，正确返回 400 |
| tenant-attempt-02 | 65 / 1 / 1 / 0 | 新租户夹具误用 ent-acme 专属字典读能力 ID，正确返回 403 |
| tenant-attempt-03-diagnostic | 65 / 1 / 1 / 0 | 安全诊断确认新租户没有对应能力声明，持久授权原因为 DEFAULT_DENY |
| tenant-attempt-04 | 117 / 1 / 1 / 0 | 租户隔离已通过；后续参数 PATCH 夹具漏传当前版本，正确返回 409 |
| tenant-attempt-05 | 149 / 0 / 0 / 0 | 修正能力码、版本及 publicValue 字段后，任意租户与剩余可见性边界全部通过 |

最终三个成功运行器及 Chrome 均退出 0。八个运行批次均核验 schema、临时数据库账号、独立 Redis 容器、自有 JVM/Chrome/Vite 和端口清理完成。Chrome pageErrors=0，正常关闭，forced=false。

## 七项 AC

| AC | 实际证据 | 结果 |
| --- | --- | --- |
| 1 管理端接口权限与数据范围 | 参数/字典 read、create、update、publish、disable、rollback 分别施加持久显式拒绝，已有令牌随后立即被拒；管理列表、参数详情、版本历史及字典项按范围过滤；覆盖初建、修改、发布、停用、回滚分别拒绝并保持原版本 | PASS |
| 2 公共 API 只返回公开且有权数据 | 私有基值不能被公开覆盖绕过；隐藏参数/字典及历史返回 404，隐藏字典项从快照列表剔除且单项消费 404；已登录显式读拒绝为 403；匿名需显式租户，TENANT 可读、OWNER 不可读 | PASS |
| 3 覆盖优先级与审计 | 实测组织 > 租户 > 基值；私有/范围隐藏组织覆盖、停用组织与停用租户覆盖均不回退；草稿、发布、停用、回滚历史和审计带主体/requestId；两个新建真实租户的相同 key/code、覆盖值、修订及审计分别隔离 | PASS |
| 4 前端授权与服务器一致 | Chrome 实际提交覆盖成功；持久 publish 拒绝显示 403 和 requestId；持久 read 拒绝清空选项、版本和未提交 JSON。此前 T10/T11 草稿、历史和错误反馈按匹配输入复用 | PASS |
| 5 迁移、真实 HTTP/UI 与异常路径 | 固定生产 JAR、真实 MySQL/独立 Redis/JVM、真实 PKCE、HTTP 与 Chrome；检查 V29/V30/V32/V33 成功应用；包含 400/403/404/409、真实审计触发器 500 及同 JVM/同页恢复 | PASS |
| 6 契约、关联与敏感边界 | Unified success/error、SUMMER_ 错误码、响应头/信封/请求的 requestId 一致；敏感测试值仅内存比较，不写响应快照或日志；各批敏感输出检测零命中 | PASS |
| 7 追踪、计数与用户改动 | 原票、ADR、既有实现、分批执行脚本/结果、原失败、安全诊断、截图、输入摘要与清理回执齐备；未修改生产代码、用户 CustomerController 或共享暂存内容 | PASS |

## 复用与失败解释

114 个相关配置、DataScope 和迁移输入与固定候选一致，执行前后无漂移。T07 的 5 个计数生产输入、T17 的 16 个生产输入，以及 T10/T11 的 41 个生产文件和 5 个对应页面文件逐项匹配。参数 Controller 整文件因 T07 抽取列表查询解析而不同；详情、写入、发布/回滚、覆盖与相关响应辅助方法所在连续区段逐字节匹配。没有将该文件误标为整文件相同。

据此复用 [T07](query-115.md)、[T10](system-parameter-release-118.md)、[T11](dictionary-release-119.md)、[T17](data-scopes.md) 与 [T18](composed-data-scopes-126.md) 中已经通过且输入匹配的基础流程。本次未重复运行其完整矩阵，也未运行新的 JUnit、类型检查或构建。Chrome 本次 242 个 UI 输入无漂移；相关页面与旧验收匹配，其他票的后续修改不属于此声明。

本轮失败均为验收脚本或夹具问题，没有据此修改产品权限：覆盖响应字段应为 revision；字典项必须提供 sortOrder；新租户应通过正式角色 API 授予规范能力码，不能借用其他租户专属 capability ID；参数修改须提供当前 version，并使用 publicValue 字段。曾把字典 403 口头误称匿名请求，回读检查顺序后已更正为已登录主体缺少能力。原失败目录及执行脚本保持不变。

四张完整截图已逐张目视：覆盖发布、故障保留草稿、写拒绝、读拒绝。故障截图显示关联 ID 和待重试 JSON；读拒绝截图显示空选择及 `[]`。截图中短暂的加载态/通知叠层不作为额外产品缺陷。审计触发器仅证明覆盖行状态和 CONFIGURATION_OVERRIDE 审计条数回滚，不扩称已核对所有 outbox 或业务表。

## 复现与证据

脚本目录：`scripts/acceptance/authorized-configuration/`。要求 JDK 21、Node 22、Chrome、Docker，以及既有 docker-desktop/local-base MySQL 访问。每次选择新的证据目录，并先确认 21081、21082、21083、21573、21580 空闲。凭据仅使用内存、环境或标准输入；数据库业务变更均通过实际 HTTP，SQL 仅用于隔离资源、只读核验和本轮故障触发器。

```sh
SUMMER_AUTHORIZED_CONFIGURATION_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t19-live/new-protocol-run" \
SUMMER_AUTHORIZED_CONFIGURATION_CANDIDATE="$PWD/.git/summer4-closeout-20261015/t18-live/frozen-build-01/candidate.jar" \
SUMMER_AUTHORIZED_CONFIGURATION_CANDIDATE_SHA256=9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af \
python3 scripts/acceptance/authorized-configuration/run.py
```

任意租户入口为 `tenant_run.py`，Chrome 入口为 `browser_probe.py`；两者使用相同三个环境变量，但必须分别换用全新证据目录。`SUMMER_AUTHORIZED_CONFIGURATION_SKIP_ACTIONS=1` 可跳过已保存的独立动作拒绝矩阵；本轮 attempt-02 使用此开关，原 attempt-01 的十五个相关拒绝断言仍为实际证据。

私有执行证据位于 `.git/summer4-closeout-20261015/t19-live/`，每批保存 executed-scripts、result、http-observations、candidate-provenance 和 cleanup。`candidate-source-match.json`、`reused-*-inputs.json`、`controller-release-section-match.json` 提供复用依据；`FILES.json` 和 `READY.json` 供主会话交接复核。

本票不修改 T20 的菜单刷新/授权事件共享边界、T33 的审计 trace 字段、参数 query/count 算法、DataScope 基础语义、密钥或 Task/SSE。共享工作区与暂存区的其他变动全部保留。
