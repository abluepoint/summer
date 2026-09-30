# Issue #81：服务端会话与 OIDC 退出验收

依据 [ADR 0015](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md) 和 [安全模型](../architecture/OAuth2-OIDC安全模型.md)。本票实现并验证服务端边界；浏览器内存认证状态、PKCE 交易清理和退出调用失败后的本地清理由 #86 接入实际 OIDC 客户端后验证。

| 要求 | 实现与证据 |
| --- | --- |
| 30 分钟 inactivity | Sample 的 `server.servlet.session.timeout=30m`；嵌入式 Tomcat 测试读取实际 ServletContext 超时 |
| Cookie 属性 | 实际 HTTP `/csrf` 响应验证 `HttpOnly`、`SameSite=Lax`；本地 HTTP 无 Secure，启用部署配置时有 Secure |
| 固定攻击防护 | form login 后 session ID 改变，既有授权请求继续完成 |
| RP logout | discovery 发布 end-session；真实 PKCE 签发的 ID Token 经 `/connect/logout` 验证，销毁 session、清除 JSESSIONID，重定向到注册的 URI；错误 hint 返回 400 |
| Access token 生命周期 | ID Token 和 access token 的 `exp` 均约 15 分钟；会话失效或退出后，已签发 access token 在自身有效期内仍可调用 Bearer API |

聚焦门禁：`./gradlew :summer-sample:test --tests '*PublicClientPkceIntegrationTest' --tests '*SessionCookieIntegrationTest'`，5 项通过、0 失败。完整 `./gradlew check` 在本工作树执行，Sample 共 32 项、3 失败：两个 `SummerEndToEndTest` 断言仍使用 Customer username 迁移前的请求形态，一个 PostgreSQL Testcontainers 测试在启动期因缺少 `summer_customer` 表失败；这些失败不在 #81 改动文件中。测试报告位于 `summer-sample/build/test-results/test/`；后续运行以当次报告为准。

回滚以本票提交为单位，连同 Sample 配置、授权服务默认值、logout handler 和测试一起回退。回滚后 RP logout 的 session/Cookie 行为及 30 分钟超时不再受本票保证；部署环境中的 `SUMMER_SAMPLE_SESSION_COOKIE_SECURE` 设置可保留但回滚版本不会读取它。已签发 JWT 仍按自身 `exp` 失效，回滚不会撤销它们。
