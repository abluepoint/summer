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
| [0020](0020-separate-http-request-logging-responsibilities.md) | MVC、HTTP 日志适配层与日志模块分工 | 默认记录请求摘要；正文经公共配置开启并统一脱敏、限长；职责与范围保持收敛，缓存策略由 0021 补充，尚未实现 |
| [0021](0021-manage-request-body-caching-automatically.md) | MVC 自动管理必要的请求体缓存 | 已移除 cache-request-body 及其专属启动检查，字段校验与 HTTP 日志适配共享完整 MVC 请求体；部分替代 0010、0013 |
| [0022](0022-define-styles-without-builtin-names.md) | Style 由应用定义，不设框架内置名称 | 框架提供规则构建 API，应用以 Java 公共配置定义具名 Bean；链式定义写法已确认，部分替代 0017，尚未实现 |

## 来源与适用范围

2026-09-22 整理时，本工作树只有 ADR 0001–0013。通过 `git worktree list` 找到 `core` 工作树中的未提交 ADR 0014–0016，并与 2026-09-08 的 [Issue #69](https://github.com/abluepoint/summer/issues/69) 核对；本次将三份 ADR 原文同步到 `summer4`，同时补入其新增领域词汇。未修改来源工作树，也未把未提交文件描述为远端提交。

AICT 的 Vben、同源 BFF、Product Session、IAM 与独立连接服务属于另一产品范围。核验来源是本地工作区尚未提交的 `docs/specs/aict-identity-console-connection-service-complete-spec-v2.md` 与 `docs/specs/aict-unified-identity-technical-design.md`，不包含在本次架构文档提交中。它们不替代 Summer UI 的 Arco/PKCE 决策；Summer UI 决策也不自动取消 AICT 产品需求。

历史日志只用于定位线索，Issue 关闭状态不能替代代码及测试证据。后续决策冲突必须新增 ADR 并写明替代关系，不删除或重写旧决定来掩盖变更。
