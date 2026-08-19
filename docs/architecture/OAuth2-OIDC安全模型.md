# OAuth2/OIDC 安全模型

## 职责边界

- Authorization Server：管理注册客户端、授权类型、scope、临时 RSA 签名密钥、OIDC 元数据和令牌签发。
- Resource Server：对配置的受保护路径执行 JWT 校验，并将 scope 映射为 `SCOPE_*` authority。
- 应用安全链：提供授权码流程所需登录页；不接管资源服务之外的公开端点。
- 业务方法：使用 `@PreAuthorize` 做细粒度 scope 决策。

过滤链按顺序隔离：授权服务端点为 Order 1，受保护资源为 Order 2，登录/公开应用端点为 Order 3。

## 配置模型

```yaml
summer:
  security:
    enabled: true
    authorization-server:
      enabled: true
      issuer: https://identity.example.com
      clients:
        - id: application-client
          secret: ${APPLICATION_CLIENT_SECRET}
          grant-types: [client_credentials, authorization_code, refresh_token]
          redirect-uris: [https://app.example.com/login/oauth2/code/summer]
          scopes: [openid, profile, customer.read]
    resource-server:
      enabled: true
      protected-paths: [/api/**]
```

安全模块没有生产默认客户端。Authorization Server 开启但未配置客户端时启动失败。示例中的 demo-only 值仅用于本地验证，生产必须由受控配置通道注入，并替换内存客户端、用户和临时 JWK Bean。

## 错误与会话策略

- 无令牌、令牌无效或过期：HTTP 401，错误码 `SUMMER_UNAUTHORIZED`。
- 令牌有效但权限不足：HTTP 403，错误码 `SUMMER_FORBIDDEN`。
- Security 复用 Summer Common 的 `ApiResponse`/`ApiError`，不维护重复安全响应类型；方法级拒绝 advice 使用高优先级，避免被 MVC 通用异常处理降级为 500。
- 资源服务使用 `STATELESS`，不使用 HttpSession 恢复认证。
- 授权码登录链使用 `IF_REQUIRED`，只为交互式授权保存必要状态。
- 安全日志只记录事件、主体标识和认证类型，不记录 token、secret 或密码。

## 演进点

- 通过覆盖 `RegisteredClientRepository` 接入持久化客户端。
- 通过覆盖 `JWKSource` 接入 HSM/KMS 或轮换密钥集。
- 通过覆盖 `UserDetailsService` 或增加联合身份配置接入企业 IdP。
- 保持 Authorization Server 与 Resource Server Bean 边界，后续可无业务 API 变更地拆分部署。
