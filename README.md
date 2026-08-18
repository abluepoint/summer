# Summer 4

Summer 4 是基于 Java 21、Spring Boot 4.0、Spring Cloud 2025.1.2 和 Spring Modulith 2.0 重建的企业级基础框架。构建系统统一为 Gradle Kotlin DSL，安全体系统一为 OAuth2/OIDC，并保留 Freemarker SQL 与 Tuple 多形态映射能力。

## 模块

- `summer-dependencies`：唯一版本与 BOM 入口。
- `summer-common`、`summer-mvc`、`summer-data-jpa`、`summer-security`：核心能力模块。
- `summer-*-autoconfigure`：条件化自动配置。
- `summer-*-starter`：按能力域接入入口。
- `summer-boot-starter`：Web、Data JPA、Security、Actuator 聚合入口。
- `summer-data-jpa-test`：Modulith 示例应用与端到端验收工程。

`summer-automator` 和核心模块的旧 Maven POM 作为历史知识资产保留，但不再参与核心 Gradle 构建。`summer-data-jpa-test` 的旧 Maven POM、`src/main` 示例与旧 SQL 资源已移除，仅保留 Summer 4 示例。

## 构建与验证

```bash
./gradlew clean check
./gradlew :summer-data-jpa-test:bootRun
```

构建固定使用 Java 21 toolchain；本机无 JDK 21 时由 Gradle toolchain resolver 获取。集成测试会通过 Testcontainers 拉起 PostgreSQL，并验证 OAuth2 令牌到受保护模板查询的完整链路。

## 示例鉴权链路

示例应用显式开启 Authorization Server 与 Resource Server。演示凭据通过 `SUMMER_SAMPLE_CLIENT_ID`、`SUMMER_SAMPLE_CLIENT_SECRET`、`SUMMER_SAMPLE_USER`、`SUMMER_SAMPLE_USER_PASSWORD` 注入；未设置时只使用标记为 demo-only 的本地演示值，不应复制到生产环境。

核心端点：

- `POST /oauth2/token`：客户端凭据换取访问令牌。
- `GET /api/customers/{email}`：需要 `customer.read` scope，执行 Freemarker SQL 模板查询。
- `POST /api/customers`：需要 `customer.write` scope，演示 Jakarta Validation 与统一错误响应。
- `GET /actuator/health`、`GET /actuator/metrics`：健康与指标。

详细设计与迁移说明见 [doc/重构范围与术语.md](doc/重构范围与术语.md)、[doc/模块边界.md](doc/模块边界.md)、[doc/OAuth2-OIDC安全模型.md](doc/OAuth2-OIDC安全模型.md)、[doc/Starter接入指南.md](doc/Starter接入指南.md) 和 [doc/迁移与验收说明.md](doc/迁移与验收说明.md)。
