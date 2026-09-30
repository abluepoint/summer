# Issue #82 Customer username CRUD 交付记录

实现将 Sample Customer 的公开资源标识迁移为不可变 `username`：实体、Repository、Field/Style、列表查询、创建、PATCH、删除和种子数据均使用 username；email 保留为唯一且可修改的联系方式。username 的最终规则由 #93 对齐为 3–64 位小写 ASCII 字母、数字、点、下划线和连字符，首尾必须为字母或数字；displayName 与 active 遵循业务必填/启停语义。PATCH 省略字段保持原值，空对象返回当前资源，显式 null 由 Field 合同拒绝；删除为物理删除并保留 username 复用能力。串行预检查与具名数据库唯一约束冲突均映射为 `409 SUMMER_CONFLICT`，并只公开对应 `username` 或 `email` violation；其他完整性错误不伪装为唯一冲突，数据库异常细节不会进入 API 响应。旧的 email 推导 username 兼容构造器已移除。

#93 将 Sample 种子用户名对齐为 `summer.demo`；旧版 `demo` 的验收数据不再代表当前种子。

需求追溯：Issue #82 的 CRUD、规则、唯一性、PATCH、active、权限和状态码要求分别落在 `CustomerController`、`CustomerParameterConfiguration`、`CustomerService`、`Customer` 及 `customer-queries.sql`；scope 仍由 `@PreAuthorize` 的 `customer.read`/`customer.write` 独立裁决。设计来源为 ADR 0016、Issue #69/#70，输入契约依赖 Issue #74，安全边界依赖 Issue #79。

验收证据：`SummerEndToEndTest` 覆盖 username/email 创建冲突、PATCH email 冲突、`PATCH {}`、email 形态的 GET/PATCH/DELETE 路径 400，以及既有权限、启停和删除复用路径；`CustomerUniqueConflictTest` 覆盖并发时预检查通过后的具名约束异常映射及未知完整性错误；`PostgresContainerIntegrationTest` 在真实 PostgreSQL 中核对重建后的完整列集合、具名唯一约束、唯一/非空拒绝、新种子且仅有一条记录；Summer UI `verify-customer-form.mjs` 覆盖两种 409 violation 的字段映射。最终源码执行 `./gradlew check` 通过，测试 XML 汇总 147 tests、0 failures、0 errors、0 skipped，其中 PostgreSQL 容器 2 tests 均实际执行；`npm test` 为 19 tests、0 failures、0 skipped。

风险：Sample 仍使用 `create-drop`，不能把它当作持久数据库迁移；无乐观锁时并发 PATCH 仍采用最后成功写入覆盖可变字段。

回滚：本提交可独立回退；Sample 使用 `ddl-auto: create-drop`，回退会在下次启动重建旧表。真实持久化数据库必须由应用提供反向迁移，不能将 Sample 生命周期当作生产迁移。
