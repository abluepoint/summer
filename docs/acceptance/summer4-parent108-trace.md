# Summer 4 父方案108需求到验收追踪

本追踪基线覆盖父方案的52个用户故事和36张工作票，提供需求、设计、实现、测试、缺陷及验收证据之间的双向索引。候选03受影响原生补验已通过，候选02未受影响批按字节一致性明确复用。技术证据与Issue状态分开：144/108关闭、最终提交与回读仍由root执行；不以本文件宣称CMMI认证。

日期为2026年10月16日，时区Asia/Shanghai。需求和七项AC取自root保存的Issue快照；提交祖先与评论引用只是配置管理线索，验收结论以各票的实际候选、运行批次和限制为准。配套[结构化追踪JSON](summer4-parent108-trace.json)保存每票七项原AC、源码摘要和分批结果字段。

关联公开报告：[最终门禁与执行/缓存边界](summer4-final-gates-144-v2.md)；[历史21AC逐项复核](summer4-historical-ac-109-117-133.md)。

## 技术证据状态与root收口事项

| 编号 | 范围 | 状态和下一步 |
| --- | --- | --- |
| G1 | T36 #144 故事49–52 | TECHNICAL_EVIDENCE_PASSED：候选02scope93、迁移54、cache故障76与综合前213通过；候选03受影响补批85/0/0/0、Chrome5/0/0/0验证会话Redis503及恢复。原失败保留，未受影响02证据按字节码/资源/依赖一致复用。 |
| G2 | #142整合 | CLOSED_COMPLETED：root已推送6c024b5270437ce811cdc9ac36bb1e8f7d953be0，评论6200616278关闭；7/7。原117/0/2/0与补42/0/0/0分列。 |
| G3 | 最终集成门禁 | FINAL_BACKEND06_PASSED：root最终backend06为775/0/0/0、181suites、730输入稳定；644本轮执行/131缓存，基线767无缺失、新增8均Session新类。历史05为767=652执行/115缓存，前端02/03复用见公开v2，不跨批加总。 |
| G4 | #109 #117 #133历史AC同步 | PUBLIC_REVIEW_COMPLETE_CHECKBOX_SYNC_PENDING：公开historical-ac-109-117-133报告已逐项映射21AC及边界，root负责同步复选框。旧525/47/7不是本次重跑；缓存原生故障与当前T36结果分列。 |
| G5 | 父#108最终状态 | ROOT_CLOSEOUT_PENDING：本地技术证据已闭合；root完成最终集成审阅/提交后逐票回读36票/252AC并实际关闭144及108，禁止预造关闭时刻。 |

## 验收证据使用规则

- tests/failures/errors/skipped依各原始批次，HTTP命名断言、Chrome、JUnit、组件和清理探针分层，不跨候选/重复夹具累加。
- 原失败、超时、零执行、强制回收、历史与当前范围保持；报告中READY是当时交接状态，不自动等于最新Issue状态。
- 本追踪基线只整理已有证据和路径，未重跑产品、未更改Java/UI/Gradle、未提交或修改Issue。
- 本地源码/报告链接已验证存在；GitHub评论及提交来自root缓存台账，不以本轮网络访问成功作背书。

版本基线：最终候选03为`e9d1ff6ef33b74c1c8ffef9bc6417b06554d92490f8b1e814c5dec49d3d2f28e`，受影响补批85/0/0/0；旧候选02为`8a2ec5987b60d7356cf2207b964a0e878d8434f903e59e887500a866ad09fb7e`。543个构建输入与源码一致，40份迁移资源含MySQL39份，历史31份MySQL资源字节未变；制品核验与实际升级分列：migration-native-attempt-02已真实完成31→39，54/0/0/0，旧源库仍31。root原校验器误将总资源数要求为39，其失败记录和更正均保留。

## 五十二个用户故事的票据映射

| 故事 | 原始需求 | 实现与验收票据 | 当前判断 |
| --- | --- | --- | --- |
| 1 | 作为平台管理员，我希望使用 Summer UI 登录管理端，以便通过现有 OIDC Authorization Code + PKCE 流程安全访问控制台。 | [#109](https://github.com/abluepoint/summer/issues/109)、[#132](https://github.com/abluepoint/summer/issues/132)、[#133](https://github.com/abluepoint/summer/issues/133) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 2 | 作为平台管理员，我希望查看当前 MySQL、Redis、数据库迁移和密钥模块健康状态，以便及时发现运行基础设施问题。 | [#110](https://github.com/abluepoint/summer/issues/110)、[#111](https://github.com/abluepoint/summer/issues/111)、[#141](https://github.com/abluepoint/summer/issues/141) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 3 | 作为部署管理员，我希望通过环境变量或受控 Secret 注入数据库、Redis 和主密钥配置，以便仓库和日志中不出现敏感值。 | [#111](https://github.com/abluepoint/summer/issues/111)、[#129](https://github.com/abluepoint/summer/issues/129)、[#130](https://github.com/abluepoint/summer/issues/130) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 4 | 作为部署管理员，我希望在本地使用 127.0.0.1:3306 和 127.0.0.1:6379 连接 Kubernetes 基础设施，以便开发环境与实际运行依赖一致。 | [#110](https://github.com/abluepoint/summer/issues/110)、[#111](https://github.com/abluepoint/summer/issues/111) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 5 | 作为平台管理员，我希望创建、修改、发布、停用和回滚系统参数，以便无需重新构建应用即可调整受控运行参数。 | [#113](https://github.com/abluepoint/summer/issues/113)、[#118](https://github.com/abluepoint/summer/issues/118)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 6 | 作为平台管理员，我希望为系统参数定义类型、默认值、环境范围、是否公开和生效版本，以便防止错误类型或越权读取。 | [#113](https://github.com/abluepoint/summer/issues/113)、[#118](https://github.com/abluepoint/summer/issues/118)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 7 | 作为平台管理员，我希望管理字典类型和字典项，以便前端表单、筛选器和业务接口使用统一枚举来源。 | [#114](https://github.com/abluepoint/summer/issues/114)、[#119](https://github.com/abluepoint/summer/issues/119)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 8 | 作为前端开发者，我希望按字典编码获取公开字典，以便页面不再维护重复的静态枚举。 | [#114](https://github.com/abluepoint/summer/issues/114)、[#119](https://github.com/abluepoint/summer/issues/119)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 9 | 作为管理员，我希望字典更新后缓存自动失效并通知前端，以便页面在不重启服务的情况下取得新值。 | [#114](https://github.com/abluepoint/summer/issues/114)、[#116](https://github.com/abluepoint/summer/issues/116)、[#117](https://github.com/abluepoint/summer/issues/117)、[#119](https://github.com/abluepoint/summer/issues/119)、[#139](https://github.com/abluepoint/summer/issues/139) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 10 | 作为框架维护者，我希望统一参数契约与 Jakarta Bean Validation，以便查询、表单、JSON body 和路径变量具有一致的校验语义。 | [#113](https://github.com/abluepoint/summer/issues/113)、[#115](https://github.com/abluepoint/summer/issues/115) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 11 | 作为 API 使用者，我希望所有业务错误返回统一错误信封、稳定错误码和 requestId，以便定位问题并正确处理用户提示。 | [#112](https://github.com/abluepoint/summer/issues/112)、[#113](https://github.com/abluepoint/summer/issues/113)、[#114](https://github.com/abluepoint/summer/issues/114)、[#141](https://github.com/abluepoint/summer/issues/141) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 12 | 作为审计人员，我希望查询包含操作者、认证主体、租户、组织、动作、资源、结果和 requestId 的操作流水，以便重建关键操作过程。 | [#112](https://github.com/abluepoint/summer/issues/112)、[#113](https://github.com/abluepoint/summer/issues/113) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 13 | 作为安全管理员，我希望审计记录不包含密码、Token、私钥和不必要的敏感正文，以便日志系统不会成为凭据泄露渠道。 | [#112](https://github.com/abluepoint/summer/issues/112) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 14 | 作为框架维护者，我希望使用 Caffeine L1 和 Redis L2 缓存系统参数、字典、权限和策略，以便降低数据库压力并支持多节点部署。 | [#111](https://github.com/abluepoint/summer/issues/111)、[#115](https://github.com/abluepoint/summer/issues/115)、[#117](https://github.com/abluepoint/summer/issues/117) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 15 | 作为框架维护者，我希望权限、参数和字典变更通过 outbox 与 Redis Stream 广播缓存失效，以便不同节点最终收敛到同一状态。 | [#116](https://github.com/abluepoint/summer/issues/116)、[#117](https://github.com/abluepoint/summer/issues/117)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 16 | 作为身份管理员，我希望创建、停用、恢复和查询用户，以便维护认证主体的生命周期。 | [#120](https://github.com/abluepoint/summer/issues/120) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 17 | 作为身份管理员，我希望创建角色并绑定接口权限、菜单 Capability 和数据策略，以便按职责分配访问能力。 | [#123](https://github.com/abluepoint/summer/issues/123)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 18 | 作为身份管理员，我希望为用户直接绑定角色或显式拒绝权限，以便处理个别例外并保留可解释的授权结果。 | [#123](https://github.com/abluepoint/summer/issues/123)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 19 | 作为安全管理员，我希望系统自动扫描全部 Spring MVC 接口，以便接口资源不会因手工登记遗漏。 | [#122](https://github.com/abluepoint/summer/issues/122) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 20 | 作为安全管理员，我希望接口资源记录 HTTP 方法、规范化路径、处理器、模块、资源类型、敏感等级和默认权限码，以便形成完整权限目录。 | [#122](https://github.com/abluepoint/summer/issues/122) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 21 | 作为开发者，我希望特殊接口能够通过注解覆盖自动权限码，以便处理业务语义要求更强的权限名称。 | [#122](https://github.com/abluepoint/summer/issues/122) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 22 | 作为安全管理员，我希望业务接口在未配置权限时默认拒绝，以便新接口不会意外暴露。 | [#122](https://github.com/abluepoint/summer/issues/122)、[#123](https://github.com/abluepoint/summer/issues/123) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 23 | 作为安全管理员，我希望 OAuth、Actuator、健康检查和基础设施接口也被扫描并分类，以便目录完整，同时可以通过显式配置决定是否公开。 | [#122](https://github.com/abluepoint/summer/issues/122)、[#123](https://github.com/abluepoint/summer/issues/123) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 24 | 作为前端开发者，我希望从路由定义生成菜单 manifest，以便前端组件、路由和菜单保持单一来源。 | [#124](https://github.com/abluepoint/summer/issues/124) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 25 | 作为前端开发者，我希望 manifest 只包含菜单 ID、父级 ID、路径、标题键、图标键、排序和 Capability Code，以便后端不依赖前端组件实现。 | [#124](https://github.com/abluepoint/summer/issues/124) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 26 | 作为管理员，我希望配置菜单的启用状态、排序、父子关系和 Capability 绑定，以便不同角色看到不同工作台结构。 | [#124](https://github.com/abluepoint/summer/issues/124) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 27 | 作为前端用户，我希望前端根据后端返回的动作级 Capability Code 控制菜单、按钮和路由可见性，以便界面与授权结果一致。 | [#124](https://github.com/abluepoint/summer/issues/124)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 28 | 作为安全管理员，我希望后端始终独立校验 API 权限，以便前端隐藏不能替代服务端安全边界。 | [#123](https://github.com/abluepoint/summer/issues/123)、[#124](https://github.com/abluepoint/summer/issues/124)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 29 | 作为租户管理员，我希望按租户、组织、部门和组织树配置数据范围，以便同一平台上的数据相互隔离。 | [#115](https://github.com/abluepoint/summer/issues/115)、[#121](https://github.com/abluepoint/summer/issues/121)、[#125](https://github.com/abluepoint/summer/issues/125)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 30 | 作为部门管理员，我希望角色只能访问本人、创建人、负责人或指定组织范围的数据，以便落实最小权限。 | [#121](https://github.com/abluepoint/summer/issues/121)、[#125](https://github.com/abluepoint/summer/issues/125)、[#126](https://github.com/abluepoint/summer/issues/126) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 31 | 作为安全管理员，我希望使用结构化条件树表达 AND/OR 数据策略，以便禁止前端注入原始 SQL。 | [#115](https://github.com/abluepoint/summer/issues/115)、[#125](https://github.com/abluepoint/summer/issues/125)、[#126](https://github.com/abluepoint/summer/issues/126) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 32 | 作为框架维护者，我希望数据范围在 Repository 或查询构造层注入，以便所有入口都遵守同一数据权限边界。 | [#115](https://github.com/abluepoint/summer/issues/115)、[#125](https://github.com/abluepoint/summer/issues/125)、[#126](https://github.com/abluepoint/summer/issues/126)、[#128](https://github.com/abluepoint/summer/issues/128) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 33 | 作为安全管理员，我希望敏感资源未配置数据范围时默认拒绝，以便避免因配置遗漏导致数据越权。 | [#125](https://github.com/abluepoint/summer/issues/125)、[#126](https://github.com/abluepoint/summer/issues/126)、[#127](https://github.com/abluepoint/summer/issues/127) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 34 | 作为认证管理员，我希望 OAuth 客户端、授权同意、会话索引和 JWK 状态持久化，以便服务重启和多节点运行不丢失关键状态。 | [#109](https://github.com/abluepoint/summer/issues/109)、[#120](https://github.com/abluepoint/summer/issues/120)、[#121](https://github.com/abluepoint/summer/issues/121)、[#129](https://github.com/abluepoint/summer/issues/129)、[#132](https://github.com/abluepoint/summer/issues/132)、[#133](https://github.com/abluepoint/summer/issues/133) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 35 | 作为密钥管理员，我希望本地密钥模块支持环境变量、Kubernetes Secret、文件引用和数据库密文四种来源，以便适配不同部署安全级别。 | [#109](https://github.com/abluepoint/summer/issues/109)、[#129](https://github.com/abluepoint/summer/issues/129)、[#130](https://github.com/abluepoint/summer/issues/130)、[#131](https://github.com/abluepoint/summer/issues/131)、[#136](https://github.com/abluepoint/summer/issues/136) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 36 | 作为密钥管理员，我希望真实私钥以密文形式保存到 MySQL，并由主密钥解密使用，以便满足本地实现要求而不暴露明文。 | [#129](https://github.com/abluepoint/summer/issues/129) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 37 | 作为密钥管理员，我希望按 kid 管理密钥版本并支持新旧 key 并存，以便轮换期间已有令牌仍可验证。 | [#109](https://github.com/abluepoint/summer/issues/109)、[#129](https://github.com/abluepoint/summer/issues/129)、[#131](https://github.com/abluepoint/summer/issues/131)、[#136](https://github.com/abluepoint/summer/issues/136) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 38 | 作为任务管理员，我希望创建、启停、立即执行、重试和查看执行记录，以便管理分布式定时任务。 | [#134](https://github.com/abluepoint/summer/issues/134)、[#135](https://github.com/abluepoint/summer/issues/135)、[#136](https://github.com/abluepoint/summer/issues/136) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 39 | 作为任务管理员，我希望任务在多个节点中只被协调执行一次，以便避免重复处理。 | [#134](https://github.com/abluepoint/summer/issues/134)、[#135](https://github.com/abluepoint/summer/issues/135) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 40 | 作为系统用户，我希望通过 WebSocket 接收双向消息，以便页面能够进行实时交互。 | [#138](https://github.com/abluepoint/summer/issues/138) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 41 | 作为系统用户，我希望通过 SSE 查看任务进度、日志流和单向通知，以便无需轮询即可看到状态变化。 | [#137](https://github.com/abluepoint/summer/issues/137)、[#139](https://github.com/abluepoint/summer/issues/139) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 42 | 作为系统用户，我希望 WebSocket 和 SSE 支持认证、租户隔离、心跳、重连和断点恢复，以便网络波动不会造成不可解释的数据丢失。 | [#133](https://github.com/abluepoint/summer/issues/133)、[#137](https://github.com/abluepoint/summer/issues/137)、[#138](https://github.com/abluepoint/summer/issues/138)、[#139](https://github.com/abluepoint/summer/issues/139) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 43 | 作为框架维护者，我希望对外部依赖配置超时、重试、限流、熔断和隔离，以便局部故障不会拖垮应用。 | [#140](https://github.com/abluepoint/summer/issues/140) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 44 | 作为安全设计者，我希望写操作失败关闭、读操作仅在明确安全时降级，以便降级不会伪造业务成功。 | [#140](https://github.com/abluepoint/summer/issues/140) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 45 | 作为运维人员，我希望通过 Actuator、Prometheus 和 OpenTelemetry 查看延迟、错误率、认证失败、缓存命中率、任务状态和实时连接数，以便及时定位故障。 | [#141](https://github.com/abluepoint/summer/issues/141) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 46 | 作为产品管理员，我希望查看用户、角色、接口权限、字典、任务和审计的基础统计，以便了解系统使用和风险趋势。 | [#142](https://github.com/abluepoint/summer/issues/142) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 47 | 作为插件开发者，我希望通过受信任的插件注册接口声明能力、配置、菜单和权限，以便扩展不破坏核心边界。 | [#143](https://github.com/abluepoint/summer/issues/143) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 48 | 作为安全管理员，我希望插件不能绕过统一认证、审计和数据权限，以便扩展能力仍遵守安全控制。 | [#143](https://github.com/abluepoint/summer/issues/143) | 追踪覆盖已建立；实际验收按各关联票及其候选/批次判断 |
| 49 | 作为发布管理员，我希望每次数据库结构变化都有可追踪的 Flyway 迁移，以便升级过程可回放、可核对。 | [#110](https://github.com/abluepoint/summer/issues/110)、[#116](https://github.com/abluepoint/summer/issues/116)、[#118](https://github.com/abluepoint/summer/issues/118)、[#119](https://github.com/abluepoint/summer/issues/119)、[#144](https://github.com/abluepoint/summer/issues/144) | T36本地技术证据闭合；父票最终状态待root实际回读 |
| 50 | 作为发布管理员，我希望升级遵循 expand/contract、备份、健康检查和兼容窗口，以便降低版本升级风险。 | [#110](https://github.com/abluepoint/summer/issues/110)、[#118](https://github.com/abluepoint/summer/issues/118)、[#119](https://github.com/abluepoint/summer/issues/119)、[#143](https://github.com/abluepoint/summer/issues/143)、[#144](https://github.com/abluepoint/summer/issues/144) | T36本地技术证据闭合；父票最终状态待root实际回读 |
| 51 | 作为架构师，我希望模块先在单体内保持稳定契约，未来再按独立伸缩、发布、数据 ownership 或故障隔离需求拆分微服务，以便避免过早分布式化。 | [#144](https://github.com/abluepoint/summer/issues/144) | T36本地技术证据闭合；父票最终状态待root实际回读 |
| 52 | 作为质量负责人，我希望每个里程碑都有需求、设计、实现、验证、缺陷和度量记录，以便满足 CMMI 过程可追踪性。 | [#141](https://github.com/abluepoint/summer/issues/141)、[#142](https://github.com/abluepoint/summer/issues/142)、[#144](https://github.com/abluepoint/summer/issues/144) | T36本地技术证据闭合；父票最终状态待root实际回读 |

## 三十六张工作票的完整追踪

以下实现链接是当前工作区的具体入口，测试链接是可运行测试的定位；执行事实来自独立验收报告及其候选。通用契约见[CONTEXT.md](../../CONTEXT.md)、[Unified response决策](../adr/0008-use-a-default-unified-response.md)、[错误码决策](../adr/0009-reserve-the-summer-error-namespace.md)和[请求上下文决策](../adr/0010-use-framework-native-request-contexts.md)。

### T01 认证存储扩展点预重构

**需求**：[#109](https://github.com/abluepoint/summer/issues/109)；故事1、34、35、37。建立可替换的认证、OAuth 客户端、授权同意、会话和密钥存储契约，同时保持当前 Summer UI 的登录、PKCE、退出和测试行为不变。

**设计**：身份、客户端、授权、同意、会话和密钥经可替换存储接口装配，保持PKCE与协议响应边界。 依据：[0004-include-an-optional-authorization-server.md](../adr/0004-include-an-optional-authorization-server.md)、[0015-use-spring-security-public-client-pkce-for-summer-ui.md](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md)、[0029-share-servlet-login-context-and-isolate-bearer-authentication.md](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。

**实现**：[SummerIdentityStore.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/storage/SummerIdentityStore.java)、[SummerOAuthClientStore.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/storage/SummerOAuthClientStore.java)、[SummerAuthorizationStore.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/storage/SummerAuthorizationStore.java)、[SummerSessionStore.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/storage/SummerSessionStore.java)、[SummerKeyStore.java](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/storage/SummerKeyStore.java)。

**测试**：历史完成评论与存储/PKCE合同测试关联；旧525项属历史综合批，专属只读复核由root接收，未在本次重跑。 测试入口：[SecurityStorageContractTest.java](../../summer-security-autoconfigure/src/test/java/com/abluepoint/summer/security/autoconfigure/SecurityStorageContractTest.java)、[PublicClientPkceIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/PublicClientPkceIntegrationTest.java)。

**缺陷与修正**：存储替换不得改变外部登录契约；本追踪基线不把后续会话/JDBC问题倒算为T01原批覆盖。

**验收来源**：[summer4-t25-shared-session-acceptance.md](../guides/summer4-t25-shared-session-acceptance.md)、[summer4-historical-ac-109-117-133.md](summer4-historical-ac-109-117-133.md)。

**关闭或补救评论与提交**：[评论6185696796](https://github.com/abluepoint/summer/issues/109#issuecomment-6185696796)（[ef96e13b](https://github.com/abluepoint/summer/commit/ef96e13bf879b4aa5a3bbaf2fc8f98d67c5d75b3)）

**状态与批次局限**：历史CLOSED；公开21AC逐项复核已完成，复选框同步待root。原历史21AC已由root独立逐项核对并输出公开报告，复选框同步仍由root负责；本次T36原生结果与历史计数分列。

### T02 Customer 在 MySQL 中持久化

**需求**：[#110](https://github.com/abluepoint/summer/issues/110)；故事2、4、49、50。将 Summer Sample 的 Customer 真 API 从 H2/create-drop 迁移到本地 MySQL，建立 Flyway 基线并保留 Customer username、email、displayName、active 和 customerId 的既有领域语义。

**设计**：MySQL权威持久化和Flyway管理Customer，运行时外置配置；诊断页面显示真实迁移状态。 依据：[0006-use-sql-templates-for-complex-reads.md](../adr/0006-use-sql-templates-for-complex-reads.md)、[0016-identify-customers-by-username.md](../adr/0016-identify-customers-by-username.md)。

**实现**：[CustomerAuditService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerAuditService.java)、[RuntimeDiagnosticsService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RuntimeDiagnosticsService.java)、[SampleFlywayConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/SampleFlywayConfiguration.java)、[CustomerScreen.tsx](../../summer-ui/src/pages/customer/CustomerScreen.tsx)。

**测试**：2859候选：scope12、Customer检查20、真实浏览器3段、受限账号2、缺配置1；各层分列。 测试入口：[CustomerMySqlFlywayIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/customer/CustomerMySqlFlywayIntegrationTest.java)、[RuntimeMigrationDiagnosticsHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/RuntimeMigrationDiagnosticsHttpTest.java)。

**缺陷与修正**：旧runtime菜单权限和迁移状态缺口→专用诊断修复→scope真实回放12；foundation旧未完成段保留，最终结果见综合JSON和关闭评论。

**验收来源**：[foundation.md](foundation.md)、[summer4-closeout-20261015.json](summer4-closeout-20261015.json)。

**关闭或补救评论与提交**：[评论6189360475](https://github.com/abluepoint/summer/issues/110#issuecomment-6189360475)（[045a22a7](https://github.com/abluepoint/summer/commit/045a22a77189d1b129e28f6270db8c01a15862ed)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。浏览器流程段数不是JUnit数量；随机隔离库不代表生产部署。

### T03 Redis 配置与运行诊断

**需求**：[#111](https://github.com/abluepoint/summer/issues/111)；故事2、3、4、14。接入 127.0.0.1:6379 的 Redis，并为 Summer UI 提供脱敏健康状态、连接失败原因和运行指标。

**设计**：Redis诊断复用受保护会话，凭据仅运行时注入，固定状态与脱敏指标反馈。 依据：[0024-session-protected-redis-diagnostics.md](../adr/0024-session-protected-redis-diagnostics.md)。

**实现**：[RedisDiagnosticService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RedisDiagnosticService.java)、[RedisDiagnosticsController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RedisDiagnosticsController.java)、[index.tsx](../../summer-ui/src/pages/diagnostics/redis/index.tsx)。

**测试**：2859候选真实UP、认证失败、不可用、超时及恢复5态；健康脱敏5次，真实拒绝403。 测试入口：[RedisDiagnosticServiceTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/RedisDiagnosticServiceTest.java)、[RedisDiagnosticsControllerTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/RedisDiagnosticsControllerTest.java)。

**缺陷与修正**：菜单能力错位及未续取Bearer→路由/诊断凭据修正→真实页面故障与恢复。

**验收来源**：[foundation.md](foundation.md)、[summer4-closeout-20261015.json](summer4-closeout-20261015.json)。

**关闭或补救评论与提交**：[评论6189362439](https://github.com/abluepoint/summer/issues/111#issuecomment-6189362439)（[045a22a7](https://github.com/abluepoint/summer/commit/045a22a77189d1b129e28f6270db8c01a15862ed)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。故障作用于独立Redis；不改变共享实例或将健康200当全部业务通过。

### T04 操作流水持久化与查询

**需求**：[#112](https://github.com/abluepoint/summer/issues/112)；故事11、12、13。以 Customer 的真实写入操作为首个场景，把操作流水追加写入 MySQL，并在 Summer UI 中按主体、资源、动作、结果、时间和 requestId 查询。

**设计**：操作流水追加写入，主体、有效租户组织及requestId关联；查询独立授权。 依据：[0010-use-framework-native-request-contexts.md](../adr/0010-use-framework-native-request-contexts.md)、[0020-separate-http-request-logging-responsibilities.md](../adr/0020-separate-http-request-logging-responsibilities.md)。

**实现**：[CustomerAuditService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerAuditService.java)、[index.tsx](../../summer-ui/src/pages/audit/index.tsx)。

**测试**：2859候选上下文7、审计8、真实变更27、浏览器4段；相关HTTP JUnit6单列。 测试入口：[CustomerAuditLedgerHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/customer/CustomerAuditLedgerHttpTest.java)、[CustomerAuditMySqlIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/customer/CustomerAuditMySqlIntegrationTest.java)。

**缺陷与修正**：有效组织丢失及不支持写方法500→实时上下文与405/Allow修正→真实上下文回放。

**验收来源**：[foundation.md](foundation.md)、[summer4-closeout-20261015.json](summer4-closeout-20261015.json)。

**关闭或补救评论与提交**：[评论6189364905](https://github.com/abluepoint/summer/issues/112#issuecomment-6189364905)（[045a22a7](https://github.com/abluepoint/summer/commit/045a22a77189d1b129e28f6270db8c01a15862ed)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。应用追加/API只读不等于防DBA修改；最终全量中测试修复由root独立记录。

### T05 类型化系统参数管理

**需求**：[#113](https://github.com/abluepoint/summer/issues/113)；故事5、6、10、11、12。提供系统参数的真实后端和 Summer UI 管理闭环，支持类型化值、版本、状态、公开标记、并发冲突、统一校验、错误返回和审计。

**设计**：参数类型、版本、公开标记和范围经Parameter contract校验，错误字段和关联ID显示在编辑处。 依据：[0005-keep-parameter-contracts-alongside-bean-validation.md](../adr/0005-keep-parameter-contracts-alongside-bean-validation.md)、[0013-expand-parameter-contracts-to-json-and-path.md](../adr/0013-expand-parameter-contracts-to-json-and-path.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[SystemParameterController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterController.java)、[SystemParameterService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterService.java)、[index.tsx](../../summer-ui/src/pages/system-parameters/index.tsx)。

**测试**：旧9976候选API120、修复后Chrome15；组件2；135是去重分批命名检查而非JUnit。 测试入口：[SystemParameterHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterHttpTest.java)。

**缺陷与修正**：服务端violations到页面丢失、抽屉遮挡错误→API保留字段信息并在抽屉展示requestId→真实Chrome15。

**验收来源**：[system-parameters-113.md](system-parameters-113.md)、[system-parameters-113-result.json](system-parameters-113-result.json)。

**关闭或补救评论与提交**：[评论6190065288](https://github.com/abluepoint/summer/issues/113#issuecomment-6190065288)（[06cd7dc8](https://github.com/abluepoint/summer/commit/06cd7dc89ff0b13ebe389f9d149f13e6bdb2d5f5)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。补验46含重复夹具，不与135累加；后续参数页改动由T07/T10另验。

### T06 字典管理与表单消费

**需求**：[#114](https://github.com/abluepoint/summer/issues/114)；故事7、8、9、11。提供字典类型、字典项和公开读取 API，并让一个真实 Summer UI 表单从后端加载字典选项。

**设计**：字典管理与发布消费分离，单项读取复用有效公开快照及租户数据权限。 依据：[0027-versioned-dictionary-public-snapshots.md](../adr/0027-versioned-dictionary-public-snapshots.md)。

**实现**：[DictionaryController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryController.java)、[DictionaryService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryService.java)、[index.tsx](../../summer-ui/src/pages/dictionary/index.tsx)。

**测试**：9976基础API140和Chrome17；442e AC4补充HTTP84和Chrome8；JUnit11，组件3。 测试入口：[DictionarySelectedItemHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/dictionary/DictionarySelectedItemHttpTest.java)、[DictionaryMySqlIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/dictionary/DictionaryMySqlIntegrationTest.java)。

**缺陷与修正**：仅列表过滤不能满足停用单项错误契约→新增单项读取与表单校验；有效实际92/23/0/0→新候选92/0/0/0。

**验收来源**：[dictionary-114.md](dictionary-114.md)、[dictionary-114-result.json](dictionary-114-result.json)。

**关闭或补救评论与提交**：[评论6190699779](https://github.com/abluepoint/summer/issues/114#issuecomment-6190699779)（[c88eb236](https://github.com/abluepoint/summer/commit/c88eb236da12e4c95a634ce0483c308b29d87012)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。基础157与补充92含重复设置，不累加；完整版本历史/实时通知分别属于T11/T31。

### T07 增强查询与参数筛选

**需求**：[#115](https://github.com/abluepoint/summer/issues/115)；故事10、14、29、31、32。在 Summer Data JPA 查询边界提供可组合过滤、排序、分页、投影和租户条件能力，并用于系统参数查询。

**设计**：Field白名单查询经命名SQL投影和统一DataScope，独立count复用同一解析条件。 依据：[0006-use-sql-templates-for-complex-reads.md](../adr/0006-use-sql-templates-for-complex-reads.md)、[0007-keep-result-projection-inside-sql-templates.md](../adr/0007-keep-result-projection-inside-sql-templates.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[SystemParameterQueryService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterQueryService.java)、[SystemParameterCountController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterCountController.java)、[index.tsx](../../summer-ui/src/pages/system-parameters/index.tsx)。

**测试**：442e API375、Chrome19；993独立count294/0/0/0；count及列表JUnit7。 测试入口：[SystemParameterQueryHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterQueryHttpTest.java)、[SystemParameterCountHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterCountHttpTest.java)。

**缺陷与修正**：摘要省略字段显示undefined→页面占位修复；列表totalElements不足以替代独立count→新增正式count路由并真实补验。

**验收来源**：[query-115.md](query-115.md)、[query-115-result.json](query-115-result.json)。

**关闭或补救评论与提交**：[评论6194324720](https://github.com/abluepoint/summer/issues/115#issuecomment-6194324720)（[fc74f566](https://github.com/abluepoint/summer/commit/fc74f566ed2a062696c0319ad6fbb86eb2111e79)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。旧整批375/0/1/0的浏览器错误保留；各候选/夹具/组件不得加成一次全量。

### T08 参数变更的可靠事件投递

**需求**：[#116](https://github.com/abluepoint/summer/issues/116)；故事9、15、49。把系统参数变更与 MySQL outbox 放入同一事务，并通过 Redis Stream 投递可重试、可观测的参数变更事件。

**设计**：MySQL事务outbox与Redis Stream至少一次投递；每节点独立消费组和幂等回执。 依据：[0025-versioned-two-level-parameter-dictionary-cache.md](../adr/0025-versioned-two-level-parameter-dictionary-cache.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[SystemParameterEventStreamListener.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterEventStreamListener.java)、[SystemParameterEventService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterEventService.java)。

**测试**：旧完整117/6/0/0含Chrome17绿；同脚本恢复旧61/6→新b96d61/0；最终JUnit12。 测试入口：[SystemParameterEventStreamRecoveryIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterEventStreamRecoveryIntegrationTest.java)、[SystemParameterEventConsumerTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterEventConsumerTest.java)。

**缺陷与修正**：只读新消息、迟建组和死信重处理→pending/接管/历史起点/死信终态修复→真实恢复与重启。

**验收来源**：[system-parameter-events-116.md](system-parameter-events-116.md)、[system-parameter-events-116-result.json](system-parameter-events-116-result.json)。

**关闭或补救评论与提交**：[评论6195683599](https://github.com/abluepoint/summer/issues/116#issuecomment-6195683599)（[562510bd](https://github.com/abluepoint/summer/commit/562510bdb42239d247f8b8933d91e440183a5fac)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。XCLAIM IDLE是时间夹具；只重放保留事件，非exactly-once；首条空流丢失未复现。

### T09 参数与字典二级缓存

**需求**：[#117](https://github.com/abluepoint/summer/issues/117)；故事9、14、15。为参数和字典建立 Caffeine L1 + Redis L2 缓存，结合版本 key、TTL 和事件失效支持多节点读取。

**设计**：Caffeine L1与Redis L2使用租户和资源版本键，Redis版本不可读时绕过旧缓存回源。 依据：[0025-versioned-two-level-parameter-dictionary-cache.md](../adr/0025-versioned-two-level-parameter-dictionary-cache.md)。

**实现**：[VersionedTwoLevelCache.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCache.java)、[VersionedTwoLevelCacheConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCacheConfiguration.java)。

**测试**：历史完成评论及真实Redis双节点/TTL/版本失效测试；后续T08与T20分别提供恢复补验。 测试入口：[VersionedTwoLevelCacheTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCacheTest.java)、[VersionedTwoLevelCacheRedisIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/cache/VersionedTwoLevelCacheRedisIntegrationTest.java)。

**缺陷与修正**：节点本地清理不得被共享receipt抑制；原缓存版本/回源约束与后续消费恢复缺陷分列。

**验收来源**：[summer4-cache-117.md](../guides/summer4-cache-117.md)、[summer4-historical-ac-109-117-133.md](summer4-historical-ac-109-117-133.md)。

**关闭或补救评论与提交**：[评论6185697881](https://github.com/abluepoint/summer/issues/117#issuecomment-6185697881)（[ef96e13b](https://github.com/abluepoint/summer/commit/ef96e13bf879b4aa5a3bbaf2fc8f98d67c5d75b3)）

**状态与批次局限**：历史CLOSED；公开21AC逐项复核已完成，复选框同步待root。原历史21AC已由root独立逐项核对并输出公开报告，复选框同步仍由root负责；本次T36原生结果与历史计数分列。

### T10 系统参数发布与回滚

**需求**：[#118](https://github.com/abluepoint/summer/issues/118)；故事5、6、49、50。为系统参数增加草稿、发布、停用、回滚和生效版本，使管理员可追踪变更并安全恢复。

**设计**：参数草稿与运行发布版本分离，发布/回滚事务写审计和事件，历史按服务端版本刷新。 依据：[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[SystemParameterReleaseService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterReleaseService.java)、[SystemParameterController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterController.java)、[index.tsx](../../summer-ui/src/pages/system-parameters/index.tsx)。

**测试**：442e真实HTTP18、Chrome12、组件9；此前688中的发布JUnit2仅历史关联。 测试入口：[SystemParameterReleaseHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/systemparameter/SystemParameterReleaseHttpTest.java)。

**缺陷与修正**：停用后历史旧版本导致直接回滚409→启停成功重读当前历史并保持请求代次→真实无需手动刷新回滚。

**验收来源**：[system-parameter-release-118.md](system-parameter-release-118.md)、[system-parameter-release-118-result.json](system-parameter-release-118-result.json)。

**关闭或补救评论与提交**：[评论6193848344](https://github.com/abluepoint/summer/issues/118#issuecomment-6193848344)（[9c278d60](https://github.com/abluepoint/summer/commit/9c278d605c7c6de246a84f62dcf6e655affd712c)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。不将T07独立count或其他菜单修复计入本票；批次各自报告。

### T11 字典版本发布与更新

**需求**：[#119](https://github.com/abluepoint/summer/issues/119)；故事7、8、9、49、50。为字典增加版本发布、停用和回滚，并让 Summer UI 主动重新读取时取得最新已发布版本。

**设计**：不可变字典发布快照隔离草稿，生命周期审计/outbox和缓存版本一致。 依据：[0027-versioned-dictionary-public-snapshots.md](../adr/0027-versioned-dictionary-public-snapshots.md)。

**实现**：[DictionaryVersionService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryVersionService.java)、[DictionaryController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryController.java)、[index.tsx](../../summer-ui/src/pages/dictionary/index.tsx)。

**测试**：442e真实HTTP18、Chrome12；组件5与独立写拒绝1；历史JUnit3属于旧688批。 测试入口：[DictionaryVersionHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/dictionary/DictionaryVersionHttpTest.java)。

**缺陷与修正**：停用404清空未保存覆盖JSON→仅保留404时的编辑输入，401/403仍清空→实际恢复与拒绝回放。

**验收来源**：[dictionary-release-119.md](dictionary-release-119.md)、[dictionary-release-119-result.json](dictionary-release-119-result.json)。

**关闭或补救评论与提交**：[评论6194259613](https://github.com/abluepoint/summer/issues/119#issuecomment-6194259613)（[f440eedd](https://github.com/abluepoint/summer/commit/f440eedd18cef11e27b1a0ae017b0e6f2c089bfe)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。原Chrome10/2/1/0及提示超时原因未证实；主动重读不替代T31实时通知。

### T12 持久化用户与真实登录

**需求**：[#120](https://github.com/abluepoint/summer/issues/120)；故事16、34。将用户认证从内存用户迁移到 MySQL 持久化，并保留当前 Summer UI 的表单登录、OIDC PKCE、会话超时和退出协议。

**设计**：真实认证用户与Customer分离，持久身份签发且保持public-client PKCE与内存Token。 依据：[0015-use-spring-security-public-client-pkce-for-summer-ui.md](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md)、[0023-consolidate-identity-frontend-in-summer-ui.md](../adr/0023-consolidate-identity-frontend-in-summer-ui.md)。

**实现**：[AuthUserService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/AuthUserService.java)、[LocalConsoleAuthenticationConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/LocalConsoleAuthenticationConfiguration.java)、[index.tsx](../../summer-ui/src/pages/auth-users/index.tsx)。

**测试**：2859与T13共用API112；协议Chrome10、CRUD/拒绝Chrome22；最终审计保护JUnit19单列。 测试入口：[AuthUserServiceTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/AuthUserServiceTest.java)、[IdentityChainE2EContractTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/IdentityChainE2EContractTest.java)。

**缺陷与修正**：认证主体与审计资源ID碰撞保护；共享H2上下文删表只修测试隔离，专用9绿。

**验收来源**：[identity-120-121.md](identity-120-121.md)、[summer4-closeout-20261015.json](summer4-closeout-20261015.json)。

**关闭或补救评论与提交**：[评论6189408554](https://github.com/abluepoint/summer/issues/120#issuecomment-6189408554)（[045a22a7](https://github.com/abluepoint/summer/commit/045a22a77189d1b129e28f6270db8c01a15862ed)、[985c5c24](https://github.com/abluepoint/summer/commit/985c5c24af7525926b643fedc1135620188a332e)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。共用112不能按两票重复计；自然闲置75秒仅对应配置15秒场景。

### T13 租户、组织与成员管理

**需求**：[#121](https://github.com/abluepoint/summer/issues/121)；故事29、30、34。建立租户、组织树、成员关系和当前组织上下文，为后续角色与数据权限提供真实边界。

**设计**：Enterprise、Member、Organization及上下文持久化，活动成员和企业状态在每次授权中复查。 依据：[0025-persist-enterprise-organization-context.md](../adr/0025-persist-enterprise-organization-context.md)、[0032-persist-enterprise-members-and-organization-context.md](../adr/0032-persist-enterprise-members-and-organization-context.md)。

**实现**：[OrganizationDirectoryService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/organization/OrganizationDirectoryService.java)、[LocalConsoleIdentityClaims.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/LocalConsoleIdentityClaims.java)、[index.tsx](../../summer-ui/src/pages/organization/index.tsx)。

**测试**：共享API112；旧Chrome19中1失败，修复后CRUD/拒绝22；新库及V36到V37升级。 测试入口：[OrganizationDirectoryMySqlHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/organization/OrganizationDirectoryMySqlHttpTest.java)、[PersistedMembershipAuthorizationMySqlHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/PersistedMembershipAuthorizationMySqlHttpTest.java)。

**缺陷与修正**：已选组织刷新丢失、旧JWT绕过成员停用→服务端上下文回读与实时成员判定→真实重启/页面补验。

**验收来源**：[identity-120-121.md](identity-120-121.md)。

**关闭或补救评论与提交**：[评论6189410667](https://github.com/abluepoint/summer/issues/121#issuecomment-6189410667)（[045a22a7](https://github.com/abluepoint/summer/commit/045a22a77189d1b129e28f6270db8c01a15862ed)、[985c5c24](https://github.com/abluepoint/summer/commit/985c5c24af7525926b643fedc1135620188a332e)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。193项广泛回归早于最终碰撞保护19；UI内存企业选择不等于服务端组织上下文切换。

### T14 完整接口资源目录

**需求**：[#122](https://github.com/abluepoint/summer/issues/122)；故事19、20、21、22、23。扫描全部 Spring MVC、协议、安全过滤链、Actuator 和健康接口，形成接口资源目录并分类，支持自动权限码和显式覆盖。

**设计**：扫描全部HTTP映射并分类，配置资源和组件自有安全边界分开，未知扫描保持不完整。 依据：[0030-deny-unassigned-users-and-align-configurable-resource-grants.md](../adr/0030-deny-unassigned-users-and-align-configurable-resource-grants.md)。

**实现**：[InterfaceResourceCatalogService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resource/InterfaceResourceCatalogService.java)、[InterfaceResourceCatalogController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resource/InterfaceResourceCatalogController.java)、[index.tsx](../../summer-ui/src/pages/interface-resources/index.tsx)。

**测试**：9976最终27/0/0/0，含16后端/环境、6Chrome和5纯生产检查；本地JUnit25。 测试入口：[InterfaceResourceDiscoveryHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/resource/InterfaceResourceDiscoveryHttpTest.java)、[InterfaceResourceManagementContextHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/resource/InterfaceResourceManagementContextHttpTest.java)。

**缺陷与修正**：协议URL可配置标记和CompositeHandlerMapping误报complete=false→精确委托覆盖修复→生产无fixture目录完整检查。

**验收来源**：[interface-resources.md](interface-resources.md)、[interface-resources-result.json](interface-resources-result.json)。

**关闭或补救评论与提交**：[评论6189702773](https://github.com/abluepoint/summer/issues/122#issuecomment-6189702773)（[906e5498](https://github.com/abluepoint/summer/commit/906e5498399c05ae4f180213373117482fe09c54)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。未知映射仍报告不完整；管理健康存在不等于目录完整，旧27中1失败保留。

### T15 角色与接口授权

**需求**：[#123](https://github.com/abluepoint/summer/issues/123)；故事17、18、22、23、28。提供角色、权限绑定、用户分配和显式拒绝，完成接口资源到真实 API 的后端授权闭环。

**设计**：角色/用户绑定按企业与版本在数据库裁决，显式拒绝优先，提交后发布投影及回执。 依据：[0030-deny-unassigned-users-and-align-configurable-resource-grants.md](../adr/0030-deny-unassigned-users-and-align-configurable-resource-grants.md)。

**实现**：[AuthorizationService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationService.java)、[AuthorizationController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationController.java)、[index.tsx](../../summer-ui/src/pages/roles/index.tsx)。

**测试**：442e双JVM定向16/0/0/0；保留原57绿与Chrome8功能绿；JUnit34、组件2。 测试入口：[AuthorizationVersionConcurrencyTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizationVersionConcurrencyTest.java)、[AuthorizationAssignmentHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizationAssignmentHttpTest.java)。

**缺陷与修正**：双节点同版本丢写及UI固定If-Match1→数据库CAS和页面维护新版本→一200一412、真实200/403/200。

**验收来源**：[authorization-roles.md](authorization-roles.md)、[authorization-roles-result.json](authorization-roles-result.json)。

**关闭或补救评论与提交**：[评论6190730911](https://github.com/abluepoint/summer/issues/123#issuecomment-6190730911)（[49c10c71](https://github.com/abluepoint/summer/commit/49c10c71041db48a0b7ed9902218133c1d51f7e0)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。Chrome功能8绿但旧退出-15保留；独立生命周期0业务不算功能；重启JVM非重启MySQL。

### T16 菜单 manifest 与动作权限

**需求**：[#124](https://github.com/abluepoint/summer/issues/124)；故事24、25、26、27、28。让前端从路由定义生成菜单 manifest，后端保存菜单元数据和 Capability 绑定，并按当前主体返回可见菜单。

**设计**：路由生成组件解耦manifest，数据库版本覆盖与后端动作能力控制菜单和按钮。 依据：[0026-use-versioned-route-menu-manifest.md](../adr/0026-use-versioned-route-menu-manifest.md)。

**实现**：[MenuManifestService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/menu/MenuManifestService.java)、[MenuManifestController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/menu/MenuManifestController.java)、[index.tsx](../../summer-ui/src/pages/menu-manifest/index.tsx)、[manifest.ts](../../summer-ui/src/menu/manifest.ts)。

**测试**：993候选实际HTTP及两轮定向Chrome见分批JSON；原完整47/3/1/0保留。 测试入口：[MenuManifestConcurrencyTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/menu/MenuManifestConcurrencyTest.java)、[MenuManifestHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/menu/MenuManifestHttpTest.java)。

**缺陷与修正**：菜单版本竞争/回滚、动作权限和父路由接住停用子路由→CAS、提交后回执、精确父路径匹配。

**验收来源**：[menu-manifest.md](menu-manifest.md)、[menu-manifest-result.json](menu-manifest-result.json)。

**关闭或补救评论与提交**：[评论6194509367](https://github.com/abluepoint/summer/issues/124#issuecomment-6194509367)（[fcde16b7](https://github.com/abluepoint/summer/commit/fcde16b7883c4dc2e12105033f4fefff1802359c)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。最终Chrome有界强制回收如实保留；前端隐藏不替代服务端授权。

### T17 基础行级数据权限

**需求**：[#125](https://github.com/abluepoint/summer/issues/125)；故事29、30、31、32、33。在系统参数或字典查询中落实租户、组织、部门、本人、创建人和负责人数据范围，并在查询层统一注入。

**设计**：结构化DataScope在查询层注入强制租户边界，缺策略默认拒绝，禁止原始SQL。 依据：[0030-deny-unassigned-users-and-align-configurable-resource-grants.md](../adr/0030-deny-unassigned-users-and-align-configurable-resource-grants.md)、[0032-persist-enterprise-members-and-organization-context.md](../adr/0032-persist-enterprise-members-and-organization-context.md)。

**实现**：[DataScopeService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/DataScopeService.java)、[SystemParameterQueryService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterQueryService.java)、[index.tsx](../../summer-ui/src/pages/data-scopes/index.tsx)。

**测试**：993真实后端279/0/0/0；最终共享UI6及配套HTTP6分别绿。 测试入口：[DataScopeEnforcementHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/DataScopeEnforcementHttpTest.java)、[DataScopeEffectivePolicyTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/DataScopeEffectivePolicyTest.java)。

**缺陷与修正**：非法策略400后成功200仍显示旧错误→保存成功清错误→原生6/1到共享UI6/0。

**验收来源**：[data-scopes.md](data-scopes.md)、[data-scopes-result.json](data-scopes-result.json)。

**关闭或补救评论与提交**：[评论6195539306](https://github.com/abluepoint/summer/issues/125#issuecomment-6195539306)（[97d58065](https://github.com/abluepoint/summer/commit/97d58065aeef7957d88211bffe019c05aa88bed7)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。后端不因本票改动；独立快照仅历史对照，最终以共享工作区回放为准。

### T18 组合数据策略与有效权限预览

**需求**：[#126](https://github.com/abluepoint/summer/issues/126)；故事30、31、32、33。支持组织树、指定组织、创建人、负责人和 AND/OR 条件树，并在管理端展示合并后的有效数据权限。

**设计**：组合AND/OR与有效策略预览沿用DataScope，签发时复制可信活动租户和组织上下文。 依据：[0032-persist-enterprise-members-and-organization-context.md](../adr/0032-persist-enterprise-members-and-organization-context.md)。

**实现**：[DataScopeService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/DataScopeService.java)、[LocalConsoleTokenConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/LocalConsoleTokenConfiguration.java)、[index.tsx](../../summer-ui/src/pages/data-scopes/index.tsx)。

**测试**：旧a9后端123绿；单租户18HTTP及Chrome6+2；9f双租户22HTTP及Chrome5；声明JUnit4。 测试入口：[DataScopeCompositionHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/DataScopeCompositionHttpTest.java)、[LocalConsoleTokenContextTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/LocalConsoleTokenContextTest.java)。

**缺陷与修正**：resolver已有声明但签发白名单遗漏→只补两项可信声明→真实PKCE双租户预览。

**验收来源**：[composed-data-scopes-126.md](composed-data-scopes-126.md)、[composed-data-scopes-126-result.json](composed-data-scopes-126-result.json)。

**关闭或补救评论与提交**：[评论6196239453](https://github.com/abluepoint/summer/issues/126#issuecomment-6196239453)（[2498fc1b](https://github.com/abluepoint/summer/commit/2498fc1be2701a3f529e47c62b6fc764f56fc434)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。9f还包含T29变更；未重跑旧123；审计故障仅证明本资源回滚，不扩称全outbox。

### T19 参数与字典的授权读取

**需求**：[#127](https://github.com/abluepoint/summer/issues/127)；故事5、6、7、8、29、33。将参数和字典管理端、组织级配置和公共读取接入接口权限与数据权限。

**设计**：动作权限、持久DataScope与租户组织覆盖共同决定参数/字典有效读取和发布。 依据：[0028-enforce-effective-configuration-scopes.md](../adr/0028-enforce-effective-configuration-scopes.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[ConfigurationOverrideService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/configuration/ConfigurationOverrideService.java)、[SystemParameterReleaseService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterReleaseService.java)、[DictionaryVersionService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryVersionService.java)、[index.tsx](../../summer-ui/src/pages/dictionary/index.tsx)。

**测试**：9f真实主批156、任意租户149、Chrome配套36及Chrome7分别全绿。 测试入口：[AuthorizedConfigurationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizedConfigurationHttpTest.java)、[MultiTenantConfigurationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/MultiTenantConfigurationHttpTest.java)。

**缺陷与修正**：失败批为能力ID归属、声明和版本字段夹具错误，修正正式HTTP夹具；未为通过测试放宽产品。

**验收来源**：[authorized-configuration-127.md](authorized-configuration-127.md)、[authorized-configuration-127-result.json](authorized-configuration-127-result.json)。

**关闭或补救评论与提交**：[评论6196982699](https://github.com/abluepoint/summer/issues/127#issuecomment-6196982699)（[b56135c8](https://github.com/abluepoint/summer/commit/b56135c8e1fac4b02f088630aced29141c250737)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。故障回滚范围为覆盖行及相应审计，未证明全部业务表/outbox；无本票生产修复。

### T20 权限变更跨节点生效

**需求**：[#128](https://github.com/abluepoint/summer/issues/128)；故事15、17、18、27、28、32。让角色、接口、菜单和数据策略变更通过 outbox 与 Redis Stream 传播，并使多节点权限缓存最终一致。

**设计**：授权事务outbox与独立节点消费组传播失效，页面定时/聚焦重读当前权限并失败关闭。 依据：[0027-versioned-authorization-change-stream.md](../adr/0027-versioned-authorization-change-stream.md)。

**实现**：[AuthorizationChangeEventStreamListener.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationChangeEventStreamListener.java)、[AuthorizationChangeEventService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationChangeEventService.java)、[index.tsx](../../summer-ui/src/pages/authorization-events/index.tsx)。

**测试**：真实Chrome权限6、超时传输5；Redis恢复21及逐节点22分别绿；JUnit6。 测试入口：[AuthorizationChangeEventStreamRecoveryIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizationChangeEventStreamRecoveryIntegrationTest.java)、[AuthorizationChangeEventTerminalTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizationChangeEventTerminalTest.java)。

**缺陷与修正**：页面不主动刷新、悬停不超时、Redis重建组跳过首条→轮询/超时及0-0建组→真实恢复ACK。

**验收来源**：[authorization-propagation-128.md](authorization-propagation-128.md)、[authorization-propagation-128-result.json](authorization-propagation-128-result.json)。

**关闭或补救评论与提交**：[评论6197171978](https://github.com/abluepoint/summer/issues/128#issuecomment-6197171978)（[f771837e](https://github.com/abluepoint/summer/commit/f771837e9481f8b1e70b74920c8b8249ce02bb13)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。后台节流不承诺硬实时；本票旧记录不证明后续发现的事件B作用域缺陷，见T34补救。

### T21 数据库密钥与稳定签发

**需求**：[#129](https://github.com/abluepoint/summer/issues/129)；故事3、34、35、36、37。实现 Summer 4 本地密钥 Capability module，将真实私钥加密保存到 MySQL，支持签名、公钥/JWK、验签和重启后稳定签发。

**设计**：MySQL保存密文私钥，运行时主密钥解密；公开JWK保持Raw协议，失败审计只存安全标识。 依据：[0004-include-an-optional-authorization-server.md](../adr/0004-include-an-optional-authorization-server.md)、[0015-use-spring-security-public-client-pkce-for-summer-ui.md](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md)。

**实现**：[MysqlSummerKeyStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/MysqlSummerKeyStore.java)、[KeyCapabilityController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/KeyCapabilityController.java)、[index.tsx](../../summer-ui/src/pages/security-keys/index.tsx)。

**测试**：442e真实故障补验9；9976功能23和Chrome6保留；本地审计/MVC19、新失败审计9、兼容10分列。 测试入口：[MysqlKeyFailureAuditTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/MysqlKeyFailureAuditTest.java)、[KeyFailureAuditTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/KeyFailureAuditTest.java)。

**缺陷与修正**：失败认证审计泄露Bearer、Raw500错误包装、缺读取/签名失败审计→三个窄修复→真实密钥表故障和恢复。

**验收来源**：[security-keys-129.md](security-keys-129.md)、[security-keys-129-result.json](security-keys-129-result.json)。

**关闭或补救评论与提交**：[评论6190703221](https://github.com/abluepoint/summer/issues/129#issuecomment-6190703221)（[c88eb236](https://github.com/abluepoint/summer/commit/c88eb236da12e4c95a634ce0483c308b29d87012)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。Raw500只有关联头无业务信封；不把新9项冒称整个旧矩阵重跑。

### T22 引用型密钥接入

**需求**：[#130](https://github.com/abluepoint/summer/issues/130)；故事3、35。为密钥模块增加环境变量、Kubernetes Secret 和受控文件引用模式，并提供来源状态和失败诊断。

**设计**：引用仅保存来源和引用名，环境/受控文件/Secret在运行时解析并失败关闭。 依据：[0004-include-an-optional-authorization-server.md](../adr/0004-include-an-optional-authorization-server.md)。

**实现**：[ReferenceKeyMaterialResolver.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/ReferenceKeyMaterialResolver.java)、[ReferenceSummerKeyStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/ReferenceSummerKeyStore.java)、[index.tsx](../../summer-ui/src/pages/security-keys/index.tsx)。

**测试**：442e真实运行45与Chrome6；13种无效启动包含在45内。 测试入口：[ReferenceKeyConfigurationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/ReferenceKeyConfigurationTest.java)。

**缺陷与修正**：首轮健康等待超时根因未证实，保留未完成回执；补诊断后新批通过，未宣称生产修复。

**验收来源**：[key-references-130.md](key-references-130.md)、[key-references-130-result.json](key-references-130-result.json)。

**关闭或补救评论与提交**：[评论6193624404](https://github.com/abluepoint/summer/issues/130#issuecomment-6193624404)（[821b0859](https://github.com/abluepoint/summer/commit/821b0859de39c24708a7c3f2048daca32bea0859)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。Secret由K8s Pod注入后供本机JVM，应用未部署K8s；引用模式轮换不在本票承诺中。

### T23 手动密钥轮换

**需求**：[#131](https://github.com/abluepoint/summer/issues/131)；故事35、37。提供人工轮换、kid 状态管理和新旧公钥并存验证。

**设计**：数据库轮换状态行锁裁决版本，成功回执和JWK快照在事务提交后发布，旧公钥保留GRACE。 依据：[0004-include-an-optional-authorization-server.md](../adr/0004-include-an-optional-authorization-server.md)。

**实现**：[MysqlSummerKeyStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/MysqlSummerKeyStore.java)、[KeyCapabilityController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/KeyCapabilityController.java)、[index.tsx](../../summer-ui/src/pages/security-keys/index.tsx)。

**测试**：917新候选HTTP10、Chrome4；JUnit19、组件2；旧442失败批保留。 测试入口：[ManualKeyRotationTransactionTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/ManualKeyRotationTransactionTest.java)、[KeyCapabilityHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/KeyCapabilityHttpTest.java)。

**缺陷与修正**：失败同键重试返回未持久kid、并发500、页面缺requestId→行锁/提交后发布/页面修复→三个实际定向绿。

**验收来源**：[key-rotation-131.md](key-rotation-131.md)、[key-rotation-131-result.json](key-rotation-131-result.json)。

**关闭或补救评论与提交**：[评论6194583784](https://github.com/abluepoint/summer/issues/131#issuecomment-6194583784)（[94a49e95](https://github.com/abluepoint/summer/commit/94a49e95892b9b62036e5988118034a244a4150e)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。GRACE截止使用时间夹具，不冒称自然等待或独立人工停用完整能力。

### T24 OAuth 客户端与授权持久化

**需求**：[#132](https://github.com/abluepoint/summer/issues/132)；故事1、34。将 OAuth 客户端、授权同意和授权记录持久化到 MySQL，同时保持 Summer UI public client、PKCE、无 refresh token 边界。

**设计**：OAuth客户端、授权和同意持久化；默认种子只初始化，协议端点保持Raw与PKCE。 依据：[0015-use-spring-security-public-client-pkce-for-summer-ui.md](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md)。

**实现**：[JdbcSummerOAuthClientStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/JdbcSummerOAuthClientStore.java)、[JdbcSummerAuthorizationStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/JdbcSummerAuthorizationStore.java)、[JdbcSummerConsentStore.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/JdbcSummerConsentStore.java)、[index.tsx](../../summer-ui/src/pages/oauth-clients/index.tsx)。

**测试**：917首批48/1/0/0；Chrome15+故障2；修正过期夹具补13/0/0/0，另离线审计1。 测试入口：[OAuthPersistenceHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/OAuthPersistenceHttpTest.java)。

**缺陷与修正**：expiresAt早于issuedAt的夹具制造500→改合法过期区间；未发现需改OAuth生产代码的缺陷。

**验收来源**：[oauth-persistence-132.md](oauth-persistence-132.md)、[oauth-persistence-132-result.json](oauth-persistence-132-result.json)。

**关闭或补救评论与提交**：[评论6195495044](https://github.com/abluepoint/summer/issues/132#issuecomment-6195495044)（[338128e5](https://github.com/abluepoint/summer/commit/338128e596957bf8a7494eb5f7691f886ad3e7aa)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。不同运行去重59不是61；离线审计不算新增HTTP，过期为时间夹具。

### T25 共享登录会话与撤销

**需求**：[#133](https://github.com/abluepoint/summer/issues/133)；故事1、34、42。使用 Redis 共享会话索引，支持多节点登录、退出、会话查询和撤销，并保持浏览器安全边界。

**设计**：Servlet登录上下文由Redis共享；Bearer使用请求级仓库，注销和续期与索引撤销一致。 依据：[0029-share-servlet-login-context-and-isolate-bearer-authentication.md](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。

**实现**：[SharedHttpSessionConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/SharedHttpSessionConfiguration.java)、[SessionIndexService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/SessionIndexService.java)、[index.tsx](../../summer-ui/src/pages/sessions/index.tsx)。

**测试**：2026-10-07历史Sample47、安全自动配置12、完整Sample双进程Chrome7分别通过；本轮未重跑。 测试入口：[SharedSessionNodesHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/SharedSessionNodesHttpTest.java)、[SharedSessionRedisFailureHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/SharedSessionRedisFailureHttpTest.java)。

**缺陷与修正**：跨节点401、CSRF缺口、Bearer隐式访问Session Redis、JDBC反序列化→共享会话/请求隔离/受限类型映射修复。

**验收来源**：[summer4-t25-shared-session-acceptance.md](../guides/summer4-t25-shared-session-acceptance.md)、[summer4-historical-ac-109-117-133.md](summer4-historical-ac-109-117-133.md)。

**关闭或补救评论与提交**：[评论6185698881](https://github.com/abluepoint/summer/issues/133#issuecomment-6185698881)（[ef96e13b](https://github.com/abluepoint/summer/commit/ef96e13bf879b4aa5a3bbaf2fc8f98d67c5d75b3)）

**状态与批次局限**：历史CLOSED；公开21AC逐项复核已完成，复选框同步待root。原历史21AC已由root独立逐项核对并输出公开报告，复选框同步仍由root负责；本次T36原生结果与历史计数分列。

### T26 定时任务配置与执行

**需求**：[#134](https://github.com/abluepoint/summer/issues/134)；故事38、39。接入 Quartz 集群表和任务管理页面，提供任务创建、启停、立即执行和执行历史。

**设计**：Quartz持久任务定义、执行与审计；管理能力和接口权限同时授权，输入和版本经正式API验证。 依据：[父方案设计决策](https://github.com/abluepoint/summer/issues/108)。

**实现**：[TaskSchedulerService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskSchedulerService.java)、[TaskSchedulerController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskSchedulerController.java)、[index.tsx](../../summer-ui/src/pages/tasks/index.tsx)。

**测试**：真实HTTP13、Chrome7；旧688中的调度JUnit4仅历史关联。 测试入口：[TaskSchedulerHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskSchedulerHttpTest.java)、[TaskMySqlFlywayIntegrationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskMySqlFlywayIntegrationTest.java)。

**缺陷与修正**：初始化漏幂等头导致500由独立MVC缺头合同修复；任务页时间戳修复归历史任务记录。

**验收来源**：[task-configuration-134.md](task-configuration-134.md)、[task-configuration-134-result.json](task-configuration-134-result.json)。

**关闭或补救评论与提交**：[评论6194685283](https://github.com/abluepoint/summer/issues/134#issuecomment-6194685283)（[df437af3](https://github.com/abluepoint/summer/commit/df437af3f0006e73154c35e4092af064c6ee6e7a)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。初始化失败不当任务产品失败；生产任务源码本轮未改。

### T27 任务重试与集群故障恢复

**需求**：[#135](https://github.com/abluepoint/summer/issues/135)；故事38、39。为任务增加幂等键、失败重试、节点接管、暂停恢复和副作用保护。

**设计**：至少一次调度结合业务事务幂等收据；短事务领取和唯一owner防止旧节点覆盖接管结果。 依据：[父方案设计决策](https://github.com/abluepoint/summer/issues/108)。

**实现**：[TaskExecutionService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskExecutionService.java)、[TaskSchedulerService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskSchedulerService.java)、[index.tsx](../../summer-ui/src/pages/tasks/index.tsx)。

**测试**：E1真实补5；保留2859双JVM业务3、Chrome6和接管1；相关JUnit19分批。 测试入口：[TaskExecutionRecoveryTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskExecutionRecoveryTest.java)、[TaskBusinessIdempotencyMySqlTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskBusinessIdempotencyMySqlTest.java)。

**缺陷与修正**：长事务看不到进度、失败同键不重派发、演示租户恢复限制、浏览器伪造结束时间→对应修复和真实接管。

**验收来源**：[task-recovery-135.md](task-recovery-135.md)、[task-recovery-135-result.json](task-recovery-135-result.json)。

**关闭或补救评论与提交**：[评论6195190396](https://github.com/abluepoint/summer/issues/135#issuecomment-6195190396)（[b8a950d8](https://github.com/abluepoint/summer/commit/b8a950d889b0107523a5cb8171a006f6a69c1e88)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。业务副作用一次依赖处理器在自身事务原子提交幂等收据；不承诺任意外部副作用exactly-once。

### T28 自动密钥轮换

**需求**：[#136](https://github.com/abluepoint/summer/issues/136)；故事35、37、38。将密钥轮换接入分布式任务，支持自动轮换、失败重试、告警状态和旧 key 保留。

**设计**：Quartz集群周期触发既有轮换事务；失败回滚后在新事务记录状态、退避与安全审计。 依据：[父方案设计决策](https://github.com/abluepoint/summer/issues/108)。

**实现**：[AutomaticKeyRotationService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/AutomaticKeyRotationService.java)、[AutomaticKeyRotationQuartzConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/AutomaticKeyRotationQuartzConfiguration.java)、[index.tsx](../../summer-ui/src/pages/security-keys/index.tsx)。

**测试**：修复前真实7/1；最终轮换JUnit7、审计6、真实基础7/补10、Chrome4+4+1分批全绿。 测试入口：[AutomaticKeyRotationTransactionTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/AutomaticKeyRotationTransactionTest.java)、[AutomaticKeyRotationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/key/AutomaticKeyRotationHttpTest.java)。

**缺陷与修正**：JPA flush失败使同事务失败状态丢失、缺失败审计→回滚后新事务并重查成功计划→实际故障恢复。

**验收来源**：[automatic-key-rotation-136.md](automatic-key-rotation-136.md)、[automatic-key-rotation-136-result.json](automatic-key-rotation-136-result.json)。

**关闭或补救评论与提交**：[评论6195835345](https://github.com/abluepoint/summer/issues/136#issuecomment-6195835345)（[de0115c8](https://github.com/abluepoint/summer/commit/de0115c8e9d9d6aac3698575b79caf44bc54bf41)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。到期时间夹具不是自然等待90天；成功/失败批和旧手动轮换不合并为新全量。

### T29 SSE 任务进度与恢复

**需求**：[#137](https://github.com/abluepoint/summer/issues/137)；故事41、42。为任务和长耗时操作提供鉴权 SSE，支持进度、脱敏日志、心跳、断线恢复和 Last-Event-ID。

**设计**：Cookie EventSource按持久事件ID恢复，实时授权与会话复查，发送池和连接数有界。 依据：[0029-share-servlet-login-context-and-isolate-bearer-authentication.md](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。

**实现**：[TaskProgressEventService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskProgressEventService.java)、[TaskProgressController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskProgressController.java)、[index.tsx](../../summer-ui/src/pages/tasks/index.tsx)。

**测试**：9f协议17、真实TCP背压8、跨租户容量5分别绿；最终JUnit12。 测试入口：[TaskProgressBackpressureTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskProgressBackpressureTest.java)、[TaskProgressSseHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/task/TaskProgressSseHttpTest.java)。

**缺陷与修正**：4个不读连接阻塞原4线程池→核心4最大256直接交接及回收→健康订阅及时收到事件。

**验收来源**：[task-progress-137.md](task-progress-137.md)、[task-progress-137-result.json](task-progress-137-result.json)。

**关闭或补救评论与提交**：[评论6196583113](https://github.com/abluepoint/summer/issues/137#issuecomment-6196583113)（[9b48457a](https://github.com/abluepoint/summer/commit/9b48457a83cb506d29c80d7d2e56844989f38c64)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。历史窗口最多500，越界resync；中间虚拟线程绿非最终实现，容量等待失败原记录保留。

### T30 WebSocket 双向消息

**需求**：[#138](https://github.com/abluepoint/summer/issues/138)；故事40、42。提供认证、租户隔离、节点转发、确认、心跳和重连的 WebSocket 消息通道。

**设计**：持久序号和企业行锁保证消息顺序与幂等，连接按当前成员/权限和会话lease复查。 依据：[0029-share-servlet-login-context-and-isolate-bearer-authentication.md](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。

**实现**：[RealtimeMessageService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/realtime/RealtimeMessageService.java)、[RealtimeWebSocketConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/realtime/RealtimeWebSocketConfiguration.java)、[index.tsx](../../summer-ui/src/pages/messages/index.tsx)。

**测试**：442e真实协议16、Chrome7、JUnit17分别通过。 测试入口：[RealtimeDeliveryOrderTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/realtime/RealtimeDeliveryOrderTest.java)、[RealtimeWebSocketRevocationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/realtime/RealtimeWebSocketRevocationTest.java)。

**缺陷与修正**：会话撤销仍投递、并发序号/去重及乱序、超长文本反馈→持久游标串行投递和发送/输入边界修复。

**验收来源**：[realtime-messages-138.md](realtime-messages-138.md)、[realtime-messages-138-result.json](realtime-messages-138-result.json)。

**关闭或补救评论与提交**：[评论6190789741](https://github.com/abluepoint/summer/issues/138#issuecomment-6190789741)（[9da9e009](https://github.com/abluepoint/summer/commit/9da9e0091669eb94ee6de700f1c890be8e7a914b)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。发送阻塞只由专用测试覆盖，真实网络未耗尽内核缓冲；超长输入客户端阻止不证明连接关闭。

### T31 字典更新实时通知前端

**需求**：[#139](https://github.com/abluepoint/summer/issues/139)；故事9、41、42。将字典发布事件推送给已打开的 Summer UI 页面，触发安全刷新并保留未提交输入。

**设计**：字典生命周期只推失效元数据，页面重新读取当前有权版本；断线保留草稿按游标追赶。 依据：[0027-versioned-dictionary-public-snapshots.md](../adr/0027-versioned-dictionary-public-snapshots.md)。

**实现**：[DictionaryNotificationService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryNotificationService.java)、[DictionaryNotificationController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryNotificationController.java)、[index.tsx](../../summer-ui/src/pages/dictionary/index.tsx)。

**测试**：最终Chrome13/0/0/0及独立MySQL1；外层原2/0/1/0保留；生命周期探针0业务、exit0。 测试入口：[DictionaryNotificationSseHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/dictionary/DictionaryNotificationSseHttpTest.java)。

**缺陷与修正**：整JVM故障清权限属失败关闭；单独SSE断线测试代理未传错误→仅修代理及收尾，未改生产。

**验收来源**：[dictionary-notifications-139.md](dictionary-notifications-139.md)、[dictionary-notifications-139-result.json](dictionary-notifications-139-result.json)。

**关闭或补救评论与提交**：[评论6197613319](https://github.com/abluepoint/summer/issues/139#issuecomment-6197613319)（[cdb80094](https://github.com/abluepoint/summer/commit/cdb80094af4060da5ac2c2655a2c9e18dc6f50eb)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。功能绿不代表原runner全绿；0业务清理探针不计功能，不以T29覆盖本票。

### T32 依赖故障下的受控降级

**需求**：[#140](https://github.com/abluepoint/summer/issues/140)；故事43、44。以参数读取为场景接入 Resilience4j，落实超时、重试、限流、熔断、隔离和安全降级。

**设计**：仅安全读取允许有权限/版本/TTL证明的fallback；调用方TimeLimiter结果计入熔断，写入失败关闭。 依据：[0028-enforce-effective-configuration-scopes.md](../adr/0028-enforce-effective-configuration-scopes.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[ResilientReadService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resilience/ResilientReadService.java)、[ResilienceDiagnosticsController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resilience/ResilienceDiagnosticsController.java)、[index.tsx](../../summer-ui/src/pages/diagnostics/resilience/index.tsx)。

**测试**：JUnit最终相关21与retry11分批；原生88/1两批、定向业务41绿但收尾exit1；后续Chrome12及配套51全绿。 测试入口：[ResilientReadServiceTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/resilience/ResilientReadServiceTest.java)、[ResilienceRevocationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/ResilienceRevocationHttpTest.java)。

**缺陷与修正**：旧JWT撤权不生效、调用方超时未熔断、retry字段缺失→持久判定/统计边界/真实事件计数修复。

**验收来源**：[controlled-resilience-140.md](controlled-resilience-140.md)、[controlled-resilience-140-result.json](controlled-resilience-140-result.json)。

**关闭或补救评论与提交**：[评论6198053860](https://github.com/abluepoint/summer/issues/140#issuecomment-6198053860)（[cdec1f04](https://github.com/abluepoint/summer/commit/cdec1f04af75d738f0ee8269d0b6526789ce2d5e)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。41业务绿后的UNPAUSE超时补清单列；真实retry0按原值显示；密钥故障沿用T21不重跑。

### T33 运行监控与链路定位

**需求**：[#141](https://github.com/abluepoint/summer/issues/141)；故事2、11、45、52。接入 Actuator、Micrometer、Prometheus 和 OpenTelemetry，展示 HTTP、MySQL、Redis、审计和认证指标并关联 requestId。

**设计**：真实OTel span、Micrometer模块指标和安全审计关联，监控入口复查持久权限。 依据：[0010-use-framework-native-request-contexts.md](../adr/0010-use-framework-native-request-contexts.md)、[0020-separate-http-request-logging-responsibilities.md](../adr/0020-separate-http-request-logging-responsibilities.md)。

**实现**：[RuntimeDiagnosticsService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RuntimeDiagnosticsService.java)、[RuntimeDiagnosticsController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RuntimeDiagnosticsController.java)、[index.tsx](../../summer-ui/src/pages/diagnostics/runtime/index.tsx)。

**测试**：真实主批111/1/0/0含Chrome12，唯一标签正则误报；同候选补5/0/0/0；最终相关JUnit23。 测试入口：[RuntimeObservabilityHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/RuntimeObservabilityHttpTest.java)、[MonitoringRevocationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/operations/MonitoringRevocationHttpTest.java)。

**缺陷与修正**：缺指标/trace及旧JWT可读监控→实际观测接入与持久撤权→真实HTTP和故障恢复。

**验收来源**：[runtime-monitoring-141.md](runtime-monitoring-141.md)、[runtime-monitoring-141-result.json](runtime-monitoring-141-result.json)。

**关闭或补救评论与提交**：[评论6197383278](https://github.com/abluepoint/summer/issues/141#issuecomment-6197383278)（[1cc57d2d](https://github.com/abluepoint/summer/commit/1cc57d2d24d93e3896fa2cc1ca516f62ed60bd6a)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。未配置外部OTLP Collector；指标AVAILABLE不等于全链健康；部分原JUnit仅锁内解析记录无原XML。

### T34 基础业务统计

**需求**：[#142](https://github.com/abluepoint/summer/issues/142)；故事46、52。提供用户、角色、接口、字典、任务和审计的基础统计 API 与 Summer UI 展示。

**设计**：统计采用业务审计表和现有DataScope/有效成员口径，共享企业选择与角色/事件请求企业一致。 依据：[0030-deny-unassigned-users-and-align-configurable-resource-grants.md](../adr/0030-deny-unassigned-users-and-align-configurable-resource-grants.md)、[0032-persist-enterprise-members-and-organization-context.md](../adr/0032-persist-enterprise-members-and-organization-context.md)。

**实现**：[StatisticsService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/statistics/StatisticsService.java)、[StatisticsController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/statistics/StatisticsController.java)、[AuthorizationChangeEventController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationChangeEventController.java)、[AuthorizationRequestInterceptor.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationRequestInterceptor.java)、[index.tsx](../../summer-ui/src/pages/statistics/index.tsx)、[index.tsx](../../summer-ui/src/pages/roles/index.tsx)、[index.tsx](../../summer-ui/src/pages/authorization-events/index.tsx)。

**测试**：历史后端162、Chrome故障8/空拒绝5/上下文补35分批；错误码JUnit11及真实6；事件JUnit18，新8a2首117/0/2/0和补42/0/0/0含Chrome5。 测试入口：[StatisticsHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/statistics/StatisticsHttpTest.java)、[AuthorizationEventTenantAuthorizationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/AuthorizationEventTenantAuthorizationHttpTest.java)。

**缺陷与修正**：不存在AICT表、统计口径、共享入口、旧角色重复key、事件B撤权仍200→各限定修复及原生补验；初始关闭后重开，root完成补救整合后再次关闭。

**验收来源**：[business-statistics-142.md](business-statistics-142.md)、[business-statistics-142-standards-fix.md](business-statistics-142-standards-fix.md)、[enterprise-page-integration-142.md](enterprise-page-integration-142.md)、[enterprise-event-authorization-142.md](enterprise-event-authorization-142.md)、[business-statistics-142-result.json](business-statistics-142-result.json)、[enterprise-page-integration-142-result.json](enterprise-page-integration-142-result.json)、[enterprise-event-authorization-142-result.json](enterprise-event-authorization-142-result.json)。

**关闭或补救评论与提交**：[评论6199149251](https://github.com/abluepoint/summer/issues/142#issuecomment-6199149251)（[53c4b015](https://github.com/abluepoint/summer/commit/53c4b015db2424a75c8189b6c83019fb599ec370)）；[评论6199311541](https://github.com/abluepoint/summer/issues/142#issuecomment-6199311541)（[35bdb130](https://github.com/abluepoint/summer/commit/35bdb13088be55ad3ecb7377a56bb4f763f80896)、[53c4b015](https://github.com/abluepoint/summer/commit/53c4b015db2424a75c8189b6c83019fb599ec370)）；[评论6200616278](https://github.com/abluepoint/summer/issues/142#issuecomment-6200616278)（[6c024b52](https://github.com/abluepoint/summer/commit/6c024b5270437ce811cdc9ac36bb1e8f7d953be0)）

**状态与批次局限**：CLOSED/COMPLETED；root已推送6c024b52并回读7/7。原11批与所有错误保留；事件native06用A门禁不证明B撤权；候选02补批才验证B旧JWT，root已于关闭评论6200616278回读关闭。

### T35 受信任插件启停与升级

**需求**：[#143](https://github.com/abluepoint/summer/issues/143)；故事47、48、50。提供一个真实插件扩展示例，验证插件注册、能力声明、配置、前端 manifest、权限、审计、版本检查和启停。

**设计**：随包受信任插件通过摘要/版本注册，复用参数字典、菜单、权限、DataScope和审计。 依据：[0028-enforce-effective-configuration-scopes.md](../adr/0028-enforce-effective-configuration-scopes.md)、[0031-resolve-configuration-tenants-and-version-overrides.md](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。

**实现**：[PluginService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/plugin/PluginService.java)、[PluginController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/plugin/PluginController.java)、[index.tsx](../../summer-ui/src/pages/plugins/index.tsx)。

**测试**：旧服务端105、Chrome9+拒绝1、竞态组件6；Standards新4dd真实9、相关JUnit16/12/1分批。 测试入口：[PluginLifecycleHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/plugin/PluginLifecycleHttpTest.java)、[PluginConfigurationHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/plugin/PluginConfigurationHttpTest.java)。

**缺陷与修正**：企业子路由落错页、403旧卡残留及迟到响应、审计requestId与业务码命名→各定向红绿与真实补验。

**验收来源**：[trusted-plugins-143.md](trusted-plugins-143.md)、[trusted-plugins-143-result.json](trusted-plugins-143-result.json)。

**关闭或补救评论与提交**：[评论6198171002](https://github.com/abluepoint/summer/issues/143#issuecomment-6198171002)（[35bdb130](https://github.com/abluepoint/summer/commit/35bdb13088be55ad3ecb7377a56bb4f763f80896)）；[评论6198735952](https://github.com/abluepoint/summer/issues/143#issuecomment-6198735952)（[9feba275](https://github.com/abluepoint/summer/commit/9feba2753fb10883fe800bc4df5146db28c93473)）

**状态与批次局限**：历史CLOSED；当前最终回读待root。无任意代码上传/前端动态装载；部分Chrome强制回收；新9不冒称旧105重跑。

### T36 升级与水平扩展验收

**需求**：[#144](https://github.com/abluepoint/summer/issues/144)；故事49、50、51、52。汇总 Flyway 升级预检、备份恢复、滚动升级、两节点一致性和未来微服务拆分边界，形成最终验收门禁。

**设计**：模块化单体保留稳定契约，十项预检与真实备份恢复/expand-contract/兼容窗口分开取证。 依据：[0002-separate-capability-assembly-and-entry-points.md](../adr/0002-separate-capability-assembly-and-entry-points.md)、[0009-reserve-the-summer-error-namespace.md](../adr/0009-reserve-the-summer-error-namespace.md)。

**实现**：[ReleaseGateService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/release/ReleaseGateService.java)、[ReleaseGateController.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/release/ReleaseGateController.java)、[AuthorizationService.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationService.java)、[index.tsx](../../summer-ui/src/pages/diagnostics/release/index.tsx)、[SessionFailureConfiguration.java](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/SessionFailureConfiguration.java)。

**测试**：候选02scope93、历史31→39迁移54、cache故障76各0失败/错误/跳过；综合214/1/1/0中的前213通过，所有旧Chrome/夹具/500失败保留。候选03仅受影响原生补批85/0/0/0、Chrome5/0/0/0，清理完成；987个runtime条目仅两个SessionFailureConfiguration class变化，未受影响02批明确复用。 测试入口：[T36UpgradeAcceptanceHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/release/T36UpgradeAcceptanceHttpTest.java)、[ReleasePlatformScopeHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/release/ReleasePlatformScopeHttpTest.java)、[ReleaseErrorStatusHttpTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/release/ReleaseErrorStatusHttpTest.java)、[SessionFailureConfigurationTest.java](../../summer-sample/src/test/java/com/abluepoint/summer/sample/identity/SessionFailureConfigurationTest.java)。

**缺陷与修正**：未知异常泄露、固定400绕过解析器、旧JWT撤权和同码ENTERPRISE替代PLATFORM均有有效红绿。真实Redis会话超时500/success=true/error=null由root有限修复Redis原因链识别，定向9绿及候选03真实Cookie503/no-data/no-store、Bearer隔离与恢复通过；JDBC超时负对照保留。

**验收来源**：[summer4-t36-final-acceptance.md](../guides/summer4-t36-final-acceptance.md)、[summer4-t36-upgrade-acceptance.md](../guides/summer4-t36-upgrade-acceptance.md)、[summer4-final-gates-144-v2.md](summer4-final-gates-144-v2.md)、[summer4-release-upgrade-144-result.json](summer4-release-upgrade-144-result.json)、[session-redis-timeout-144.md](session-redis-timeout-144.md)、[session-redis-timeout-144.json](session-redis-timeout-144.json)。

**关闭或补救评论与提交**：本票尚无本轮关闭评论或提交，保持开放。

**状态与批次局限**：本地技术验收证据通过；待root集成审阅/提交/关票。候选02原scope/迁移/滚动/cache批复用而非03新跑；两版制品与每批失败/退出/Chrome强制回收均保留。本地演练不代表生产Kubernetes或任意历史版本升级。

## 最终状态与复用边界

本追踪基线的链接覆盖校验只确认52个故事均映射到票据、36票均有六段追踪和七项AC来源、所有本地文件存在。它不执行测试，不复验历史关闭理由，也不把历史READY状态改成当前通过。T36真实验收已按原批回填，后续由root完成最终全仓结果、36票和252项AC状态回读及提交基线。
