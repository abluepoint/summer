# Issue #83 Customer 列表查询交付记录

## 交付范围

Customer 列表接口 `GET /api/customers` 现在明确支持 `page`、`size`、`active`、`sort`、`direction` 和 `projection`。缺省值为 `page=0`、`size=20`、`sort=username`、`direction=asc`、`projection=detail`；`size` 限制为 1–100，`page` 不允许为负数，排序字段只允许 `username`、`email`、`displayName`、`active`。列表结果为空时仍返回成功响应和空 `items`，总数与总页数保持可计算。

`summary` 投影返回 `customerId`、`username`、`email`、`displayName`，`detail` 投影在此基础上返回 `active`。SQL 模板只选择对应投影的列，并在请求排序字段之后追加 `username asc` 作为稳定尾排序。Repository 仅保留实体按 username、唯一性检查等简单读取；分页、筛选、排序和投影由 `CustomerQuery` 使用窄结果投影完成，Customer 模块的持久化层没有引入 Summer MVC 类型。

## 需求追溯与证据

| 要求 | 实现 | 验证 |
| --- | --- | --- |
| 分页、过滤、排序、方向 | `CustomerController`、`CustomerListQuery`、`CustomerQuery`、`customer-queries.sql` | `CustomerListHttpTest` 的 email 排序、active 过滤和空页测试 |
| 稳定 username 尾排序 | `customer-queries.sql` 的 `, username asc` | HTTP 排序测试断言同一结果可定位且 email 顺序正确 |
| detail/summary 窄投影 | `CustomerView`、`CustomerSummaryView` 和 SQL 投影分支 | `CustomerPersistenceTest.complexReadsUseNarrowSummaryAndDetailProjections` |
| 默认值、边界和空结果 | Field Style、`CustomerListQuery` 和列表 envelope | `SummerEndToEndTest` 的默认/边界/参数聚合测试与 `CustomerListHttpTest` 的 active 过滤、空页测试 |
| 模块边界 | `CustomerRepository` 与 `CustomerQuery` 分工 | `CustomerArchitectureTest` 检查 Repository 不依赖 MVC、SQL 模板或 `CustomerPage` |

## 验证命令

```text
./gradlew :summer-sample:test --tests 'com.abluepoint.summer.sample.customer.CustomerListHttpTest' --tests 'com.abluepoint.summer.sample.customer.CustomerPersistenceTest' --tests 'com.abluepoint.summer.sample.customer.CustomerArchitectureTest'
```

上述聚焦执行通过（8 tests，failures 0）。完整 `:summer-sample:test --rerun-tasks` 共执行 33 tests；其中 30 项通过，3 项失败：PostgreSQL Testcontainers 环境因 `summer_customer` 表未建立而失败，另有 `SummerEndToEndTest` 中两项 #82 迁移遗留断言仍使用旧语义。后 3 项均未由本票据改动，聚焦的 #83 测试仍全部通过。

## 回滚

本票据变更可独立回退，回退后列表恢复到此前的查询排序/投影实现。Sample 使用 `ddl-auto: create-drop`，本票据没有数据库迁移；真实持久化应用仍应按自身迁移策略回滚代码和查询契约。文档、HTTP、持久化和架构测试随同提交一起回退。
