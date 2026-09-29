# summer4 迁移计划

本轮完成分支与架构基线整理。下述代码迁移尚未实施；业务功能任务仍以 [Issue #69](https://github.com/abluepoint/summer/issues/69) 的 T01–T38、AC01–AC63 为完整规格。后续配置简化与请求日志访谈新增的决定以 ADR 0017–0022 及其替代关系为准，目前尚未同步到该 Issue，不另建一份竞争的任务清单。

## 迁移原则

先归拢职责，再变更行为。保留既有工作区与暂存内容；每个阶段必须能够独立审查和验证。不通过删除测试、放宽权限、Mock 后端或改写 ADR 让阶段“完成”。

AICT 产品迁出必须先落实目标仓库/应用和完整接收副本，核对源码、资源、配置和测试后再从 Sample 移除。当前未指定迁出目标，不能将“归属已明确”当作删除这些代码的授权。也不创建 legacy 目录来假装完成产品拆分。

## 分阶段实施

| 阶段 | 变更 seam 与内容 | 前置条件 | 完成证据 |
| --- | --- | --- | --- |
| 0 架构基线（本轮） | summer4；ADR 0014–0016；术语；总览、模块、安全、差异文档 | 当前源码与最新决定核对 | ADR 原文一致，既有改动保留；依赖检查与 4 个架构测试通过 |
| 1 应用归属与构建门禁 | 按模块清单迁出 IAM/BFF/连接执行/审计/release；携带产品测试与资源；补未登记项目检查 | 产品接收位置与保留清单落实 | 框架无产品路径/类型耦合；产品迁入侧原行为测试通过；Sample 架构通过 |
| 2 通用安全链恢复 | Resource Server Bearer/stateless；产品 session/CSRF 放回应用；移除框架中的产品端点例外与任意 success handler 选择 | 产品认证 seam 已隔离，失败行为测试先到位 | Cookie 不能替代 API Bearer；协议登录仍恢复 saved request；401/403/CSRF 正确 |
| 3 Customer username | 实体、Repository、Service、Field/Style、查询/投影、路由、种子、测试一次协调迁移；对应 T06–T12 | 参考后端边界清楚 | PostgreSQL 唯一/非空、旧 email 路径 400、PATCH omission/null、204 删除与 username 复用 |
| 4 Public PKCE 与 OIDC | public/confidential 配置兼容、TTL/claims、csrf/login/session/logout；对应 T13–T18 | 阶段 2 | 真实 authorization code + S256；无 secret/refresh token；code 5m、token 15m；confidential 回归 |
| 5 官方 UI 基线 | 独立 summer-ui；固定官方 Arco SHA、原 lockfile/包管理器、路由、locale、Mock；对应 T01–T05 | 实施时核验官方稳定基线 | 原始 lint/typecheck/build 可重复执行；上游路由清单；Summer 请求不被 Mock |
| 6 UI 业务与部署 | oidc-client-ts、内存 auth、请求错误适配、CustomerForm、三种交互、同源代理；对应 T19–T33 | 阶段 3/4/5 契约稳定 | 客户端契约测试、表单/路由/权限行为、独立构建、深链及 GET/POST login 分流 |
| 7 完整验收 | 后端、前端、真实 PostgreSQL/PKCE/Playwright、视觉与敏感信息检查；对应 T34–T38 | 前述阶段达到各自门禁 | 各门禁分别记录通过/失败/跳过；满足 AC01–AC63 后才可声称功能交付 |

阶段 3、4、5 可以在接口约定稳定后独立推进；UI 联调需等待三者。阶段 1 不能通过把 AICT 模型挪到 Common/Security 达成。

## 关键兼容性与验收

- 请求日志按 [ADR 0020](../adr/0020-separate-http-request-logging-responsibilities.md) 分离 MVC 请求数据管理、HTTP 日志快照生成与日志输出职责，范围止于请求摘要与按需请求正文。后续验证默认输出 requestId、请求方法、路径、响应状态和耗时且不记录正文；公共配置开启正文后统一脱敏、限长，无需逐接口声明。验证日志和字段校验共享读取能力、业务请求体保持完整、日志快照处理不影响业务输入；具体日志配置细节与缓存容量策略留待实现设计。
- 按 [ADR 0021](../adr/0021-manage-request-body-caching-automatically.md) 已移除 `summer.mvc.cache-request-body` 属性、条件装配、专属 `JsonFieldCachingValidator` 及旧配置示例，由 MVC 自动管理必要缓存。#73 已验证 JSON Field/FieldList 校验后业务仍可读取完整正文，日志消费者与字段校验共用读取能力，无 Field 的接口也无须另开缓存；默认关闭正文日志的策略不变。业务请求体大小与日志预览长度分别由各自边界负责。
- 后续 Field 配置简化按 [ADR 0017](../adr/0017-simplify-configuration-with-explicit-field-contracts.md)、[ADR 0018](../adr/0018-infer-field-sources-automatically.md)、[ADR 0019](../adr/0019-reject-empty-field-lists.md)、[ADR 0022](../adr/0022-define-styles-without-builtin-names.md) 及其替代关系整理实施规格后再迁移。需审查默认必填变化，验证实际来源推断与冲突、缺失与 null，以及 Query/表单/JSON 的空字符串和全空白字符串均进入 Style 校验；可选邮箱传空字符串不能被当作省略。Java 公共 Style 定义与字段引用保持统一，链式定义写法已确认，尚未实现。
- 按 ADR 0022 取消框架内置具名 Style 自动注册及 Field.style 的 emptyStyle 默认回退。框架提供类型、格式与约束构建能力，应用用 @Configuration/@Bean 显式定义包括基础类型在内的所需 Style，名称默认取 Bean 方法名；迁移旧引用与 Sample 公共定义，验证同一 Bean 可被多个字段复用、构建规则生效，未定义的名称按既有启动检查规则报错。
- 删除 Field/FieldList 的 source 属性及使用方赋值，同步迁移运行时读取、严格未知字段检查、请求体缓存联动与相关启动检查；不保留手动覆盖入口。验证同一普通字段可在不同请求从受支持的不同来源读取，同名多来源仍返回 400，不能因删除配置而回退为旧 PARAM 默认行为。
- 移除 Item 注解，将 FieldList.items 从 Item[] 改为 Field[] 并迁移全部使用方；集合子字段沿用 required=true、nullable=false、显式 Style 及 name 缺省使用 param 的约定。审查旧可选子字段并显式标记，保留必要导出别名；验证子字段按元素相对路径读取，逐项校验、未知子字段拒绝及启动期 Style 检查覆盖集合内部。集合数量约束仍由 FieldList 承担。
- Style 引用检查须在启动期执行且无须额外开关：合法引用可正常启动，不存在的引用须在处理请求前使启动失败，并标明接口方法、字段及 Style 名；同时覆盖可选字段，不能依赖请求触发检查。
- 集合长度由实际数据推断：保持 JSON 数组长度读取，新增 Query/表单索引识别，迁移 countParam 注解配置和调用方专门数量字段；验证无需数量字段也能完整识别各元素及校验各子字段，旧数量字段不绕过未知字段检查。Query/表单索引须从 0 开始连续，覆盖 0/1 合法、0/2 缺口及仅从 1 开始返回 400；同一索引的多个子字段应归入同一元素，参数传输顺序不影响识别。
- PATH 仅保留单值 Field：迁移旧 FieldList(source=PATH) 用法与测试，集合改为 Query/表单或 JSON；验证普通路径变量仍可读取校验，路径变量不能被解析为集合或集合数量。
- 集合缺失、null 与空数组分别验收：Query/表单无集合参数表示该来源未提供，JSON `{"items":[]}` 一律返回 400，不转换成缺失或 null。没有 Query/表单空集合标记，也不以数量 0 表达空集合。集合默认 required=true、nullable=false，覆盖 required/nullable 四种组合对缺失和显式 null 的处理；即使可选或可 null，[] 仍拒绝。Query 缺失不得使 JSON 中的 [] 跳过检查。
- 集合级业务约束直接配置在 FieldList，例如 minSize/maxSize；子字段继续显式引用 Style。按 ADR 0019 将 minSize 默认值设为 1，验证 [] 拒绝、一项通过默认数量下限、显式更大下限生效；required 仍独立处理缺失。maxSize 显式配置优先，未声明才使用公共默认值；以公共默认 100 验证未声明上限 100、局部 20 上限 20、局部 500 上限 500，覆盖 JSON 与 Query/表单。
- 数量配置在启动时检查 1 <= minSize <= 有效 maxSize：覆盖 minSize=0、maxSize=0、minSize=5/maxSize=3 以及 minSize 超过继承公共上限时启动失败；局部覆盖后区间合法时可启动。错误须指出接口方法、集合字段及配置值，可选集合也须检查。
- 使用 Field 契约的接口始终拒绝未知请求字段：分别验证 Query 和 JSON 的未知字段返回 400 并指出字段，已声明字段正常校验；无需逐接口开启严格模式，也不提供全局或接口级宽松开关。该变化替代 ADR 0013 的选择启用方式，须迁移旧开关用法并审查原先依赖忽略多余字段的调用方。技术信息经 Header 传递，分页排序等业务字段逐项声明并复用 Style；验证未声明的 requestId、分页或排序字段不会通过自动放行名单绕过检查，协议端点保持自身处理规则。
- Customer 的公开标识切换是破坏变更：不保留 email fallback、by-email 或 redirect。username 不是登录用户名。持久库迁移由真实应用提供；Sample 维持 create-drop。
- 删除 Sample 的 confidential client 实例不等于删除框架 confidential capability。Public none 禁 secret；已有显式 secret 配置维持兼容。
- 认证恢复只恢复页面和非敏感草稿，绝不自动重放创建/修改/删除。
- 业务响应覆盖 success/error/204，协议响应保持原生；需从浏览器和后端同时验证。
- 后端 `check`、制品构建、前端 lint/typecheck/unit/build、Mock-off 真实 Playwright、视觉/焦点/存储审计相互独立。零测试或 skipped 不能写成通过。
- 拓扑验收同时核对公开 issuer、callback、logout、Cookie、GET/POST `/login`、SPA fallback；仅返回 HTTP 200 不足以完成验收。

## 回退与交付记录

后续每阶段形成可独立回退的提交，但提交/推送/发布按当次授权执行。AICT 移除前保留可核对的接收副本；Customer 与安全变化按契约整体回退，不能只退一侧。create-drop 回退仅适用于可丢弃 Sample 数据；持久库使用应用自有迁移方案。

每阶段交付需记录实际变更、保留的用户改动、来源版本、执行命令、tests/failures/errors/skipped、已知限制。阶段 0 的证据见 [现状与目标差异](../architecture/现状与目标差异.md)，不能复用它宣称后续业务阶段完成。
