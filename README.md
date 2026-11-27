# Summer 4

Summer 4 是企业应用基础框架，提供 Common、MVC、Data JPA、Security 四类可复用能力，以 Gradle Kotlin DSL 构建，通过独立 Starter 接入。

`summer4` 分支按 ADR 0001–0016 整理架构。框架、参考后端 `summer-sample`、参考前端 `summer-ui` 各自承担独立职责；AICT IAM、BFF 和连接服务属于消费框架的产品应用。先读 [整体架构](docs/architecture/整体架构.md) 和 [现状与目标差异](docs/architecture/现状与目标差异.md)。

## 当前实现状态

- 15 个 Gradle 子项目已存在，涵盖依赖平台、四类能力、四类自动配置、四类 Starter、聚合 Starter 和 Sample。
- 最新决策采用官方 Arco Design Pro React/Vite、OIDC public client + PKCE，以及不可变 Customer username；这些决策尚未全部实现。
- `summer-ui` 已按官方 Arco Design Pro React/Vite `v2.8.1` 建立独立基线；Customer API 仍使用 email 路径，注册客户端仍要求 secret，暂不要按目标契约调用现有后端。
- 工作区中的 AICT 业务实现目前仍位于 Sample；其迁出安排见 [迁移计划](docs/guides/summer4迁移计划.md)。

## 接入与构建

```kotlin
dependencies {
    implementation(platform("com.abluepoint.summer:summer-dependencies:4.0.0-SNAPSHOT"))
    implementation("com.abluepoint.summer:summer-boot-starter")
}
```

也可只引入 `summer-common-starter`、`summer-mvc-starter`、`summer-data-jpa-starter` 或 `summer-security-starter`。授权服务与资源保护需要显式启用，详见 [Starter 接入指南](docs/guides/Starter接入指南.md)。

仓库固定基线为 Java toolchain 21、Gradle 9.0.0、Spring Boot 4.0.7、Spring Cloud 2025.1.2、Spring Modulith 2.0.7。版本事实来源为 [版本目录](gradle/libs.versions.toml)、[Wrapper](gradle/wrapper/gradle-wrapper.properties) 和 [依赖平台](summer-dependencies/build.gradle.kts)，本次架构整理不升级依赖。

```bash
./gradlew check
./gradlew :summer-sample:bootJar
./gradlew :summer-sample:bootRun
```

完整验收需要可用的容器运行时和 PostgreSQL Testcontainers；执行结果必须核对是否跳过。Sample 使用 `create-drop` 和演示身份/临时密钥，仅连接可丢弃的开发数据库；演示凭据通过环境变量注入。前端使用 `summer-ui` 独立执行 Node/Vite 构建，Gradle 不构建前端，Sample JAR 不嵌入前端产物。

## 文档入口

| 文档 | 用途 |
| --- | --- |
| [CONTEXT](CONTEXT.md) | 唯一领域术语表 |
| [ADR 索引](docs/adr/README.md) | 决策、替代关系与来源 |
| [整体架构](docs/architecture/整体架构.md) | 产品分层、请求链路、数据与部署职责 |
| [产品范围](docs/architecture/产品范围.md) | 保留能力、应用边界与非目标 |
| [模块边界](docs/architecture/模块边界.md) | 15 个构建模块、依赖规则与 Sample 包归属 |
| [安全模型](docs/architecture/OAuth2-OIDC安全模型.md) | Bearer、登录会话、OIDC 与产品 BFF 的职责 |
| [现状与目标差异](docs/architecture/现状与目标差异.md) | 源码证据、未完成项与本次验证 |
| [summer4 迁移计划](docs/guides/summer4迁移计划.md) | 分阶段迁移、验收与回退 |
| [迁移与验收说明](docs/guides/迁移与验收说明.md) | Gradle 基线与后续完整质量门禁 |

需求和任务以 [GitHub Issues](https://github.com/abluepoint/summer/issues) 为准；最新 Summer UI 规格为 [Issue #69](https://github.com/abluepoint/summer/issues/69)。

### 平台文件存储库

在「文件管理 → 文件存储库」登记本地子目录或已有的 S3 存储桶。本地根目录由 `summer.storage.root` 指定，默认是服务工作目录下的 `data/file-repositories`。多实例部署时应将该目录指向同一共享卷。

新增 S3 库时在页面填写 Access Key ID 和 Secret Access Key。管理员可用「更换密钥」更新已有库。服务端使用部署主密钥（`SUMMER_SECURITY_MASTER_KEY` 或 `summer.sample.security.master-key`）加密保存每个库的凭据，保存前验证桶访问权限。已有未单独配置密钥的库继续使用 AWS SDK 默认凭据链。服务地址和区域仍由部署配置 `summer.storage.s3.endpoint` 和 `summer.storage.s3.region` 指定。列表统计需要列举桶内对象；读写及删除分别需要相应对象权限。刷新会遍历目录或分页列举存储桶，S3 的可用空间显示为「未提供」。

业务服务可注入 `com.abluepoint.summer.sample.storage.FileStoreManager`，通过存储库 ID 与文件名调用 `write(id, key, inputStream, byteLength)`、`read(id, key)` 和 `delete(id, key)`。写入同名文件会替换内容，调用方应关闭输入流和读取返回的流。移除登记保留底层文件。
