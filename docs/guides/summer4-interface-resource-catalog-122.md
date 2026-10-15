# 接口资源目录与历史（T14 / #122）

## 运行时来源

目录在单例初始化、应用/管理子上下文刷新和管理员读取时扫描实际注册对象，不使用默认端点清单补齐结果。

| 来源 | 发现方式 | 权限配置含义 |
| --- | --- | --- |
| Spring MVC / Actuator | 每个上下文的 HandlerMapping / RequestMappingInfo | 业务接口使用现有角色权限；基础设施仍遵守组件自己的保护 |
| 独立 management 端口 | 子上下文生命周期事件，contextId=management | 不与 application 的同路径混淆；目录不保存监听器凭据 |
| Spring Security 协议端点 | 已安装过滤器的 RequestMatcher，结合实际链范围和 AuthorizationServerSettings | PROTOCOL_OWNED；目录可见不代表绕过 OAuth、CSRF、客户端认证或 Token 校验 |
| 静态资源 / WebSocket / URL handler | AbstractUrlHandlerMapping 的实际映射 | 静态与 WebSocket 保持组件保护；普通业务 URL handler 无适配器时不可配置 |
| 函数式路由 | RouterFunctions / RequestPredicates 元数据 visitor | 保留方法、嵌套路径、请求条件；业务 handler 未接入托管权限时默认拒绝 |
| 自定义映射 / 端点过滤器 | InterfaceResourceContributor bean | Optional.empty 表示该适配器不支持；返回空列表表示已识别且无端点 |

MVC 不依赖业务包名前缀。`/api/**`、`/internal/**` 被识别为业务路径；运行时统一角色拦截边界沿用现有 `/api/**`。其他业务路径应由其安全链/专用权限适配器保护，不应把目录标签当作授权。所有 MVC 条件保留 params、headers、consumes、produces、version/custom 类型及 declaredMethods。未限定方法的映射展开成标准 HTTP 方法，GET 同时列出 HEAD；这表示映射适用性，容器的 TRACE 开关、框架 OPTIONS 处理和安全链仍然生效。

目录字段：原有 method/path/handler/module/resourceType/sensitiveLevel/permissionCode/configurable，加上 contextId、source、conditions、configurationPolicy。协议和基础设施 URL 映射也明确标记 configurable=true；这表示可按所属组件的显式配置管理，不会改写 COMPONENT_OWNED 或协议自身的认证、CSRF、令牌校验。静态资源和未适配托管权限的非 MVC 业务映射保持各自策略。自动权限码仍为 `METHOD:/归一化模板`，显式 `@ResourcePermission` 优先。不同请求条件可以共享该权限码；它们不是独立的角色授权粒度。

协议过滤器适配器只读取 RequestMatcher/PathPattern 元数据，使用当前依赖 Spring Security 的内部字段；升级框架时必须运行发现测试。未知 HandlerMapping、无法解析的端点匹配器、函数式不透明谓词会令 `complete=false`，并返回 `SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED`。未知任意 Servlet Filter 无法仅凭 Java 类型推断其是否自行响应端点；此类组件必须提供 contributor。`complete` 表示已扫描来源中没有未支持项，不是对任意用户代码的可达性证明。适配器不得执行 handler，不得读取凭据、任意资源内容或请求正文。敏感条件值被脱敏。

## 冲突与旧授权保护

精确路由冲突（上下文、方法、归一化路径、条件）抛出显式异常；路径变量别名不允许掩盖相同模板冲突。同路径同方法且等优先级的 params/headers 条件若可同时成立，也拒绝建目录；互斥条件和不同上下文被保留。复杂正则、媒体类型和自定义条件仍由 Spring 自身的匹配规则处理，不能把此检查当作任意谓词求交证明。

历史中已接受的自动权限码记录其 handler 身份。删除接口不会删除角色或能力绑定。新 handler 复用旧自动码时，目录显示 `REPLACED_REQUIRES_EXPLICIT_PERMISSION`、`configurable=false`，请求边界返回 403；服务仍可启动，管理员可查看和比较历史。即使显式注解再次填写旧自动码，也不能绕过保护。迁移应使用新的、有明确业务含义的显式权限码，并通过现有角色管理流程重新授权。

资源模块的 MappedInterceptor 覆盖 `/api/**` 的 MVC、函数式和自定义 AbstractHandlerMapping。未经托管适配的业务 handler 默认拒绝。静态资源/WebSocket 保留原有组件保护。该拦截器只增加保护，不替代现有租户、角色、scope、显式禁止和协议校验；没有修改授权、身份或配置模块实现。

## 持久化与 API

Flyway `V34__interface_resource_history.sql` 创建 `summer_interface_resource_revision`，保存不可变 JSON 快照、内容 SHA-256、资源数和创建时间。应用只追加快照，不提供编辑/删除历史 API；相同内容复用 revision，并发相同快照通过唯一 fingerprint 收敛。revision 是内容快照标识，不是每次部署事件的连续序号，回退到旧内容可以返回较小 revision。

生产部署先执行 V34。测试使用现有 H2/Hibernate 测试配置；这不能替代生产 MySQL 迁移验收。历史包含移除资源描述，现有角色/能力表不做删除或重新绑定。重建历史服务后从持久化快照恢复旧 handler 保护关系。

所有读取要求现有 `platform.interface-resources.read` 权限、scope 和平台声明：

- `GET /api/v1/platform/interface-resources`：当前 items、revision、issues、complete，保持 ETag/304 与 X-Request-Id。
- `GET .../history?page=0&size=20`：分页历史；size 为 1–100。
- `GET .../history/{revision}`：不可变快照。
- `GET .../diff?from=1&to=2`：added、removed、changed；handler 替换标为 REPLACED。

非法参数返回 400，缺失历史返回 404，无权限返回 403；错误响应保留 requestId。当前目录读取会扫描并按内容变化追加历史，因此不是不产生持久化副作用的纯查询。

管理页提供当前目录、发现不完整提示、历史分页、快照详情、版本比较和替换保护说明。比较失败保留当前目录并显示 requestId；401/403 清除受保护结果，允许刷新重试。

## 验证和发布边界

本地测试覆盖：实际 Spring 上下文及 MockMvc 请求、独立 management HTTP 上下文、动态增删/替换映射、持久化历史恢复、业务扩展包的角色授权与显式拒绝。浏览器组件测试运行真实 Chrome/React，HTTP 响应使用夹具。它们不代表真实登录浏览器验收或真实 MySQL / 双节点验收。

最终集成由主任务使用同一份候选 JAR/UI 和独立 MySQL schema 完成：V34 首次/升级迁移、重启后历史与旧绑定保护、双节点相同快照收敛、真实主体的目录/历史/差异允许与拒绝、页面错误恢复及协议端点原有安全约束。测试计数、源码摘要和未执行项见交接证据，不用组件测试代替上述结果。
