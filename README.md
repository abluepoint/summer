# Summer 4

Summer 4 是基于 Java 21、Spring Boot 4.0、Spring Cloud 2025.1.2 和 Spring Modulith 2.0 的企业应用基础框架产品线。构建系统统一为 Gradle Kotlin DSL，安全体系统一为 OAuth2/OIDC，并提供 Summer MVC、Freemarker SQL template 与窄化结果投影等可复用能力。

## 模块

- `summer-dependencies`：唯一版本与 BOM 入口。
- `summer-common`、`summer-mvc`、`summer-data-jpa`、`summer-security`：核心能力模块。
- `summer-*-autoconfigure`：条件化自动配置。
- `summer-*-starter`：按能力域接入入口。
- `summer-boot-starter`：MVC、Data JPA、Security、Actuator 聚合入口。
- `summer-sample`：展示和验证框架能力的 Modulith 参考应用，不属于框架产品能力。

`summer-parent` 与 `summer-automator` 已退出活动分支，历史实现通过 Git 追溯。Gradle 构建、`summer-dependencies` 和版本目录是当前唯一构建与依赖治理来源。

## 快速接入

```kotlin
dependencies {
    implementation(platform("com.abluepoint.summer:summer-dependencies:4.0.0-SNAPSHOT"))
    implementation("com.abluepoint.summer:summer-boot-starter")
}
```

也可以只引入 `summer-common-starter`、`summer-mvc-starter`、`summer-data-jpa-starter` 或 `summer-security-starter`。

## 构建与验证

```bash
./gradlew clean check
./gradlew :summer-sample:bootRun
```

构建固定使用 Java 21 toolchain；本机无 JDK 21 时由 Gradle toolchain resolver 获取。集成测试会通过 Testcontainers 拉起 PostgreSQL，并验证 OAuth2 令牌到受保护模板查询的完整链路。

## 示例鉴权链路

示例应用显式开启 Authorization Server 与 Resource Server。演示凭据通过 `SUMMER_SAMPLE_CLIENT_ID`、`SUMMER_SAMPLE_CLIENT_SECRET`、`SUMMER_SAMPLE_USER`、`SUMMER_SAMPLE_USER_PASSWORD` 注入；未设置时只使用标记为 demo-only 的本地演示值，不应复制到生产环境。

核心端点：

- `POST /oauth2/token`：客户端凭据换取访问令牌。
- `GET /api/customers/{email}`：需要 `customer.read` scope，执行 Freemarker SQL 模板查询。
- `POST /api/customers`：需要 `customer.write` scope，演示 Jakarta Validation 与统一错误响应。
- `GET /actuator/health`、`GET /actuator/metrics`：健康与指标。

统一术语见 [CONTEXT.md](CONTEXT.md)，架构决策见 [docs/adr](docs/adr)，详细说明见 [产品范围](docs/architecture/产品范围.md)、[模块边界](docs/architecture/模块边界.md)、[OAuth2/OIDC 安全模型](docs/architecture/OAuth2-OIDC安全模型.md)、[Starter 接入指南](docs/guides/Starter接入指南.md) 和 [迁移与验收说明](docs/guides/迁移与验收说明.md)。
