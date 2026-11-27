# 服务端 Controller 字段校验检查

2026-11-24，基于 Summer 当前工作区生产 Java 源码。按 Spring MVC 处理方法计数，条件装配的 Controller 计入，路由别名合并在同一行。测试夹具、验收脚本及依赖框架自动提供的端点另属运行环境。

检查 55 个 Controller 的 283 个映射方法。需核对正文契约 54 个，需保留兼容行为 27 个，无显式传输字段 21 个，协议入口 4 个，已声明 177 个。

原清单的 153 个标量候选已全部声明 Field，涉及 35 个 Controller。路径、查询及 JSON 正文共享未知字段检查。已有专用业务错误码的写入接口继续由服务判断缺参、状态、权限及幂等冲突；Field 检查已提供字段的传输类型。数值查询参数沿用 Spring 转换，分页默认值与夹取范围保留在原入口。无显式传输字段仅指未发现应用声明的路径、查询或正文字段，鉴权、条件请求及请求头仍按原入口处理。

## 需关注的接口

- 系统参数历史原先缺少详情接口已有的参数键规则。已补齐历史入口的 key 及企业路由变量。统计查询的时间 Style 原先只检查长度，已改为同时检查时间格式，可一次报告 from 与 to 的错误。
- 客户审计查询原先漏接日期解析异常；企业审计事件列表和调用记录列表也绕过了已有的审计时间解析器。这三处已修正。企业审计继续使用可覆盖 HTTP 状态的 IAM_AUDIT_INVALID。
- 角色读取和权限查询都接受路由或资源类别特有的筛选项。接口目录还直接从 servlet 读取五个筛选参数，导航直接读取 clientCode；仅根据方法参数生成注解会拒绝合法调用。
- BFF 回调会在错误分支清理状态 Cookie，密钥轮换要记录失败审计；这两处的错误处理顺序属于现有行为。字典选项、菜单与角色授权等接口还包含标量数组，FieldList 只支持对象数组。

## 验证范围

通过 Java 语法树逐一比对迁移前后的 283 个方法，核验了类级映射、方法映射、参数和方法体。新增声明的方法与原清单的 153 个标量候选一致。

兼容性回归覆盖分页空值和前导零、JSON 字段类型错误汇总、未知查询参数、Spring Security 表单 CSRF 参数，以及历史策略请求字段 `allow`。策略决定仍由服务端计算。

隔离构建使用独立 Redis，并在测试进程中将依赖读取超时设为 5 秒。JUnit 报告记录 `summer-mvc` 全部 12 个测试类、89 项通过，`summer-sample` 选取的 28 个回归测试类、204 项通过。合计 293 项，失败、错误和跳过均为 0。

## 逐方法清单

### AgentController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/agent/AgentController.java) · 11 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/enterprises/{enterpriseId}/agents` | — | 已声明 | enterpriseId |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/agents` | 正文 AgentCreateRequest | 已声明 | enterpriseId、name、code、description、ownerMemberId、runtimeType |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/agents/{agentId}` | — | 已声明 | enterpriseId、agentId |
| `update` | `PATCH /api/v1/enterprises/{enterpriseId}/agents/{agentId}` | 正文 AgentUpdateRequest | 已声明 | enterpriseId、agentId、name、description、ownerMemberId、runtimeType |
| `enable` | `POST /api/v1/enterprises/{enterpriseId}/agents/{agentId}:enable` | — | 已声明 | enterpriseId、agentId |
| `disable` | `POST /api/v1/enterprises/{enterpriseId}/agents/{agentId}:disable` | — | 已声明 | enterpriseId、agentId |
| `instances` | `GET /api/v1/enterprises/{enterpriseId}/agents/{agentId}/instances` | — | 已声明 | enterpriseId、agentId |
| `register` | `POST /internal/v1/agent-instances` | 正文 AgentInstanceRegisterRequest | 已声明 | agentId、runtimeId、workloadIdentity、attestationType、attestationDigest、environment、expiresInSeconds |
| `heartbeat` | `POST /internal/v1/agent-instances/{instanceId}:heartbeat` | 正文 AgentInstanceHeartbeatRequest | 已声明 | instanceId、extendSeconds |
| `revoke` | `POST /api/v1/enterprises/{enterpriseId}/agent-instances/{instanceId}:revoke` | — | 已声明 | enterpriseId、instanceId |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/agents/{agentId}/audit` | — | 已声明 | enterpriseId、agentId |

### ApplicationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/application/ApplicationController.java) · 12 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/enterprises/{enterpriseId}/applications` | — | 已声明 | enterpriseId |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/applications` | 正文 ApplicationCreateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/applications/{id}` | — | 已声明 | enterpriseId、id |
| `update` | `PATCH /api/v1/enterprises/{enterpriseId}/applications/{id}` | 正文 ApplicationUpdateRequest | 已声明 | enterpriseId、id、name、description |
| `enable` | `POST /api/v1/enterprises/{enterpriseId}/applications/{id}:enable` | — | 已声明 | enterpriseId、id |
| `disable` | `POST /api/v1/enterprises/{enterpriseId}/applications/{id}:disable` | — | 已声明 | enterpriseId、id |
| `oauth` | `GET /api/v1/enterprises/{enterpriseId}/applications/{id}/oauth-config` | — | 已声明 | enterpriseId、id |
| `config` | `PUT /api/v1/enterprises/{enterpriseId}/applications/{id}/oauth-config` | 正文 OAuthConfigRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `credentials` | `GET /api/v1/enterprises/{enterpriseId}/applications/{id}/credentials` | — | 已声明 | enterpriseId、id |
| `rotate` | `POST /api/v1/enterprises/{enterpriseId}/applications/{id}/credentials:rotate` | — | 已声明 | enterpriseId、id |
| `revoke` | `POST /api/v1/enterprises/{enterpriseId}/applications/{id}/credentials/{cid}:revoke` | — | 已声明 | enterpriseId、id、cid |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/applications/{id}/audit` | — | 已声明 | enterpriseId、id |

### AuditLedgerController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/auditledger/AuditLedgerController.java) · 11 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `append` | `POST /internal/v1/audit-events` | 正文 AuditEventAppendRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |
| `complete` | `POST /internal/v1/mcp/invocations/{invocationId}:complete` | 正文 CompletionRequest | 已声明 | invocationId、permitId、permitJti、executionAttemptId、resultStatus、resultDigest、remoteRequestId、latencyMs、connectionSessionRef、detail |
| `reconcile` | `POST /internal/v1/mcp/invocations/{invocationId}:reconcile` | 正文 ReconciliationRequest | 已声明 | invocationId、executionAttemptId、resultStatus、resultDigest、remoteRequestId、latencyMs |
| `markUnknown` | `POST /api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}:mark-unknown` | — | 已声明 | enterpriseId、invocationId |
| `events` | `GET /api/v1/enterprises/{enterpriseId}/audit-events` | 查询 initiator,actor,effectiveSubject,agentInstanceId,toolId,decision,resultStatus,invocationId,eventType,from,to | 需保留兼容行为 | 已复用审计时间解析器，保留 IAM_AUDIT_INVALID 与可配置 HTTP 状态。 |
| `query` | `GET /api/v1/enterprises/{enterpriseId}/audit-events:query` | 查询 page,size,initiator,actor,effectiveSubject,agentInstanceId,toolId,decision,resultStatus,invocationId,eventType,requestId,traceId,from,to | 需保留兼容行为 | 非法分页和时间使用 IAM_AUDIT_INVALID，测试覆盖自定义 422。 |
| `export` | `GET /api/v1/enterprises/{enterpriseId}/audit-events:export` | 查询 initiator,actor,effectiveSubject,agentInstanceId,toolId,decision,resultStatus,invocationId,eventType,requestId,traceId,from,to | 需保留兼容行为 | 成功返回 CSV，非法时间使用审计领域码，需核对两种响应。 |
| `event` | `GET /api/v1/enterprises/{enterpriseId}/audit-events/{eventId}` | — | 已声明 | enterpriseId、eventId |
| `invocations` | `GET /api/v1/enterprises/{enterpriseId}/mcp-invocations` | 查询 initiator,actor,effectiveSubject,agentInstanceId,toolId,decision,resultStatus,invocationId,from,to | 需保留兼容行为 | 已复用审计时间解析器，保留 IAM_AUDIT_INVALID 与可配置 HTTP 状态。 |
| `invocation` | `GET /api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}` | — | 已声明 | enterpriseId、invocationId |
| `anomalies` | `GET /api/v1/enterprises/{enterpriseId}/audit-anomalies` | — | 已声明 | enterpriseId |

### InterfacePermissionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/InterfacePermissionController.java) · 3 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `directory` | `GET /api/v1/platform/authorization/interface-directory` | 查询 page,size | 已声明 | page、size |
| `read` | `GET /api/v1/platform/permissions/{permissionId}/interfaces` | — | 已声明 | permissionId |
| `replace` | `PUT /api/v1/platform/permissions/{permissionId}/interfaces` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### MenuDirectoryController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/MenuDirectoryController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `directory` | `GET /api/v1/platform/authorization/menu-directory`<br>`GET /api/v1/platform/authorization/menu-layout` | — | 无显式传输字段 | — |
| `manifest` | `GET /api/v1/platform/authorization/menu-manifest` | — | 无显式传输字段 | — |
| `publish` | `PUT /api/v1/platform/authorization/menu-manifest` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `layout` | `PATCH /api/v1/platform/authorization/menu-layout` | 查询 menuId<br>正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### MenuPermissionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/MenuPermissionController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `read` | `GET /api/v1/platform/permissions/{permissionId}/menus` | — | 已声明 | permissionId |
| `replace` | `PUT /api/v1/platform/permissions/{permissionId}/menus` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### PermissionAccessController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PermissionAccessController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `status` | `GET /api/v1/enterprises/{enterpriseId}/authorization/status`<br>`GET /api/v1/platform/authorization/status` | — | 已声明 | enterpriseId |
| `profile` | `GET /api/v1/enterprises/{enterpriseId}/authorization/profile`<br>`GET /api/v1/platform/authorization/profile` | — | 已声明 | enterpriseId |
| `access` | `GET /api/v1/enterprises/{enterpriseId}/authorization/access`<br>`GET /api/v1/platform/authorization/access` | — | 已声明 | enterpriseId |
| `navigation` | `GET /api/v1/enterprises/{enterpriseId}/authorization/navigation`<br>`GET /api/v1/platform/authorization/navigation` | 查询 clientCode | 需保留兼容行为 | servlet 直接取 clientCode；平台和企业别名共用签名，企业路径另含 enterpriseId。 |
| `operations` | `GET /api/v1/enterprises/{enterpriseId}/authorization/operations`<br>`GET /api/v1/platform/authorization/operations` | 查询 actions | 需保留兼容行为 | actions 是可重复的字符串查询参数，须保持 Spring 列表绑定。 |

### PermissionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PermissionController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/platform/permission-groups`<br>`GET /api/v1/platform/permissions` | 查询 page,size,name,type,status,accessMode,groupId,includeDescendants | 需保留兼容行为 | 服务层已验证键白名单；page 从 1 开始，size 上限 200。 |
| `get` | `GET /api/v1/platform/permission-groups/{id}`<br>`GET /api/v1/platform/permissions/{id}` | — | 已声明 | id |
| `create` | `POST /api/v1/platform/permission-groups`<br>`POST /api/v1/platform/permissions` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `patch` | `PATCH /api/v1/platform/permission-groups/{id}`<br>`PATCH /api/v1/platform/permissions/{id}` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `delete` | `DELETE /api/v1/platform/permission-groups/{id}`<br>`DELETE /api/v1/platform/permissions/{id}` | — | 已声明 | id |

### PermissionDataController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PermissionDataController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/enterprises/{enterpriseId}/data-records/{resource}`<br>`GET /api/v1/platform/data-records/{resource}` | 查询 page,size | 已声明 | enterpriseId、resource、page、size |
| `jpa` | `GET /api/v1/enterprises/{enterpriseId}/data-records/{resource}/jpa`<br>`GET /api/v1/platform/data-records/{resource}/jpa` | 查询 page,size | 已声明 | enterpriseId、resource、page、size |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/data-records/{resource}/{id}`<br>`GET /api/v1/platform/data-records/{resource}/{id}` | — | 已声明 | enterpriseId、resource、id |
| `preview` | `POST /api/v1/enterprises/{enterpriseId}/data-resources/{resource}/preview`<br>`POST /api/v1/platform/data-resources/{resource}/preview` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### PermissionDataOperationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PermissionDataOperationController.java) · 8 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `create` | `POST /api/v1/enterprises/{enterpriseId}/data-records/{resource}`<br>`POST /api/v1/platform/data-records/{resource}` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `update` | `PATCH /api/v1/enterprises/{enterpriseId}/data-records/{resource}/{id}`<br>`PATCH /api/v1/platform/data-records/{resource}/{id}` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `delete` | `DELETE /api/v1/enterprises/{enterpriseId}/data-records/{resource}/{id}`<br>`DELETE /api/v1/platform/data-records/{resource}/{id}` | 查询 version | 已声明 | enterpriseId、resource、id、version |
| `batchUpdate` | `POST /api/v1/enterprises/{enterpriseId}/data-records/{resource}/batch-update`<br>`POST /api/v1/platform/data-records/{resource}/batch-update` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `batchDelete` | `POST /api/v1/enterprises/{enterpriseId}/data-records/{resource}/batch-delete`<br>`POST /api/v1/platform/data-records/{resource}/batch-delete` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `batchRead` | `POST /api/v1/enterprises/{enterpriseId}/data-records/{resource}/batch-read`<br>`POST /api/v1/platform/data-records/{resource}/batch-read` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `cached` | `GET /api/v1/enterprises/{enterpriseId}/data-records/{resource}/{id}/cached`<br>`GET /api/v1/platform/data-records/{resource}/{id}/cached` | — | 已声明 | enterpriseId、resource、id |
| `export` | `GET /api/v1/enterprises/{enterpriseId}/data-records/{resource}/export`<br>`GET /api/v1/platform/data-records/{resource}/export` | 查询 page,size | 已声明 | enterpriseId、resource、page、size |

### PermissionDataScopeController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PermissionDataScopeController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `get` | `GET /api/v1/platform/permissions/{id}/data-scope` | — | 已声明 | id |
| `replace` | `PUT /api/v1/platform/permissions/{id}/data-scope` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### PlatformRoleDeletionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/PlatformRoleDeletionController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `delete` | `DELETE /api/v1/platform/roles/{id}` | — | 已声明 | id |
| `deleteTenantRole` | `DELETE /api/v1/enterprises/{enterpriseId}/roles/{id}` | — | 已声明 | enterpriseId、id |

### RolePermissionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/api/permission/RolePermissionController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `read` | `GET /api/v1/enterprises/{enterpriseId}/roles`<br>`GET /api/v1/enterprises/{enterpriseId}/roles/{id}`<br>`GET /api/v1/enterprises/{enterpriseId}/roles/{id}/permissions`<br>`GET /api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities`<br>`GET /api/v1/enterprises/{enterpriseId}/users/{username}/roles`<br>`GET /api/v1/platform/roles`<br>`GET /api/v1/platform/roles/{id}`<br>`GET /api/v1/platform/roles/{id}/permissions`<br>`GET /api/v1/platform/roles/{id}/capabilities`<br>`GET /api/v1/platform/users/{username}/roles` | 查询 catalog / 查询 Map | 需保留兼容行为 | 同一处理方法按路径分派，catalog 与角色分页从 servlet 参数表提取，服务层另拒绝重复键。 |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/roles`<br>`POST /api/v1/platform/roles` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `patch` | `PATCH /api/v1/enterprises/{enterpriseId}/roles/{id}`<br>`PATCH /api/v1/platform/roles/{id}` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |
| `replace` | `PUT /api/v1/enterprises/{enterpriseId}/roles/{id}/permissions`<br>`PUT /api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities`<br>`PUT /api/v1/enterprises/{enterpriseId}/users/{username}/roles`<br>`PUT /api/v1/platform/roles/{id}/permissions`<br>`PUT /api/v1/platform/roles/{id}/capabilities`<br>`PUT /api/v1/platform/users/{username}/roles` | 正文 Map | 需核对正文契约 | 请求体是 Map，需按操作核对允许键、动态值及错误码。 |

### AuthorizationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationController.java) · 14 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `capabilities` | `GET /api/v1/enterprises/{enterpriseId}/capabilities/catalog` | — | 已声明 | enterpriseId |
| `platformCapabilities` | `GET /api/v1/platform/capabilities/catalog` | — | 无显式传输字段 | — |
| `createPlatformCapability` | `POST /api/v1/platform/capabilities` | 正文 CapabilityCreateRequest | 已声明 | code、resource、action、riskLevel、scope |
| `createCapability` | `POST /api/v1/enterprises/{enterpriseId}/capabilities` | 正文 CapabilityCreateRequest | 已声明 | enterpriseId、code、resource、action、riskLevel、scope |
| `disable` | `POST /api/v1/enterprises/{enterpriseId}/capabilities/{id}:disable` | — | 已声明 | enterpriseId、id |
| `enable` | `POST /api/v1/enterprises/{enterpriseId}/capabilities/{id}:enable` | — | 已声明 | enterpriseId、id |
| `updateCapability` | `PATCH /api/v1/enterprises/{enterpriseId}/capabilities/{id}` | 正文 CapabilityStatusRequest | 已声明 | enterpriseId、id、status |
| `bundles` | `GET /api/v1/enterprises/{enterpriseId}/capability-bundles` | — | 已声明 | enterpriseId |
| `bundle` | `GET /api/v1/enterprises/{enterpriseId}/capability-bundles/{id}` | — | 已声明 | enterpriseId、id |
| `bundleVersions` | `GET /api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/versions` | — | 已声明 | enterpriseId、id |
| `createBundle` | `POST /api/v1/enterprises/{enterpriseId}/capability-bundles` | 正文 BundleCreateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `updateBundle` | `PATCH /api/v1/enterprises/{enterpriseId}/capability-bundles/{id}` | 正文 BundleUpdateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `updateBundleCapabilities` | `PUT /api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/capabilities` | 正文 BundleUpdateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/authorization-audit/{id}` | — | 已声明 | enterpriseId、id |

### OAuthClientManagementController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/OAuthClientManagementController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `listClients` | `GET /api/security/oauth-clients` | — | 无显式传输字段 | — |
| `create` | `POST /api/security/oauth-clients` | 正文 ClientRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `changeStatus` | `PATCH /api/security/oauth-clients/{clientId}` | 正文 UpdateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `listConsents` | `GET /api/security/oauth-consents` | — | 无显式传输字段 | — |
| `revokeConsent` | `DELETE /api/security/oauth-consents/{clientId}/{principalName}` | — | 已声明 | clientId、principalName |

### PolicyController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/policy/PolicyController.java) · 16 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/enterprises/{enterpriseId}/policies` | — | 已声明 | enterpriseId |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/policies` | 正文 PolicyCreateRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}` | — | 已声明 | enterpriseId、policyId |
| `versions` | `GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}/versions` | — | 已声明 | enterpriseId、policyId |
| `updateDraft` | `PATCH /api/v1/enterprises/{enterpriseId}/policies/{policyId}/draft` | 正文 PolicyDraftUpdateRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `validate` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:validate` | — | 已声明 | enterpriseId、policyId |
| `publish` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:publish` | — | 已声明 | enterpriseId、policyId |
| `pause` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:pause` | — | 已声明 | enterpriseId、policyId |
| `revoke` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:revoke` | — | 已声明 | enterpriseId、policyId |
| `rollback` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:rollback` | 正文 PolicyRollbackRequest | 已声明 | enterpriseId、policyId、versionNo |
| `copy` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:copy` | 正文 PolicyCreateRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `testCases` | `GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}/test-cases` | — | 已声明 | enterpriseId、policyId |
| `createTestCase` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}/test-cases` | 正文 PolicyTestCaseCreateRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |
| `runTests` | `POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:test` | — | 已声明 | enterpriseId、policyId |
| `evaluate` | `POST /internal/v1/authorization/evaluate` | 正文 PolicyEvaluationRequest | 已声明 | initiator、actor、effectiveSubject、enterpriseId、resource、action、risk、organizationId、requestId、allow（兼容旧客户端，决策由服务端计算） |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}/audit` | — | 已声明 | enterpriseId、policyId |

### BffSessionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/bff/BffSessionController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `start` | `GET /bff/auth/start` | — | 协议入口 | BFF 会话协议，保留 Cookie、重定向及失败响应。 |
| `callback` | `GET /bff/auth/callback` | 查询 code,state | 协议入口 | BFF 会话协议，保留 Cookie、重定向及失败响应。 |
| `logout` | `DELETE /bff/session` | — | 协议入口 | BFF 会话协议，保留 Cookie、重定向及失败响应。 |
| `invokeMcp` | `POST /bff/api/v1/enterprises/{enterpriseId}/mcp/invoke` | 正文 BffMcpInvokeRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |

### ConfigurationOverrideController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/configuration/ConfigurationOverrideController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `parameterVersions` | `GET /api/system-parameters/{key}/overrides/{organization}/versions`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/versions` | — | 已声明 | key、organization、enterpriseId |
| `parameterDraft` | `POST /api/system-parameters/{key}/overrides/{organization}/draft`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/draft` | 正文 ConfigurationOverrideRequest | 需核对正文契约 | 字段类型由业务解释，需核对兼容类型和并发语义。 |
| `parameterPublish` | `POST /api/system-parameters/{key}/overrides/{organization}/publish`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/publish` | 正文 ConfigurationOverrideCommand | 已声明 | key、organization、enterpriseId、revision、expectedVersion |
| `parameterDisable` | `POST /api/system-parameters/{key}/overrides/{organization}/disable`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/disable` | 正文 ConfigurationOverrideCommand | 已声明 | key、organization、enterpriseId、revision、expectedVersion |
| `parameterRollback` | `POST /api/system-parameters/{key}/overrides/{organization}/rollback`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/rollback` | 正文 ConfigurationOverrideCommand | 已声明 | key、organization、enterpriseId、revision、expectedVersion |

### ConnectionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/connection/ConnectionController.java) · 9 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/platform/connection-services` | — | 无显式传输字段 | — |
| `create` | `POST /api/v1/platform/connection-services` | 正文 ConnectionServiceCreateRequest | 已声明 | serviceCode、name、permitAudience、protocolVersion |
| `status` | `PATCH /api/v1/platform/connection-services/{serviceId}` | 查询 status | 已声明 | serviceId、status |
| `catalog` | `PUT /internal/v1/connection-services/{serviceId}/connections/{connectionId}/catalog` | 正文 CatalogSyncRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `bindings` | `GET /api/v1/enterprises/{enterpriseId}/mcp-connections` | — | 已声明 | enterpriseId |
| `createBinding` | `POST /api/v1/enterprises/{enterpriseId}/mcp-connections` | 正文 McpConnectionCreateRequest | 已声明 | enterpriseId、connectionServiceId、externalConnectionId |
| `binding` | `GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}` | — | 已声明 | enterpriseId、connectionId |
| `tools` | `GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/tools` | — | 已声明 | enterpriseId、connectionId |
| `health` | `GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/health` | — | 已声明 | enterpriseId、connectionId |

### CustomerAuditController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerAuditController.java) · 3 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `search` | `GET /api/audit-events` | 查询 page,size,principal,resource,action,result,requestId,tenantId,from,to | 需保留兼容行为 | 已修复非法时间解析；分页默认 0/20，size 1–100，跨租户返回 403。 |
| `detail` | `GET /api/audit-events/{eventId}` | — | 已声明 | eventId |
| `export` | `GET /api/audit-events/export` | 查询 principal,resource,action,result,requestId,tenantId,from,to | 需保留兼容行为 | 成功返回 CSV；筛选与客户审计查询共用语义，租户拒绝为 403。 |

### CustomerController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/customers` | 查询 page,size,active,sort,direction,projection | 已声明 | page、size、active、sort、direction、projection |
| `find` | `GET /api/customers/{username}` | — | 已声明 | username |
| `create` | `POST /api/customers` | 正文 CreateCustomerRequest | 已声明 | username、email、displayName |
| `update` | `PATCH /api/customers/{username}` | 正文 UpdateCustomerRequest | 已声明 | username、email、displayName、active |
| `delete` | `DELETE /api/customers/{username}` | — | 已声明 | username |

### DelegationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/delegation/DelegationController.java) · 9 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `create` | `POST /api/v1/enterprises/{e}/agents/{a}/delegations` | 正文 DelegationCreateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `list` | `GET /api/v1/enterprises/{e}/agents/{a}/delegations` | — | 已声明 | e、a |
| `revoke` | `POST /api/v1/enterprises/{e}/delegations/{id}:revoke` | — | 已声明 | e、id |
| `approval` | `POST /internal/v1/mcp/approvals` | 正文 ApprovalCreateRequest | 已声明 | enterpriseId、agentId、agentInstanceId、mcpServerId、toolId、requestDigest、approvalScope、expiresInSeconds、approverPrincipalId |
| `approve` | `POST /api/v1/mcp/approvals/{id}:approve` | — | 已声明 | id |
| `reject` | `POST /api/v1/mcp/approvals/{id}:reject` | — | 已声明 | id |
| `withdraw` | `POST /api/v1/mcp/approvals/{id}:withdraw` | — | 已声明 | id |
| `recheck` | `POST /api/v1/mcp/approvals/{id}:recheck` | — | 已声明 | id |
| `audit` | `GET /api/v1/mcp/approvals/{id}/audit` | — | 已声明 | id |

### AdministrativeDivisionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/AdministrativeDivisionController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `children` | `GET /api/v1/platform/administrative-divisions` | 查询 parentId | 已声明 | parentId |
| `create` | `POST /api/v1/platform/administrative-divisions` | 正文 Create | 已声明 | code、name、level、parentId |
| `rename` | `PUT /api/v1/platform/administrative-divisions/{id}` | 正文 Rename | 已声明 | id、name、revision |
| `delete` | `DELETE /api/v1/platform/administrative-divisions/{id}` | 查询 revision | 已声明 | id、revision |

### DictionaryController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryController.java) · 17 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/dictionaries`<br>`GET /api/v1/platform/dictionaries` | — | 已声明 | 空字段契约 |
| `resolveNames` | `POST /api/dictionaries/{code}/resolve-names`<br>`POST /api/v1/platform/dictionaries/{code}/resolve-names` | 正文 DictionaryNameRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `resolveSelection` | `POST /api/dictionaries/{code}/resolve-selection`<br>`POST /api/v1/platform/dictionaries/{code}/resolve-selection` | 正文 DictionaryNameRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `effective` | `GET /api/dictionaries/{code}/effective`<br>`GET /api/v1/platform/dictionaries/{code}/effective` | — | 已声明 | code |
| `draft` | `GET /api/dictionaries/{code}/draft`<br>`GET /api/v1/platform/dictionaries/{code}/draft` | — | 已声明 | code |
| `saveDraft` | `PUT /api/dictionaries/{code}/draft`<br>`PUT /api/v1/platform/dictionaries/{code}/draft` | 正文 DictionaryDraftRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `create` | `POST /api/dictionaries`<br>`POST /api/v1/platform/dictionaries` | 正文 DictionaryTypeRequest | 已声明 | code、name、organizationId、published、publicReadable、enabled |
| `update` | `PATCH /api/dictionaries/{code}`<br>`PATCH /api/v1/platform/dictionaries/{code}` | 正文 DictionaryTypeRequest | 需核对正文契约 | 字段类型由业务解释，需核对兼容类型和并发语义。 |
| `createItem` | `POST /api/dictionaries/{code}/items`<br>`POST /api/v1/platform/dictionaries/{code}/items` | 正文 DictionaryItemRequest | 需核对正文契约 | 字段类型由业务解释，需核对兼容类型和并发语义。 |
| `updateItem` | `PATCH /api/dictionaries/{code}/items/{value}`<br>`PATCH /api/v1/platform/dictionaries/{code}/items/{value}` | 正文 DictionaryItemRequest | 需核对正文契约 | 字段类型由业务解释，需核对兼容类型和并发语义。 |
| `publish` | `POST /api/dictionaries/{code}/publish`<br>`POST /api/v1/platform/dictionaries/{code}/publish`<br>`POST /api/dictionaries/{code}/versions/publish`<br>`POST /api/v1/platform/dictionaries/{code}/versions/publish` | 正文 DictionaryVersionRequest | 需保留兼容行为 | 版本 DTO 接受 expectedVersion / version / revision 等 setter 别名；只能在保留优先级与版本冲突响应后迁移。 |
| `disable` | `POST /api/dictionaries/{code}/disable`<br>`POST /api/v1/platform/dictionaries/{code}/disable`<br>`POST /api/dictionaries/{code}/deactivate`<br>`POST /api/v1/platform/dictionaries/{code}/deactivate`<br>`POST /api/dictionaries/{code}/versions/disable`<br>`POST /api/v1/platform/dictionaries/{code}/versions/disable`<br>`POST /api/dictionaries/{code}/versions/deactivate`<br>`POST /api/v1/platform/dictionaries/{code}/versions/deactivate` | 正文 DictionaryVersionRequest | 需保留兼容行为 | 版本 DTO 接受 expectedVersion / version / revision 等 setter 别名；只能在保留优先级与版本冲突响应后迁移。 |
| `rollback` | `POST /api/dictionaries/{code}/rollback`<br>`POST /api/v1/platform/dictionaries/{code}/rollback`<br>`POST /api/dictionaries/{code}/versions/rollback`<br>`POST /api/v1/platform/dictionaries/{code}/versions/rollback` | 正文 DictionaryVersionRequest | 需保留兼容行为 | 版本 DTO 接受 expectedVersion / version / revision 等 setter 别名；只能在保留优先级与版本冲突响应后迁移。 |
| `versions` | `GET /api/dictionaries/{code}/versions`<br>`GET /api/v1/platform/dictionaries/{code}/versions` | — | 已声明 | code |
| `history` | `GET /api/dictionaries/{code}/versions/{version}`<br>`GET /api/v1/platform/dictionaries/{code}/versions/{version}` | — | 已声明 | code、version |
| `publicRead` | `GET /public/dictionaries/{code}`<br>`GET /public/enterprises/{enterpriseId}/dictionaries/{code}` | — | 需保留兼容行为 | 两个公开路径共用处理方法，需保留未发布或不可读时的响应。 |
| `publicItem` | `GET /public/dictionaries/{code}/items/{value}`<br>`GET /public/enterprises/{enterpriseId}/dictionaries/{code}/items/{value}` | — | 需保留兼容行为 | 先判断已发布及可读性，再验证选项值；提前拒绝会改变既有错误优先级。 |

### DictionaryNotificationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/DictionaryNotificationController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `subscribe` | `GET /api/v1/platform/dictionaries/{code}/events` | — | 已声明 | 路径编码已校验；Last-Event-ID 在 SSE 层解析。 |

### EnterpriseDirectoryController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/enterprise/EnterpriseDirectoryController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `create` | `POST /api/v1/platform/enterprises` | 正文 CreateEnterpriseRequest | 已声明 | name、code |
| `audit` | `GET /api/v1/platform/enterprises/{enterpriseId}/audit` | — | 已声明 | enterpriseId |

### EnterpriseReviewController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/enterprise/EnterpriseReviewController.java) · 6 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/platform/enterprise-reviews` | — | 无显式传输字段 | — |
| `detail` | `GET /api/v1/platform/enterprise-reviews/{id}` | — | 已声明 | id |
| `audit` | `GET /api/v1/platform/enterprise-reviews/{id}/audit` | — | 已声明 | id |
| `approve` | `POST /api/v1/platform/enterprise-reviews/{id}:approve` | — | 已声明 | id |
| `reject` | `POST /api/v1/platform/enterprise-reviews/{id}:reject` | 正文 ReviewRequest | 已声明 | id、reason |
| `requestInfo` | `POST /api/v1/platform/enterprise-reviews/{id}:request-info` | 正文 ReviewRequest | 已声明 | id、reason |

### PlatformEnterpriseDirectoryController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/enterprise/PlatformEnterpriseDirectoryController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/platform/enterprises` | 查询 search,status,qualification | 已声明 | search、status、qualification |
| `transition` | `POST /api/v1/platform/enterprises/{id}:freeze`<br>`POST /api/v1/platform/enterprises/{id}:disable`<br>`POST /api/v1/platform/enterprises/{id}:reject` | — | 已声明 | id |

### AuthUserController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/AuthUserController.java) · 3 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/users` | — | 无显式传输字段 | — |
| `create` | `POST /api/users` | 正文 CreateUserRequest | 需保留兼容行为 | 密码属于敏感输入，DTO 已有长度约束；role 缺省、null 或空白会归一为 USER。 |
| `active` | `PATCH /api/users/{username}` | 正文 ActiveUserRequest | 需保留兼容行为 | DTO 用 primitive boolean 接收 active。迁移时应验证缺省、null、显式 false 和正文绑定，避免意外状态变更。 |

### CapabilityController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/CapabilityController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `platform` | `GET /api/v1/platform/capabilities` | — | 无显式传输字段 | — |
| `enterprise` | `GET /api/v1/enterprises/{enterpriseId}/capabilities` | — | 已声明 | enterpriseId |

### SessionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/identity/SessionController.java) · 4 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `session` | `GET /api/v1/session` | — | 无显式传输字段 | — |
| `sessions` | `GET /api/v1/sessions` | 查询 principal | 已声明 | principal |
| `revoke` | `DELETE /api/v1/sessions/{reference}` | — | 已声明 | reference |
| `logout` | `DELETE /api/v1/session` | — | 无显式传输字段 | — |

### InvitationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/invitation/InvitationController.java) · 15 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `create` | `POST /api/v1/enterprises/{enterpriseId}/invitations` | 正文 InvitationCreateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `get` | `GET /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}` | — | 已声明 | enterpriseId、invitationId |
| `list` | `GET /api/v1/enterprises/{enterpriseId}/invitations` | 查询 search,status,page,pageSize | 已声明 | enterpriseId、search、status、page、pageSize |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}/audit` | — | 已声明 | enterpriseId、invitationId |
| `resend` | `POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend` | — | 已声明 | enterpriseId、invitationId |
| `revoke` | `POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:revoke` | — | 已声明 | enterpriseId、invitationId |
| `accept` | `POST /api/v1/invitations/{token}:accept` | 正文 InvitationContextRequest | 已声明 | token、enterpriseId |
| `rejectToken` | `POST /api/v1/invitations/{token}:reject` | 正文 InvitationContextRequest | 已声明 | token、enterpriseId |
| `submit` | `POST /api/v1/enterprises/{enterpriseId}/join-applications` | 正文 JoinApplicationSubmitRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `supplement` | `PATCH /api/v1/enterprises/{enterpriseId}/join-applications/{id}` | 正文 JoinApplicationSupplementRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `approve` | `POST /api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve` | — | 已声明 | enterpriseId、id |
| `reject` | `POST /api/v1/enterprises/{enterpriseId}/join-applications/{id}:reject` | — | 已声明 | enterpriseId、id |
| `withdraw` | `POST /api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw` | — | 已声明 | enterpriseId、id |
| `appAudit` | `GET /api/v1/enterprises/{enterpriseId}/join-applications/{id}/audit` | — | 已声明 | enterpriseId、id |
| `applications` | `GET /api/v1/enterprises/{enterpriseId}/join-applications` | 查询 search,status,page,pageSize | 已声明 | enterpriseId、search、status、page、pageSize |

### KeyCapabilityController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/KeyCapabilityController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/security/keys` | — | 无显式传输字段 | — |
| `rotate` | `POST /api/security/keys:rotate` | 正文 RotationRequest | 需保留兼容行为 | 非法输入需要密钥轮换失败审计及专用错误码；提前拦截会跳过现有路径。 |

### ReferenceKeyCapabilityController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/key/ReferenceKeyCapabilityController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/security/keys` | — | 无显式传输字段 | — |

### McpAuthorizationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/mcpauthorization/McpAuthorizationController.java) · 3 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `decide` | `POST /internal/v1/mcp/execution-decisions` | 正文 ExecutionDecisionRequest | 已声明 | enterpriseId、connectionServiceId、connectionBindingId、externalConnectionId、connectionRevision、executionContextId、actorPrincipalId、actorInstanceId、effectiveSubjectPrincipalId、toolId、toolName、schemaDigest、argumentsDigest、audience、risk、allow、approvalId |
| `claim` | `POST /internal/v1/mcp/execution-permits/{id}:claim` | 正文 PermitClaimRequest | 已声明 | id、executionAttemptId |
| `revoke` | `POST /api/v1/enterprises/{e}/mcp-execution-permits/{id}:revoke` | — | 已声明 | e、id |

### MemberController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/member/MemberController.java) · 8 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `create` | `POST /api/v1/enterprises/{enterpriseId}/members` | 正文 MemberCreateRequest | 已声明 | enterpriseId、subject、displayName、email |
| `list` | `GET /api/v1/enterprises/{enterpriseId}/members` | 查询 page,size,pageSize,q,search,status | 需保留兼容行为 | size 与 pageSize、q 与 search 有取值优先级；校验须保留别名行为及目录错误码。 |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/members/{memberId}` | — | 已声明 | enterpriseId、memberId |
| `disable` | `POST /api/v1/enterprises/{enterpriseId}/members/{memberId}:disable` | — | 已声明 | enterpriseId、memberId |
| `enable` | `POST /api/v1/enterprises/{enterpriseId}/members/{memberId}:enable` | — | 已声明 | enterpriseId、memberId |
| `leave` | `POST /api/v1/enterprises/{enterpriseId}/members/{memberId}:leave` | — | 已声明 | enterpriseId、memberId |
| `resign` | `POST /api/v1/enterprises/{enterpriseId}/members/{memberId}:resign` | — | 已声明 | enterpriseId、memberId |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/members/{memberId}/audit` | — | 已声明 | enterpriseId、memberId |

### MenuManifestController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/menu/MenuManifestController.java) · 3 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `current` | `GET /api/v1/platform/menu-manifest` | — | 无显式传输字段 | — |
| `sync` | `PUT /api/v1/platform/menu-manifest` | 正文 MenuManifestSyncRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `visible` | `GET /api/v1/enterprises/{enterpriseId}/menu-manifest` | — | 已声明 | enterpriseId |

### PublicInfoController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/PublicInfoController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `info` | `GET /public/info`<br>`GET /` | — | 无显式传输字段 | — |

### RedisDiagnosticsController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RedisDiagnosticsController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `redis` | `GET /internal/v1/diagnostics/redis` | — | 无显式传输字段 | — |

### RuntimeDiagnosticsController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/operations/RuntimeDiagnosticsController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `runtime` | `GET /internal/v1/diagnostics/runtime` | — | 无显式传输字段 | — |

### OrganizationController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/organization/OrganizationController.java) · 12 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `tree` | `GET /api/v1/enterprises/{enterpriseId}/org-tree` | — | 已声明 | enterpriseId |
| `list` | `GET /api/v1/enterprises/{enterpriseId}/org-units` | 查询 parentId,q | 已声明 | enterpriseId、parentId、q |
| `detail` | `GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}` | — | 已声明 | enterpriseId、unitId |
| `members` | `GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members` | — | 已声明 | enterpriseId、unitId |
| `audit` | `GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/audit` | — | 已声明 | enterpriseId、unitId |
| `currentContext` | `GET /api/v1/enterprises/{enterpriseId}/context` | — | 已声明 | enterpriseId |
| `context` | `PUT /api/v1/enterprises/{enterpriseId}/context` | 正文 OrganizationContextRequest | 已声明 | enterpriseId、organizationId |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/org-units` | 正文 OrganizationUnitRequest | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `update` | `PATCH /api/v1/enterprises/{enterpriseId}/org-units/{unitId}` | 正文 OrganizationUnitRequest | 需保留兼容行为 | parentId 由 JsonSetter 记录是否出现；缺省与显式 null 对移动节点含义不同。 |
| `delete` | `DELETE /api/v1/enterprises/{enterpriseId}/org-units/{unitId}` | — | 已声明 | enterpriseId、unitId |
| `assignBatch` | `PUT /api/v1/enterprises/{enterpriseId}/org-assignments:batch` | 正文 OrganizationAssignmentsRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `assignMember` | `PUT /api/v1/enterprises/{enterpriseId}/members/{memberId}/org-assignments` | 正文 OrganizationAssignmentsRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |

### PhysicalExecutionController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/physicalconnection/PhysicalExecutionController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `invoke` | `POST /internal/v1/connections/{connectionId}/tools/{tool}:invoke` | 正文 PhysicalInvokeRequest | 已声明 | connectionId、tool、permitId、executionAttemptId、schemaDigest、argumentsDigest、sessionRef、timeoutMs、maxRetries、mcpAccessToken |

### PluginController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/plugin/PluginController.java) · 8 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/platform/plugins` | — | 无显式传输字段 | — |
| `register` | `POST /api/v1/platform/plugins` | 正文 PluginRegisterRequest | 已声明 | pluginId、version、artifactDigest |
| `enable` | `POST /api/v1/platform/plugins/{pluginId}:enable` | — | 已声明 | pluginId |
| `disable` | `POST /api/v1/platform/plugins/{pluginId}:disable` | — | 已声明 | pluginId |
| `upgrade` | `POST /api/v1/platform/plugins/{pluginId}:upgrade` | 正文 PluginUpgradeRequest | 已声明 | pluginId、version、artifactDigest |
| `diagnostic` | `GET /api/v1/platform/plugins/{pluginId}/diagnostic` | — | 已声明 | pluginId |
| `enterpriseList` | `GET /api/v1/enterprises/{enterpriseId}/plugins` | — | 已声明 | enterpriseId |
| `customerInsights` | `GET /api/v1/enterprises/{enterpriseId}/plugins/customer-insights/summary` | — | 已声明 | enterpriseId |

### RealtimeMessageController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/realtime/RealtimeMessageController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `send` | `POST /api/v1/enterprises/{enterpriseId}/messages` | 正文 RealtimeMessageCreateRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |
| `list` | `GET /api/v1/enterprises/{enterpriseId}/messages` | 查询 after | 已声明 | enterpriseId、after |
| `find` | `GET /api/v1/enterprises/{enterpriseId}/messages/{messageId}` | — | 已声明 | enterpriseId、messageId |
| `acknowledge` | `POST /api/v1/enterprises/{enterpriseId}/messages/{messageId}:ack` | — | 已声明 | enterpriseId、messageId |
| `connections` | `GET /api/v1/enterprises/{enterpriseId}/messages/connections` | — | 已声明 | enterpriseId |

### ReleaseGateController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/release/ReleaseGateController.java) · 6 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `budgets` | `GET /internal/v1/release/performance-budgets` | — | 无显式传输字段 | — |
| `readiness` | `GET /internal/v1/release/readiness` | — | 无显式传输字段 | — |
| `benchmark` | `POST /internal/v1/release/performance-benchmarks` | 正文 PerformanceBenchmarkRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `validate` | `POST /internal/v1/release/gates:validate` | 正文 ReleaseGateRequest | 需核对正文契约 | 含标量数组；FieldList 要求元素为对象。 |
| `rollback` | `POST /internal/v1/release/rollback:dry-run` | 正文 RollbackDryRunRequest | 已声明 | releaseVersion、targetVersion、reason |
| `drill` | `POST /internal/v1/release/failure-drills` | 正文 FailureDrillRequest | 已声明 | component、scenario |

### ResilienceDiagnosticsController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resilience/ResilienceDiagnosticsController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `snapshot` | `GET /internal/v1/diagnostics/resilience` | — | 无显式传输字段 | — |

### InterfaceResourceCatalogController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/resource/InterfaceResourceCatalogController.java) · 5 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `read` | `GET /api/v1/platform/interface-resources` | 查询 page,size,revision,refresh,directory,path,policy,serviceName,serviceCode | 需保留兼容行为 | 除了形参，还从请求读取 directory、path、policy、serviceName、serviceCode；目录分页和快照错误由业务错误码区分。 |
| `refresh` | `POST /api/v1/platform/interface-resources/refresh` | 正文 ServiceRefresh | 需保留兼容行为 | serviceCode 优先，缺省时按 serviceName 匹配；服务端还要检查已发布、启用且唯一。 |
| `history` | `GET /api/v1/platform/interface-resources/history` | 查询 page,size | 已声明 | page、size |
| `revision` | `GET /api/v1/platform/interface-resources/history/{revision}` | 查询 page,size | 已声明 | revision、page、size |
| `diff` | `GET /api/v1/platform/interface-resources/diff` | 查询 from,to | 已声明 | from、to |

### StatisticsController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/statistics/StatisticsController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `read` | `GET /api/v1/enterprises/{enterpriseId}/statistics` | 查询 from,to,organizationId | 已声明 | 时间 Style 已校验 ISO-8601 格式，仍由业务层判断起止顺序和跨度。 |

### PublicSystemParameterController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/PublicSystemParameterController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `read` | `GET /public/system-parameters/{key}`<br>`GET /public/enterprises/{enterpriseId}/system-parameters/{key}` | — | 已声明 | enterpriseId、key |

### SystemParameterController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterController.java) · 12 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/system-parameters`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameters` | 查询 page,size,key,type,status,public,sort,direction,projection,tenantId | 已声明 | enterpriseId、page、size、key、type、status、public、sort、direction、projection、tenantId |
| `find` | `GET /api/system-parameters/{key}`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameters/{key}` | — | 已声明 | enterpriseId、key |
| `create` | `POST /api/system-parameters`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters` | 正文 CreateSystemParameterRequest | 需保留兼容行为 | value 按参数类型解释，public 与 publicValue 均为可写属性；须保留取值校验与别名。 |
| `update` | `PATCH /api/system-parameters/{key}`<br>`PATCH /api/v1/enterprises/{enterpriseId}/system-parameters/{key}` | 正文 UpdateSystemParameterRequest | 需保留兼容行为 | value 按参数类型解释，public 与 publicValue 均为可写属性；须保留取值校验与别名。 |
| `enable` | `POST /api/system-parameters/{key}/enable`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/enable` | — | 已声明 | enterpriseId、key |
| `disable` | `POST /api/system-parameters/{key}/disable`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/disable` | — | 已声明 | enterpriseId、key |
| `draft` | `POST /api/system-parameters/{key}/draft`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/draft` | 正文 SystemParameterDraftRequest | 需保留兼容行为 | value 按参数类型解释，public 与 publicValue 均为可写属性；须保留取值校验与别名。 |
| `versions` | `GET /api/system-parameters/{key}/versions`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/versions` | — | 已声明 | 已按详情接口复用 key 与可选 enterpriseId 的 Style。 |
| `publish` | `POST /api/system-parameters/{key}/publish`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/publish` | 正文 SystemParameterReleaseCommand | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `rollback` | `POST /api/system-parameters/{key}/rollback`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/rollback` | 正文 SystemParameterReleaseCommand | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `deactivate` | `POST /api/system-parameters/{key}/deactivate`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/deactivate` | 正文 SystemParameterReleaseCommand | 需核对正文契约 | 需要展开嵌套对象、setter 或别名后再确定完整契约。 |
| `override` | `PUT /api/system-parameters/{key}/overrides/{organization}`<br>`PUT /api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}` | 正文 ConfigurationOverrideRequest | 需核对正文契约 | 字段类型由业务解释，需核对兼容类型和并发语义。 |

### SystemParameterCountController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterCountController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `count` | `GET /api/system-parameters:count`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameters:count` | 查询 page,size,key,type,status,public,sort,direction,projection,tenantId | 已声明 | enterpriseId、page、size、key、type、status、public、sort、direction、projection、tenantId |

### SystemParameterEventController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/systemparameter/SystemParameterEventController.java) · 2 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/system-parameter-events`<br>`GET /api/v1/enterprises/{enterpriseId}/system-parameter-events` | 查询 key | 需保留兼容行为 | 空白 key 与缺省均表示全部事件，不能直接套用必填参数键的格式。 |
| `retry` | `POST /api/system-parameter-events/{eventId}/retry`<br>`POST /api/v1/enterprises/{enterpriseId}/system-parameter-events/{eventId}/retry` | — | 已声明 | enterpriseId、eventId |

### TaskProgressController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskProgressController.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `subscribe` | `GET /api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events` | — | 需保留兼容行为 | SSE 的 Last-Event-ID 为请求头，继续独立处理游标与流响应。 |

### TaskSchedulerController

[源码](../../summer-sample/src/main/java/com/abluepoint/summer/sample/task/TaskSchedulerController.java) · 8 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `list` | `GET /api/v1/enterprises/{enterpriseId}/tasks` | — | 已声明 | enterpriseId |
| `types` | `GET /api/v1/enterprises/{enterpriseId}/tasks/types` | — | 已声明 | enterpriseId |
| `create` | `POST /api/v1/enterprises/{enterpriseId}/tasks` | 正文 TaskCreateRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |
| `update` | `PATCH /api/v1/enterprises/{enterpriseId}/tasks/{taskId}` | 正文 TaskUpdateRequest | 需核对正文契约 | 含动态对象键，声明固定子字段会限制现有载荷。 |
| `pause` | `POST /api/v1/enterprises/{enterpriseId}/tasks/{taskId}:pause` | — | 已声明 | enterpriseId、taskId |
| `resume` | `POST /api/v1/enterprises/{enterpriseId}/tasks/{taskId}:resume` | — | 已声明 | enterpriseId、taskId |
| `trigger` | `POST /api/v1/enterprises/{enterpriseId}/tasks/{taskId}:trigger` | — | 已声明 | enterpriseId、taskId |
| `executions` | `GET /api/v1/enterprises/{enterpriseId}/tasks/{taskId}/executions` | — | 已声明 | enterpriseId、taskId |

### SummerCsrfEndpoint

[源码](../../summer-security-autoconfigure/src/main/java/com/abluepoint/summer/security/autoconfigure/SummerCsrfEndpoint.java) · 1 个映射方法

| 方法 | HTTP 与路径 | 查询参数 / 正文 | 判断 | 说明 |
|---|---|---|---|---|
| `csrf` | `GET /csrf` | — | 协议入口 | 由 Spring Security 提供 CSRF 令牌。 |
