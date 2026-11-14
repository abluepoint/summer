# DATA 权限读取闭环（#205）

本指南保留 #205 读取接入时仅开放部分预设和动作的阶段边界。当前五种预设及组合语义以 [ADR0039](../adr/0039-manage-permissions-independently-of-resources.md) 为准，后续实际写入与导出入口见[#207 操作契约](permission-207-data-operations.md)；下文的“本票不开放”不表示当前实现仍未接入。

管理员在权限管理页选择数据权限、业务资源“申请单”、查询操作及单个“本人”或“当前租户全部”预设。默认仍是授权访问；创建权限不自动授予任何角色。将具体权限授予角色，再通过用户角色关系生效。权限组只作分类。访问方式、状态、组和数据详情可在同一次权限创建或修改中保存。

数据详情也支持所属域下的 `GET/PUT permissions/{id}/data-scope`，PUT 整体替换 `dataResourceKey`、`action`、`scopePreset`。条件版本是权限 ETag；写请求必须提供 `If-Match` 和 `Idempotency-Key`。该入口复用权限聚合已有的域锁、版本、审计、outbox 和 revision 事务；未知字段、任意 SQL、字段名、组织列表及表达式均不接受。

受控示例资源为新建的申请单读取适配器，未把现有 Customer 的创建人猜作所有者：

| 注册项 | 固定语义 |
| --- | --- |
| 资源键 | `applications` |
| 域/租户 | `domain_key` 与 `tenant_id` 同时等于可信请求域及其 ID |
| 本人 | `applicant_id` 等于有效当前主体，代录的 `created_by` 不取得本人归属 |
| 部门 | 登记 `department_id`，本票不开放部门预设；由 #206 接入组织上下文 |
| 业务上限 | `business_visible=true`，全部范围也必须满足 |
| 当前动作 | 仅 `read` 有实际适配；其他动作无法配置、执行或预览为允许 |
| SQL 适配 | 固定表/投影与注册列名；范围参数绑定后用于 count、分页列表和详情 |
| JPA 适配 | 同一不可变 Scope 转 Specification，在数据库分页前执行 |
| 写入/导出 | 没有适配器、没有回退；后续 #207 提供真实执行边界后再登记动作 |

受信任的 `DataResourceRegistry` 是应用代码中的映射，不接收权限表单里的表名、列名或 SQL。`ApplicationQueryService` 明确只执行其申请单适配；未知资源即使未来加入目录，也不能误落到申请单表。新增业务资源必须同时提供可信注册和对应的实际应用适配。

`DataReadService` 先在新的 READ_COMMITTED 只读事务中读当前域版本、活动身份及持久角色与权限详情。相同资源/动作的有效 SELF 和 TENANT_ALL 取并集，再与域、租户和业务上限相交；没有匹配范围为空集。匿名 SELF 为空；企业失效、已登录主体失效或依赖不可用失败关闭。读取完成复核域版本，变更时最多重读一次，不复用旧会话里的业务授权缓存。

HTTP 入口采用企业域 `/api/v1/enterprises/{enterpriseId}` 或平台域 `/api/v1/platform`：

- `GET data-resources`：管理权限下的受控目录。
- `GET data-records/applications?page=1&size=20`：绑定 SQL 列表和总数。
- `GET data-records/applications/jpa`：相同范围的 JPA 分页路径。
- `GET data-records/applications/{id}`：不可见记录返回 404。
- `POST data-resources/applications/preview`：管理权限下，仅接受持久 `subject`、`recordId`、`action`，读取同一范围并返回 allowed 与 revision。主体来自数据库，不接受调用者的 roleAllows、userDenies 或 organizationIds。

业务读取接口许可与 DATA 范围分别配置。只有接口许可时仍返回空集；DATA 范围不补足接口许可。平台域和 ID 恰好为 platform 的企业域不会合并范围。
