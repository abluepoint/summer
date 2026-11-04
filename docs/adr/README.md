# 架构决策索引

本目录保存长期架构决策，术语只在 [CONTEXT](../../CONTEXT.md) 定义，实现状态只在 [现状与目标差异](../architecture/现状与目标差异.md) 汇总。决策成立与代码验收是两件事。

## 决策清单

| ADR | 结论 | 状态/解释 |
| --- | --- | --- |
| [0001](0001-use-mvc-as-canonical-capability-name.md) | 请求能力统一命名 Summer MVC | 有效；不保留 summer-web 别名 |
| [0002](0002-separate-capability-assembly-and-entry-points.md) | 能力、自动配置、Starter 分离 | 有效；Starter 不承载实现 |
| [0003](0003-keep-summer-common-narrow.md) | Common 只放稳定契约 | 有效；其中“上下文”由 0010 进一步收窄 |
| [0004](0004-include-an-optional-authorization-server.md) | 可选 Authorization Server | 有效；默认关闭、生产替换演示设施 |
| [0005](0005-keep-parameter-contracts-alongside-bean-validation.md) | Field/Style 与 Bean Validation 并存 | JSON 范围被 0013 部分替代；并存原则保留 |
| [0006](0006-use-sql-templates-for-complex-reads.md) | 复杂读取使用 SQL template | 有效；写模型使用 Repository |
| [0007](0007-keep-result-projection-inside-sql-templates.md) | 结果投影留在 SQL template 内 | 有效；不发展通用对象映射框架 |
| [0008](0008-use-a-default-unified-response.md) | 应用 JSON 默认统一响应 | 有效；协议/资源/RawResponse 旁路 |
| [0009](0009-reserve-the-summer-error-namespace.md) | 保留 SUMMER_ 错误命名空间 | 有效；业务使用自身前缀 |
| [0010](0010-use-framework-native-request-contexts.md) | 使用框架原生请求上下文 | 原生上下文原则保留，不恢复 SummerContext/通用 ThreadLocal；独立缓存开关由 0021 替代 |
| [0011](0011-do-not-model-a-maven-parent-in-gradle.md) | Gradle 不模拟 Maven parent | 有效；平台约束与模块依赖分开 |
| [0012](0012-keep-automator-outside-the-framework-repository.md) | Automator 在独立仓库演进 | 有效 |
| [0013](0013-expand-parameter-contracts-to-json-and-path.md) | Parameter contract 覆盖 PARAM/JSON/PATH | 输入范围、presence/nullability、白名单保留；严格校验选择启用的默认方式由 0017 替代，显式来源配置由 0018 替代，关闭缓存的专属启动检查由 0021 替代 |
| [0014](0014-use-official-arco-design-pro-as-summer-ui-baseline.md) | Summer UI 使用官方 Arco Design Pro | 已决定，#84 已建立基线，业务与认证仍待后续票 |
| [0015](0015-use-spring-security-public-client-pkce-for-summer-ui.md) | Summer UI 使用 public client + PKCE | 已决定，当前安全配置尚有差距 |
| [0016](0016-identify-customers-by-username.md) | Customer 改用不可变 username | 已决定，当前后端仍为 email 路径 |
| [0017](0017-simplify-configuration-with-explicit-field-contracts.md) | 每个 Field 契约明确完整，简化单项配置 | 默认 required=true、nullable=false；未知字段始终拒绝；Style 统一用 Java 公共配置；空集合与 minSize=0 由 0019 替代，框架预注册 Style 名称安排由 0022 替代，未完成迁移 |
| [0018](0018-infer-field-sources-automatically.md) | Field 来源统一自动推断 | 移除 Field/FieldList 的 source 属性及手动覆盖入口；同名多来源返回 400；PATH 仅支持单值，集合走 Query/表单/JSON；尚未迁移 |
| [0019](0019-reject-empty-field-lists.md) | FieldList 不允许空集合 | 部分替代 0017；minSize 默认 1，[] 返回 400，数量配置须满足 1 <= minSize <= 有效 maxSize；尚未实现 |
| [0020](0020-separate-http-request-logging-responsibilities.md) | MVC、HTTP 日志适配层与日志模块分工 | 默认记录请求摘要；正文经公共配置开启并统一脱敏、限长；请求包装器保存完整正文，已实现 |
| [0021](0021-manage-request-body-caching-automatically.md) | MVC 自动管理必要的请求体缓存 | 已移除 cache-request-body 及其专属启动检查，字段校验与 HTTP 日志适配共享完整 MVC 请求体；部分替代 0010、0013 |
| [0022](0022-define-styles-without-builtin-names.md) | Style 由应用定义，不设框架内置名称 | 框架提供规则构建 API，应用以 Java 公共配置定义具名 Bean；链式定义写法已确认，部分替代 0017，尚未实现 |
| [0023](0023-consolidate-identity-frontend-in-summer-ui.md) | 身份管理前端统一到 Summer UI | 替代 AICT 规格中的独立 `aict-identity` / Vben 前端选择；服务端安全边界另行明确 |
| [0026](0026-use-versioned-route-menu-manifest.md) | 前端路由发布版本化菜单 manifest | 服务端只保存元数据、Capability 绑定和租户覆盖，不保存前端组件引用 |
| [0027](0027-versioned-authorization-change-stream.md) | 权限变更使用 outbox 与 Redis Stream 版本化传播 | 节点独立消费组、共享 receipt 幂等、版本/重试/死信和持久化投影恢复 |
| [0028](0028-enforce-effective-configuration-scopes.md) | 持久化主体数据范围、公共配置授权和组织/租户覆盖优先级 | T18/T19；真实 MySQL 与 HTTP 已验证，浏览器流程单独验收 |
| [0029](0029-share-servlet-login-context-and-isolate-bearer-authentication.md) | 共享 Servlet 登录上下文并隔离 Bearer 认证 | Cookie CSRF、原子撤销、故障诊断与 JDBC 本地登录身份映射 |
| [0030](0030-deny-unassigned-users-and-align-configurable-resource-grants.md) | 未分配主体默认拒绝、管理权限码对齐与持久审计 | 初始化权限限定、前端空权限关闭、精确路由例外；补充 0026/0027/0029 |

| [0031](0031-resolve-configuration-tenants-and-version-overrides.md) | 显式配置租户、租户复合键与覆盖独立版本生命周期 | 部分替代 0028 的固定租户/无覆盖历史边界；浏览器与整应用双节点单独验收 |

| [0032](0032-persist-enterprise-members-and-organization-context.md) | 持久化企业成员、组织上下文与企业目录 | 角色绑定不代替成员资格；邀请接桥、目录/审核共用持久记录；保留原邀请码生命周期边界 |
| [0036](0036-keep-business-dictionaries-flat-for-cascading-selection.md) | 业务字典保持扁平，级联采用跨字典联动 | 已决定；字典组为同字典内可重叠的字典项子集，组内独立排序；页面配置上游值到下游组的对应关系，通用组件执行；整体版本见0037，尚未实现 |
| [0037](0037-publish-dictionary-items-and-groups-as-one-version.md) | 字典项和全部字典组共用字典发布版本 | 补充字典快照ADR0027；草稿隔离，整体发布及回滚，分组无独立版本；详情随当前生效版本，新增分组及通用组件尚未实现 |
| [0038](0038-evolve-existing-dictionaries-through-effective-snapshots.md) | 演进既有业务字典并统一生效读取边界 | 部分替代ADR0027的无快照编辑行读取例外；保留旧公开选项和组织覆盖，写入接入修订保护，迁移及验收见业务字典技术规格，尚未实现 |

## 来源与适用范围

2026-09-22 整理时，本工作树只有 ADR 0001–0013。通过 `git worktree list` 找到 `core` 工作树中的未提交 ADR 0014–0016，并与 2026-09-08 的 [Issue #69](https://github.com/abluepoint/summer/issues/69) 核对；本次将三份 ADR 原文同步到 `summer4`，同时补入其新增领域词汇。未修改来源工作树，也未把未提交文件描述为远端提交。

AICT 的 IAM、BFF 与独立连接服务仍有各自的服务端职责，但前端不再使用独立 `aict-identity` 或 Vben 工程，见 ADR 0023。AICT 规格中的业务要求不因前端合并而自动取消；其中旧前端技术选型须按 ADR 0023 重新对齐。现有 Summer UI 的 Arco/PKCE 决策也不自动证明 AICT 的 BFF 安全契约已实现。

历史日志只用于定位线索，Issue 关闭状态不能替代代码及测试证据。后续决策冲突必须新增 ADR 并写明替代关系，不删除或重写旧决定来掩盖变更。
