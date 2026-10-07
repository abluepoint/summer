# ADR 0029：共享 Servlet 登录上下文并隔离 Bearer 认证

- 日期：2026-10-07
- 状态：接受
- 关联：ADR 0015、ADR 0024；T25 / T36

## 背景

Redis 会话索引只存放管理元数据，不能代替完整的 Servlet SecurityContext。此前资源服务器链没有接入共享仓库，Cookie API 缺少 CSRF 校验；从索引重建身份还会丢失密码认证因子并改写 JWT 的时间。真实浏览器 PKCE 另外暴露了 Sample 本地登录的 JwtAuthenticationToken 无法从 JDBC OAuth 授权记录读回的问题。

## 决策

1. Sample 在 `summer.security.storage.session=redis` 时用 `@EnableRedisHttpSession` 显式启用现有 Spring Session Redis 依赖。内存模式保持原行为；不再使用旧 `spring.session.store-type`。命名空间使用 `spring.session.data.redis.namespace`，默认 `summer:servlet-session`。
2. 完整 SecurityContext、CSRF 与 SavedRequest 由 Spring Session 保存。会话索引只允许或拒绝已有上下文，不签发身份。任一 Cookie 认证读取均检查共享索引；缺失、过期或 REVOKED 均拒绝。正常读取按空闲窗口续期；Lua 条件更新禁止并发旧写复活已撤销记录。
3. API 链显式使用共享仓库。有 Authorization Bearer 头的请求仅使用请求级上下文，禁止访问或创建 Servlet 会话；JWT 仍由资源服务器验证，无效/过期 Bearer 不回退 Cookie。认证详情和 MVC 请求事件也不得隐式触发 Session Redis 读取。
4. 带浏览器会话 ID 的 Cookie API 非安全方法必须校验 CSRF。Bearer API 独立校验 JWT；登录表单和标准注销维持 Spring Security 的 CSRF 保护。
5. 标准注销和 OIDC 注销在 Servlet 会话失效前先撤销共享索引。登录会话撤销不撤销已经签发的 access token，也不改变其原始 `iat/exp`。
6. Redis 会话故障在 Session Filter 外层映射为 503、`SUMMER_SESSION_STORE_UNAVAILABLE` 和 requestId；纯 Bearer API 不依赖登录会话存储可用性。默认 Cookie 为 JSESSIONID、Path=/、HttpOnly、SameSite=Lax，Secure 由显式配置或安全请求决定；空闲期默认 30 分钟。
7. JDBC OAuth 映射器保留 Spring Security 类型校验，仅补充 Sample 本地登录所需的 JwtAuthenticationToken、Jwt 和四种 JDK 不可变集合具体类型。显式构造映射保留原始 claims、Token 时间和密码因子，不允许任意类反序列化。

## 边界与后果

此决策补充 ADR 0015 的服务端共享登录实现，不改变浏览器 public-client PKCE、内存 Token、无 refresh token 的契约。Sample 本地表单身份仍是开发桥接，不能当作生产外部身份源。

Redis 存储模式需要可用的 Redis；共享会话使用 Java 序列化，滚动部署需保持类和序列化兼容。当前索引 Lua 涉及两个 Key，部署验证范围为单实例 Redis，不承诺 Redis Cluster 跨槽支持。完整 T36 的缓存、事件、任务、备份和升级门禁另行验收。
