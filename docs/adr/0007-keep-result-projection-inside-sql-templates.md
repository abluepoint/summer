# Keep result projection inside SQL templates

Summer Data JPA 不把现有 `TupleMapper` 发展为独立、通用的长期公共 API，而在 SQL template 内保留窄化的结果投影适配器。该适配器优先委托 Spring Data 的公开投影能力，只自行承担显式 camelCase 列别名规范化及上游未覆盖的必要差异；稳定返回形态限定为单列标量、`Map<String, Object>`、Record/构造器 DTO、Spring Data 接口投影和原始 `Tuple`，不支持 setter Bean 或依赖列顺序的 `List<Object>`。项目接受放弃独立 Tuple 映射复用面的代价，以减少与 Spring Data 重复实现、行为漂移和长期维护成本。
