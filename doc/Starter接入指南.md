# Starter 接入指南

## 组合

| 目标 | 依赖 |
| --- | --- |
| 统一错误、消息、上下文 | `summer-common-starter` |
| Web 请求链与统一响应 | `summer-web-starter` |
| JPA、模板 SQL、Tuple 映射 | `summer-data-jpa-starter` |
| OAuth2/OIDC | `summer-security-starter` |
| 完整平台骨架 | `summer-boot-starter` |

所有模块版本由 `com.abluepoint.summer:summer-dependencies` 统一约束。业务构建只声明 Summer BOM 和需要的 starter，不重复声明 Boot、Cloud、Modulith 或 Summer 子模块版本。

## 开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `summer.common.enabled` | `true` | 消息解析与公共基座。 |
| `summer.web.enabled` | `true` | Web 自动配置总开关。 |
| `summer.web.cache-request-body` | `true` | 请求体重复读取与上下文注入。 |
| `summer.web.wrap-responses` | `true` | Controller 返回值统一包装；OAuth2/Actuator 自动跳过。 |
| `summer.data.jpa.enabled` | `true` | SQL 模板源、渲染器、TupleMapper、JpaSqlTemplate。 |
| `summer.security.enabled` | `true` | 安全自动配置总开关。 |
| `summer.security.authorization-server.enabled` | `false` | 授权服务过滤链与令牌签发。 |
| `summer.security.resource-server.enabled` | `false` | JWT 资源保护链。 |

## 最小数据查询

1. 在资源文件中用 `-- findByEmail` 声明模板段。
2. 注入 `JpaSqlTemplate`。
3. 提供模板参数、JPA 命名参数和目标 Bean/Record/接口类型。

## 最小安全接入

1. 显式开启 Authorization Server/Resource Server。
2. 从安全配置通道提供客户端 secret。
3. 为业务端点配置 scope，并用 `@PreAuthorize("hasAuthority('SCOPE_xxx')")` 验证。
4. 生产覆盖内存客户端、用户和临时 JWK 实现。
