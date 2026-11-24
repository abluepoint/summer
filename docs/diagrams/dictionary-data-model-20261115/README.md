# 字典管理数据模型

基于 `summer-next-4` 分支提交 `5734b4a5af285ab83062f45083f7e27d4f529f8d`，以及 `summer_next4_dev` 的实际表结构（2026-11-15）。仅提取结构，无业务记录。

- 核心：字典定义、字典项、分组、分组成员、发布版本，共 5 表。
- 支撑：配置覆盖、覆盖修订、事务 Outbox、通知事件，共 4 表。
- 共 93 个字段，5 组真实数据库外键；逻辑引用另行标注。

## 文件

- [可编辑 draw.io 模型图](dictionary-data-model.drawio)
- [核心模型预览](dictionary-core.png)
- [覆盖与事件预览](dictionary-supporting.png)
- `schema.sql`：9 表实际 DDL；`schema-metadata.json`：字段、键、索引；`dictionary.ir.json`：按复合外键归一化的语义模型。

## 关键语义

1. 字典编码在企业内唯一；字典项值在企业、字典内唯一。所有成员引用带企业和字典编码，防止跨字典挂接。
2. 分组是单个字典内的项子集，项与分组通过成员表多对多关联；成员顺序独立于字典默认顺序。成员的组或项被删除时，数据库级联删除相应成员。
3. 已发布过的项值及分组编码不可更改。字典没有父子层级字段；前端联动配置不是字典项树。
4. 字典定义、项、分组、成员表承载可编辑草稿。发布版本在 `snapshot_json` 中保存完整不可变快照，包括字典属性、items、groups 与成员顺序；不存在单独的版本项或版本分组表。
5. `draft_revision` 控制草稿编辑，`effective_revision` 控制生效状态变更；`published_version` 逻辑定位当前生效的版本号。回滚切换生效指针并保留草稿。
6. 覆盖配置为共享模型，字典只取 `resource=dictionaries`、`config_key=字典编码` 的切片。`scope_id=_tenant` 为企业覆盖，其他 scope_id 为组织；有效读取顺序为组织、企业、基础发布版本。
7. `configuration_override.version` 是乐观锁版本；latest_revision / published_revision 是覆盖历史修订号，与基础字典发布版本号不同。
8. 版本操作分别写 Outbox 与通知事件；二者没有直接外键。Outbox 使用 UUID 主键、幂等键和请求回执，通知事件使用自增 BIGINT 游标。覆盖事件也能产生字典通知。
9. 权限、菜单和审计属于通用模块，本图未展开其数据表。

## 实际数据库外键

| 外键名 | 子表字段 | 父表字段 |
|---|---|---|
| `fk_configuration_override_revision` | `summer_configuration_override_revision` (override_id) | `summer_configuration_override` (id) |
| `fk_dictionary_group_type` | `summer_dictionary_group` (tenant_id, dictionary_code) | `summer_dictionary_type` (tenant_id, code) |
| `fk_dictionary_member_group` | `summer_dictionary_group_member` (tenant_id, dictionary_code, group_id) | `summer_dictionary_group` (tenant_id, dictionary_code, id) |
| `fk_dictionary_member_item` | `summer_dictionary_group_member` (tenant_id, dictionary_code, item_id) | `summer_dictionary_item` (tenant_id, dictionary_code, id) |
| `fk_summer_dictionary_tenant_type` | `summer_dictionary_item` (tenant_id, dictionary_code) | `summer_dictionary_type` (tenant_id, code) |

以下逐表列出全部字段。`?` 表示允许 NULL；类型以 MySQL 物理类型为准。

## 字典定义 · 草稿控制

`summer_dictionary_type`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `code` | `varchar(80)` | 否 |  | 业务编码 |
| `name` | `varchar(200)` | 否 |  | 显示名称 |
| `published` | `tinyint(1)` | 否 |  | 当前是否处于发布状态 |
| `public_readable` | `tinyint(1)` | 否 |  | 是否允许公开读取 |
| `enabled` | `tinyint(1)` | 否 |  | 启用标记 |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `organization_id` | `varchar(64)` | 是 |  | 组织归属；可空 |
| `created_by` | `varchar(128)` | 是 |  | 创建人 |
| `owner_id` | `varchar(128)` | 是 |  | 数据所有者 |
| `published_version` | `bigint` | 是 |  | 当前生效的基础发布版本号；回滚可指向旧版本 |
| `draft_revision` | `bigint` | 否 |  | 草稿修订号；编辑并发控制 |
| `effective_revision` | `bigint` | 否 |  | 生效状态修订号；发布、停用、回滚并发控制 |

唯一约束：

- `PRIMARY (id)`
- `uq_summer_dictionary_tenant_code (tenant_id, code)`

## 字典项

`summer_dictionary_item`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `dictionary_code` | `varchar(80)` | 否 | FK | 字典业务编码；须结合 tenant_id 使用 |
| `item_value` | `varchar(80)` | 否 |  | 供业务数据引用的字典值；曾发布后不可更改 |
| `label` | `varchar(200)` | 否 |  | 字典项显示文本 |
| `sort_order` | `int` | 否 |  | 排序；成员表内为组内独立排序 |
| `enabled` | `tinyint(1)` | 否 |  | 启用标记 |
| `tenant_id` | `varchar(80)` | 否 | FK | 企业隔离标识 |
| `organization_id` | `varchar(64)` | 是 |  | 组织归属；可空 |
| `created_by` | `varchar(128)` | 是 |  | 创建人 |
| `owner_id` | `varchar(128)` | 是 |  | 数据所有者 |
| `description` | `varchar(2000)` | 否 |  | 字典项说明 |
| `ever_published` | `tinyint(1)` | 否 |  | 是否曾发布；身份不可变约束依据 |

唯一约束：

- `PRIMARY (id)`
- `uk_dictionary_item_identity (tenant_id, dictionary_code, id)`
- `uq_summer_dictionary_tenant_value (tenant_id, dictionary_code, item_value)`

## 字典分组 · 项的子集

`summer_dictionary_group`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `tenant_id` | `varchar(80)` | 否 | FK | 企业隔离标识 |
| `dictionary_code` | `varchar(80)` | 否 | FK | 字典业务编码；须结合 tenant_id 使用 |
| `code` | `varchar(80)` | 否 |  | 业务编码 |
| `name` | `varchar(200)` | 否 |  | 显示名称 |
| `enabled` | `tinyint(1)` | 否 |  | 启用标记 |
| `ever_published` | `tinyint(1)` | 否 |  | 是否曾发布；身份不可变约束依据 |

唯一约束：

- `PRIMARY (id)`
- `uk_dictionary_group_code (tenant_id, dictionary_code, code)`
- `uk_dictionary_group_identity (tenant_id, dictionary_code, id)`

## 分组成员 · 多对多关联

`summer_dictionary_group_member`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `tenant_id` | `varchar(80)` | 否 | FK | 企业隔离标识 |
| `dictionary_code` | `varchar(80)` | 否 | FK | 字典业务编码；须结合 tenant_id 使用 |
| `group_id` | `varchar(36)` | 否 | FK | 字典分组 ID |
| `item_id` | `varchar(36)` | 否 | FK | 字典项 ID |
| `sort_order` | `int` | 否 |  | 排序；成员表内为组内独立排序 |

唯一约束：

- `PRIMARY (id)`
- `uk_dictionary_group_member (tenant_id, dictionary_code, group_id, item_id)`

## 不可变发布版本

`summer_dictionary_version`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `version_id` | `varchar(36)` | 否 | PK | 发布版本记录 ID |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `dictionary_code` | `varchar(80)` | 否 |  | 字典业务编码；须结合 tenant_id 使用 |
| `version_number` | `bigint` | 否 |  | 该字典发布版本号 |
| `status` | `varchar(20)` | 否 |  | 状态；版本表为 PUBLISHED，Outbox 为 PENDING / PUBLISHED |
| `snapshot_json` | `longtext` | 否 |  | 字典属性、字典项、分组及成员顺序的完整不可变 JSON 快照 |
| `created_by` | `varchar(128)` | 是 |  | 创建人 |
| `request_id` | `varchar(128)` | 是 |  | 请求追踪标识 |
| `created_at` | `timestamp(6)` | 否 |  | 记录创建时间 |

唯一约束：

- `PRIMARY (version_id)`
- `uk_summer_dictionary_version_number (tenant_id, dictionary_code, version_number)`

## 发布事务事件 · Outbox

`summer_dictionary_outbox`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `event_id` | `varchar(36)` | 否 | PK | 事件主键；Outbox 为 UUID 字符串，通知表为自增 bigint |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `dictionary_code` | `varchar(80)` | 否 |  | 字典业务编码；须结合 tenant_id 使用 |
| `event_type` | `varchar(80)` | 否 |  | 业务事件类型 |
| `version_number` | `bigint` | 否 |  | 该字典发布版本号 |
| `request_id` | `varchar(128)` | 是 |  | 请求追踪标识 |
| `payload_json` | `text` | 否 |  | 事件载荷 JSON |
| `idempotency_key` | `varchar(255)` | 是 |  | 幂等键；允许空值 |
| `status` | `varchar(20)` | 否 |  | 状态；版本表为 PUBLISHED，Outbox 为 PENDING / PUBLISHED |
| `created_at` | `timestamp(6)` | 否 |  | 记录创建时间 |
| `request_fingerprint` | `varchar(64)` | 是 |  | 请求语义指纹 |
| `result_json` | `longtext` | 是 |  | 幂等重放的结果回执 JSON |

唯一约束：

- `PRIMARY (event_id)`
- `uk_summer_dictionary_outbox_idempotency (tenant_id, dictionary_code, idempotency_key)`

## 通知事件 · 增量游标

`summer_dictionary_notification_event`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `event_id` | `bigint` | 否 | PK | 事件主键；Outbox 为 UUID 字符串，通知表为自增 bigint |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `dictionary_code` | `varchar(80)` | 否 |  | 字典业务编码；须结合 tenant_id 使用 |
| `event_type` | `varchar(80)` | 否 |  | 业务事件类型 |
| `version_number` | `bigint` | 否 |  | 该字典发布版本号 |
| `request_id` | `varchar(128)` | 是 |  | 请求追踪标识 |
| `payload_json` | `text` | 否 |  | 事件载荷 JSON |
| `created_at` | `timestamp(6)` | 否 |  | 记录创建时间 |

唯一约束：

- `PRIMARY (event_id)`

## 企业 / 组织配置覆盖

`summer_configuration_override`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `resource` | `varchar(160)` | 否 |  | 配置资源类型；字典为 dictionaries |
| `config_key` | `varchar(160)` | 否 |  | 配置业务键；字典场景为字典 code |
| `scope_id` | `varchar(64)` | 否 |  | _tenant 表示企业覆盖；其他值为组织 ID |
| `organization_id` | `varchar(64)` | 是 |  | 组织归属；可空 |
| `created_by` | `varchar(128)` | 否 |  | 创建人 |
| `owner_id` | `varchar(128)` | 否 |  | 数据所有者 |
| `value_json` | `text` | 否 |  | 覆盖配置值；字典场景为字典项数组 |
| `public_readable` | `tinyint(1)` | 否 |  | 是否允许公开读取 |
| `enabled` | `tinyint(1)` | 否 |  | 启用标记 |
| `version` | `bigint` | 否 |  | JPA 乐观锁版本；区别于历史修订号 |
| `updated_at` | `timestamp(6)` | 否 |  | 最近更新时间 |
| `latest_revision` | `bigint` | 否 |  | 最新覆盖修订号 |
| `published_revision` | `bigint` | 是 |  | 当前已发布覆盖修订号 |

唯一约束：

- `PRIMARY (id)`
- `uk_configuration_override (tenant_id, resource, config_key, scope_id)`

## 覆盖配置 · 不可变历史

`summer_configuration_override_revision`

| 字段 | 类型 | 可空 | 键标记 | 含义 |
|---|---|---|---|---|
| `id` | `varchar(36)` | 否 | PK | 记录标识 |
| `override_id` | `varchar(36)` | 否 | FK | 所属覆盖配置 ID |
| `tenant_id` | `varchar(80)` | 否 |  | 企业隔离标识 |
| `revision` | `bigint` | 否 |  | 覆盖历史修订号 |
| `state` | `varchar(20)` | 否 |  | DRAFT / PUBLISHED / DISABLED / ROLLED_BACK |
| `value_json` | `text` | 否 |  | 覆盖配置值；字典场景为字典项数组 |
| `public_readable` | `tinyint(1)` | 否 |  | 是否允许公开读取 |
| `enabled` | `tinyint(1)` | 否 |  | 启用标记 |
| `principal` | `varchar(128)` | 否 |  | 操作者 |
| `request_id` | `varchar(128)` | 是 |  | 请求追踪标识 |
| `created_at` | `timestamp(6)` | 否 |  | 记录创建时间 |

唯一约束：

- `PRIMARY (id)`
- `uq_configuration_override_revision (override_id, revision)`

## 实现来源

- `summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/` 下实体、DictionaryGroups、DictionaryVersionService、DictionaryNotificationService。
- `summer-sample/src/main/java/com/abluepoint/summer/sample/configuration/` 下覆盖实体与 ConfigurationOverrideService。
- `summer-sample/src/main/resources/db/migration/mysql/` 下 V23、V27、V30、V32、V33、V40、V41、V42 等迁移；最终类型和约束以提取的实际 DDL 为准。
