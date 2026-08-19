# Separate capability, assembly, and dependency entry points

Summer 4 对每项框架能力采用“能力模块 → 自动配置模块 → Starter”的强制分层，并让聚合 Starter 只组合各能力 Starter。能力模块拥有实现和稳定 API，自动配置模块负责条件装配，Starter 只表达依赖；项目接受更多模块带来的管理成本，以换取能力隔离、按需接入、独立测试和避免 Spring Boot 装配逻辑反向污染能力 API，并由构建边界检查持续约束该结构。
