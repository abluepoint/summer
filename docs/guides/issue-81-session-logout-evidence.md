# Issue #81：服务端会话与 OIDC 退出验收

依据 [ADR 0015](../adr/0015-use-spring-security-public-client-pkce-for-summer-ui.md) 和 [安全模型](../architecture/OAuth2-OIDC安全模型.md)。本票验证服务端会话边界，以及 #86 接入实际 OIDC 客户端后的浏览器退出行为。

| 要求 | 实现与证据 |
| --- | --- |
| 30 分钟 inactivity | Sample 的 `server.servlet.session.timeout=30m`；嵌入式 Tomcat 测试读取实际 ServletContext 超时 |
| Cookie 属性 | 实际 HTTP `/csrf` 响应验证 `HttpOnly`、`SameSite=Lax`；本地 HTTP 无 Secure，启用部署配置时有 Secure |
| 固定攻击防护 | form login 后 session ID 改变，既有授权请求继续完成 |
| RP logout | discovery 发布 end-session；真实 PKCE 签发的 ID Token 经 `/connect/logout` 验证，销毁 session、清除 JSESSIONID，重定向到注册的 URI；错误 hint 返回 400 |
| Access token 生命周期 | ID Token 和 access token 的 `exp` 均约 15 分钟；会话失效或退出后，已签发 access token 在自身有效期内仍可调用 Bearer API |
| 两个浏览器 session 并存 | MockMvc 以两个独立 HTTP session 分别完成登录；第二个登录后，两者继续发起 OIDC authorize 并各自获得 code redirect。真实浏览器套件另用两个隔离 context 分别登录，核对不同的 `JSESSIONID`，刷新后各自完成新 PKCE 授权并回到 Customer 列表。安全链只启用 `IF_REQUIRED` 和 fixation protection，未配置 maximum sessions |
| 退出跳转失败后的本地清理 | 真实浏览器在完成登录后注入 end-session navigation 拒绝，点击退出落到 `/login?logoutError`；再次读取 OIDC 内存用户为空，PKCE、恢复与草稿键均不存在。该故障注入只验证跳转调用拒绝，不能证明浏览器已离开页面后的服务端 5xx 可被 SPA 捕获 |

原 #81 提交的聚焦门禁为 5 项通过、0 失败；当时完整 `check` 的 3 项 Sample 失败已由后续票据修正。本次续验的 `./gradlew :summer-sample:test --tests '*PublicClientPkceIntegrationTest' --rerun-tasks` 为 4 tests、0 failures/errors/skipped；`./gradlew check --rerun-tasks` 为 143 tests、0 failures/errors/skipped。前端 `yarn test` 为 19 tests、0 failures/skipped；`yarn typecheck`、`yarn lint:style`、`yarn build` 均成功，`yarn lint` 为 0 errors、40 条已有 warnings。浏览器使用真实 Spring 后端、同源 Vite、独立 context 和 `yarn test:e2e:auth`，覆盖双 session、正常退出与失败注入；本次浏览器联调使用 Sample 默认 H2，未重新执行 PostgreSQL 全套 Customer 流程。

回滚以本票提交为单位，连同 Sample 配置、授权服务默认值、logout handler 和测试一起回退。回滚后 RP logout 的 session/Cookie 行为及 30 分钟超时不再受本票保证；部署环境中的 `SUMMER_SAMPLE_SESSION_COOKIE_SECURE` 设置可保留但回滚版本不会读取它。已签发 JWT 仍按自身 `exp` 失效，回滚不会撤销它们。

本次续验提交只增加测试和证据文档；单独回退它仅移除验收覆盖，不改变生产会话和退出行为。

双轴审阅以本次提交相对 `0d343d1` 的差异为范围：规范轴核对 `AGENTS.md`、测试门禁和敏感值输出，未发现需修改项；规格轴核对 #81 与 #69 AC33–36，新增双 session 与失败清理证据，30 分钟、Cookie、固定攻击防护、RP logout 和 token 生命周期继续由现有测试覆盖。跳转已经离开 SPA 后的服务端 5xx 无法由当前 `signoutRedirect` 的 Promise 捕获；此限制不影响跳转前清理，若要求对服务端 5xx 显示应用内提示，需要另行设计可观察的退出响应流程。
