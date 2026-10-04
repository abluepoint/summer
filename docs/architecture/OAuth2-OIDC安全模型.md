# OAuth2/OIDC 安全模型

本文件描述 ADR 0004、0008、0010、0015 的目标契约，并单列现有偏差；不要把目标配置当作已实现的接入参数。完整规格见 [Issue #69](https://github.com/abluepoint/summer/issues/69)。

## 框架与应用职责

| 位置 | 职责 | 会话/响应 |
| --- | --- | --- |
| Authorization Server 链（Order 1） | 授权、OIDC 元数据、令牌签发及协议退出 | 按需登录会话；原生 OAuth2/OIDC 响应 |
| Resource Server 链（Order 2，目标） | 校验 API Bearer JWT，scope 映射 `SCOPE_*` | `STATELESS`，不从 HttpSession 恢复 API 身份；401/403 用统一错误 |
| 登录/公开应用链（Order 3） | Spring form login、saved request、CSRF、登录会话 | `IF_REQUIRED`，登录/CSRF 保持原生语义 |
| 业务方法 | `@PreAuthorize` 最终授权 | read/write 独立，Customer 与 principal 无绑定 |
| AICT BFF（独立产品） | Product Session、服务器端换票、CSRF 与 Origin 校验 | 产品 Cookie，不改变框架默认 Bearer 语义 |

能力实现留在 Security，装配留在 Security autoconfigure；应用身份、企业权限、固定跳转和产品端点白名单由应用拥有。禁止用“第一个 AuthenticationSuccessHandler Bean”隐式决定所有消费者的登录行为。

## 当前实现边界

`summer-security` 只提供 Spring Security 主体、权限、Bearer 资源校验和协议错误处理；`summer-security-autoconfigure` 负责按条件装配过滤链。资源链固定使用 `STATELESS`，只从配置的保护路径匹配 Bearer API，不从 `HttpSession` 恢复 API 身份。授权服务和登录链只在应用显式启用时装配，并提供原始 `/csrf` 端点给浏览器表单登录。

应用可以通过自己的 `RegisteredClientRepository`、`UserDetailsService`、JWK、授权服务设置和过滤链接管产品策略；自动配置只在应用没有提供这些 Bean 时创建通用内存实现。这样 Sample 可以保留演示用的内存用户/客户端，未来的 `summer-ui` public client 或其他产品可以拥有自己的注册策略，框架不会读取 Product Session、企业成员或 Customer 业务上下文。

## 可替换存储契约

认证存储的预重构边界由 `summer-security-autoconfigure.storage` 中的六个窄契约组成：`SummerIdentityStore`、`SummerOAuthClientStore`、`SummerAuthorizationStore`、`SummerConsentStore`、`SummerSessionStore` 和 `SummerKeyStore`。它们分别适配 Spring Security 的 `UserDetailsService`、OAuth 客户端/授权/同意服务、Servlet `SecurityContextRepository` 及 JWK 选择器；业务模块不直接依赖内存类。

`summer.security.storage.identity`、`oauth-client`、`authorization`、`consent`、`session` 和 `keys` 的默认值都是 `memory`，用于保持当前 Sample 的演示行为。应用选择其他实现名（例如未来的 `mysql` 或 `redis`）时，必须提供相应 Spring Security 契约 Bean；缺少适配器或使用空实现名会在启动时失败。该选择只替换服务端存储适配器，不改变 OAuth/OIDC、PKCE、CSRF、退出或错误响应协议。浏览器 access token 和 ID Token 仍只在 Summer UI 内存中保存，私钥/JWK 适配器也不得通过业务响应或日志输出私钥材料。

资源端点的读写授权由业务端点声明的 scope 独立完成。`SummerSecurityContext` 只读取 Spring 原生 `SecurityContextHolder`，提供主体、authority 和 `SCOPE_*` 查询，不维护自建 ThreadLocal、产品会话或通用上下文袋。

## Summer UI public client（目标）

| 项目 | 约定 |
| --- | --- |
| client | `summer-ui`，authentication method `none`，无 secret |
| grant | 仅 `authorization_code`，强制 PKCE `S256`，无需 consent |
| scope | `openid profile customer.read customer.write`，按实际授予的 scope 控制界面 |
| callback / logout | `/oauth2/callback` / `/login?logout`，与公开 origin 一致 |
| 生命周期 | code 5 分钟；access token 与 ID Token 各 15 分钟 |
| 不启用 | refresh token、offline_access、password grant、client_credentials、动态注册、UserInfo |

Sample 的生产配置仅注册 public `summer-ui`，不再配置旧 `summer-client`。`public-client: true` 或 `client-authentication-method: none` 的注册不读取 secret，自动设置 authorization_code、PKCE S256、code 5 分钟、access/ID Token 15 分钟以及 post logout redirect；显式配置的 confidential client 仍沿用 basic/post 和应用声明的 grant。授权码和授权记录使用内存实现，应用重启后自然失效。

## 浏览器身份与登录

前端使用固定版本的 `oidc-client-ts` 完成 discovery、state/nonce、PKCE、callback、ID Token 校验与 RP-Initiated Logout。access token、ID Token 和认证状态仅保存在内存。sessionStorage 只保存短时单次 PKCE 交易及必要的非敏感恢复信息；成功、终止或 logout 后清除；不启用 silent renewal。

登录页先获取匿名、不可缓存、原始 JSON 的 `GET /csrf`，再以 form-urlencoded 提交标准 `POST /login`。Spring Security 恢复 saved authorization request；失败进入 `/login?error`。不增加 JSON 密码登录端点。

身份显示按 `name → preferred_username → sub`，不加入 Customer/email/role/authority 业务 claims。API 使用 access token；界面依据实际授予的 scopes，后端方法授权是最终裁决。401 最多重启一次 PKCE，恢复视图/草稿但不重放写操作；403 不触发登录。

## 登录会话与退出

登录 session 闲置 30 分钟过期，无 remember-me、并发 session 上限或自定义绝对寿命；保留 fixation protection。JSESSIONID 为 HttpOnly、SameSite=Lax，生产 HTTPS 设置 Secure，本地 HTTP 不设置。

Summer Sample 用 `server.servlet.session.timeout=30m` 配置 inactivity 超时。Cookie 的 Secure 属性由 `SUMMER_SAMPLE_SESSION_COOKIE_SECURE` 控制，本地 HTTP 默认 false；通过 HTTPS 对外部署时必须设为 true，并保持代理与应用的公开 origin 一致。Spring Security 登录时更换 session ID。授权服务默认使用内存授权记录；生产接入应提供持久化 `OAuth2AuthorizationService`。

退出先清理浏览器认证/PKCE 状态，并以仍在局部调用上下文中的 ID Token 发起 OIDC RP-Initiated Logout；后端销毁登录 session、删除 Cookie，返回已注册的 `/login?logout`。退出或 session 过期不意味着已签发 access token 立即失效，它在自身 15 分钟期限内仍可能可用；本目标不增加 revocation/introspection。

服务端 `/connect/logout` 只接受经授权记录验证的 ID Token 和已注册的 post logout URI。无效 hint 返回 400，不把失败请求重定向到任意地址；浏览器在退出调用失败时仍须清理本地认证状态，具体浏览器流程由 #86 验收。

## 同源路由与响应

| 浏览器请求 | 目标 | 响应语义 |
| --- | --- | --- |
| `GET /login`、`/oauth2/callback`、`/customer/**`、官方页面深链 | SPA / index.html fallback | 前端页面 |
| `POST /login`、`/csrf` | Spring 登录链 | form login / 原始 CSRF JSON |
| `/oauth2/**`、`/.well-known/**`、`/connect/**` | Spring 协议端点 | 原生协议，不加业务 Bearer 或 envelope |
| `/api/**` | 资源服务与业务 Controller | 统一响应；成功 DELETE 204 例外 |
| Actuator、文件、字节流、Range | 各自处理链 | 原生管理/资源响应 |

开发浏览器 origin 为 `http://127.0.0.1:5173`，Vite 代理到后端 `http://127.0.0.1:8080`；UI 集成 issuer 使用浏览器 origin，后端独立测试保留后端 origin。生产使用一个外部 HTTPS origin，issuer/redirect/logout 外配，不增加 wildcard CORS。

当前 MVC advice 的显式路径旁路只有 `/oauth2/`、`/.well-known/`、`/actuator/`；新 `/csrf` 应显式 RawResponse，`/connect/**` 和 logout 必须结合真实安全过滤链测试，不能假定所有协议响应都经过 MVC advice。

## 生产扩展责任

框架授权服务默认关闭，启用后必须有客户端配置。Sample 的内存用户/客户端/authorization/consent 与临时 JWK 属于演示设施。生产应用负责持久化 RegisteredClientRepository、authorization/consent、企业身份源与轮换 JWK/KMS/HSM；不在框架里加入 IAM 产品模型。

安全事件只记录必要主体标识、认证类型和请求关联，不记录密码、secret、原始 token。完整测试门禁见 [迁移计划](../guides/summer4迁移计划.md)。
