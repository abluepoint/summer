# summer-data-jpa

Summer Data JPA 提供基础实体与 Repository 契约，以及面向复杂读取的 Freemarker SQL template。

应用通过 `JpaSqlTemplate` 执行模板查询。结果可投影为单列标量、`Map<String,Object>`、Record/构造器 DTO、Spring Data 接口投影或原始 `Tuple`；SQL 列别名会显式规范化为 camelCase。

接入和配置说明见 [Starter 接入指南](../../docs/guides/Starter接入指南.md)。
