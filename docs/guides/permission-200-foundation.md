# 权限01：统一授权边界与兼容基础

本票是 #200 的兼容 expand，父规格 #199、ADR0039。旧授权、特殊申请人入口、自身会话、菜单和协议策略继续生效；本票没有生产切换入口。共享文档 CONTEXT 和 ADR 索引由总控维护。

## 实际接入

Summer Security 的 `ResourceAuthorization` 是窄判定契约，输入 Domain、真实 Authentication（可为空）、Resource 和 requestId，输出允许/拒绝、理由、权威 revision 及相同域/资源。Resource 只携带稳定身份、类型、来源；没有 permissionCode 或 accessMode。`AuthorizationPolicy` 是服务端实现端口，按持久域模式选取唯一实现，重复实现阻止启动、缺失实现拒绝；请求不能提供引擎选择。

旧 MVC 拦截器在完成既有特殊入口和成员边界后，将实际 HandlerMethod 与 Spring RequestMappingInfo 交给 LegacyResourceAdapter；规范化方法、路由模板、请求参数/头、consumes/produces/version 条件和应用/来源使用有长度边界的确定性编码取 SHA256，并持久化稳定身份。不同路径实体值共享同一模板身份；方法、模板或条件变化生成新身份。自定义匹配条件无法可靠解析时拒绝，需要专门适配。同一定位键首次登记的旧 code 不被后续登记静默覆盖；不同 code 冲突明确拒绝，避免已解析请求在另一节点写入后串码。受控重绑定留给04，不提供自动放宽。

旧 ResourcePermission、自动 method:path 和旧权限投影只经 LegacyAuthorizationPolicy/LegacyResourceAdapter 桥接。旧元数据契约及其他消费者仍存在，不能把本票视为全局权限码删除。旧反复绑定保护继续独立运行。

`ResourceRequestAuthorization` 是资源过滤链专用 AuthorizationManager 扩展点。未提供时复用框架 AuthenticatedAuthorizationManager.authenticated()，保持原 authenticated 判断；DELETE 自身会话的原例外保留。协议、登录、监控和其他专属安全链不变。后续04在此接入动态访问方式与实际资源前置匹配，同时用 MVC 最终处理器复核。当前没有将业务路径改为 permitAll，也没有把无效 Bearer 降级为匿名。

可信非 MVC 扩展可通过 LegacyResourceAdapter.register 注册完整定位条件，再调用相同 ResourceAuthorization。HTTP 扩展还须注册其实际匹配/过滤适配；现有 BindingGuard 对没有适配的非 MVC 业务入口继续拒绝，不能靠注册一串 URL 绕过它。非 HTTP 调用同样必须由受信任服务代码提供域、主体及注册资源。

## 域和版本

V50 新增域控制表、资源身份账本及可空审计证据列，不改已发布迁移。缺少控制行的未切换域按兼容 LEGACY/revision0 处理；第一次有效 outbox 变更在相同事务内创建或推进持久版本。平台键为 PLATFORM:platform，企业键为 ENTERPRISE:enterpriseId。模式没有普通用户管理 API；12负责旧身份迁移账本，13负责旧数据策略等价迁移，14负责最终切换门禁和混合节点隔离，必须在切换前完成，不能将此兼容缺省解释成已交付新模式切换。

AuthorizationChangeEventService.enqueue 在业务/outbox 原事务内执行数据库原子版本递增；失败回滚时版本也回滚。消费/重试通知不会推进业务版本。角色、成员等现有发出授权变更的入口因此获得共同版本基础。06–08与后续消费者票必须把新权限、关联、组织和数据策略的所有间接变更完整接入，不能仅依赖 Redis 事件。

UnifiedAuthorizationService 每次判定使用 REQUIRES_NEW、READ_COMMITTED 短事务，挂起调用者旧 MySQL RR 事务；从主数据库读域模式和版本后选唯一策略，结束前再次确认。同次读取变化最多重读一次，再失败关闭。策略回传不同域、资源或版本时拒绝。没有 ALLOW 缓存；以后新增投影缓存仍不得跳过此权威版本确认。权威数据库读取或事务失败映射为依赖失败503并中止判定，不能回退旧 ALLOW。

资源判定审计增加稳定 resourceId 和 authorizationRevision，旧审计字段继续保留。仅 LEGACY 模式下，相同域、主体和请求的旧策略决策在原记录上补充证据，保持旧审计 API 每次决策一条记录。非 LEGACY 策略始终另存最终已确认的资源判定；即使内部调用旧检查，也不能用该中间检查的结果或理由替代最终证据。证据上下文仅绑定同步策略调用，结束或异常时恢复/清除，不跨请求复用；mutation/outbox 的原子性仍由原事务负责。

## 消费者责任

完整逐文件清单：[permission-200-consumers.csv](permission-200-consumers.csv)，精确调用位置、形式、输入SHA、类型和唯一迁移批次均已列出。execution/01/consumer-inventory.json 保留结构化来源与候选判定。搜索包括方法/注解、权限实体/绑定、身份 claims、菜单、UI权限、可信租户上下文、字典名称/选择数据适配、过滤链和 DataScopeService/ScopeFilter 数据访问调用；不以“其余业务”文字替代具体清单。最终 UI 补核新增14项，当前162项中09负责83项、10负责79项；包括并行新增的字典级联组件，后续实现须按实际源码继续核销。

09负责权限/角色/接口目录/菜单/身份/成员/组织/申请人管理及其共享身份UI消费者。10负责配置覆盖、字典/系统参数、插件、客户、任务/实时/统计、连接/物理执行、应用/Agent/委托、MCP、审计查询、运维/监控/发布及扩展安全适配。表内每个文件只有一个批次；标记 support-contract-or-persistence 的实体、仓储、查询类型和表达式是支持基础设施，不冒充实际鉴权调用。06–08交付数据权限定义、求值及读写/缓存约束基础；09/10仍分别负责各自管理/业务调用方迁入，支持基础设施归属不代替逐消费者核销。新发现入口先追加清单和明确归属，再实施。

## 兼容残留和边界

保留旧权限注解、描述中的 code、Capability/用户拒绝/包、固定 scope/服务校验、菜单 capabilityCode、旧 Token claims 和专属协议策略；09/10逐项迁移，14核对后统一收缩。OAuth scope、CSRF、PKCE、活动成员及资源归属继续承担独立边界。本票不交付新权限组/权限管理、三种访问方式、菜单新 OR 投影、五预设数据求值或生产切换。

执行证据、失败批次、输入摘要和清理回执位于 `.scratch/permission-management/execution/01`。实际功能验收以该目录最终结果为准；本说明不将静态盘点或历史绿色当作功能通过。
