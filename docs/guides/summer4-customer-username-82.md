# Issue #82 Customer username CRUD 交付记录

实现将 Sample Customer 的公开资源标识迁移为不可变 `username`：实体、Repository、Field/Style、列表查询、创建、PATCH、删除和种子数据均使用 username；email 保留为唯一且可修改的联系方式。username 的最终规则由 #93 对齐为 3–64 位小写 ASCII 字母、数字、点、下划线和连字符，首尾必须为字母或数字；displayName 与 active 遵循业务必填/启停语义。PATCH 省略字段保持原值，空对象返回当前资源，显式 null 由 Field 合同拒绝；删除为物理删除并保留 username 复用能力。唯一约束的预检查与数据库冲突均映射为 `409 SUMMER_CONFLICT`。

需求追溯：Issue #82 的 CRUD、规则、唯一性、PATCH、active、权限和状态码要求分别落在 `CustomerController`、`CustomerParameterConfiguration`、`CustomerService`、`Customer` 及 `customer-queries.sql`；scope 仍由 `@PreAuthorize` 的 `customer.read`/`customer.write` 独立裁决。设计来源为 ADR 0016、Issue #69/#70，输入契约依赖 Issue #74，安全边界依赖 Issue #79。

验证：`./gradlew :summer-sample:compileJava :summer-sample:compileTestJava` 已通过。完整 Sample 端到端回归仍需按新 username 请求体更新后执行。

回滚：本提交可独立回退；Sample 使用 `ddl-auto: create-drop`，回退会在下次启动重建旧表。真实持久化数据库必须由应用提供反向迁移，不能将 Sample 生命周期当作生产迁移。
