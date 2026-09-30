# Summer 4 #80 协议验收补证

基线为 `0d343d1`。本次只补 Issue #80 与 #69 AC26–32 的后端协议验收，不改变其他 ticket 的业务行为。测试通过 Spring MockMvc 进入真实 Security 过滤链和 Authorization Server；`summer-sample/src/test/resources/application.yml` 另外注册 legacy confidential client 作为兼容性夹具，生产 Sample 的 `src/main/resources/application.yml` 默认仅配置 public `summer-ui`。

| 条款 | 直接证据 |
| --- | --- |
| AC26 | `productionSampleConfigHasNoLegacyClient` 核对生产配置只有 `summer-ui`；`registersPublicSummerUiPolicy` 断言 public `none`、唯一 `authorization_code`、无 secret、精确四个 scopes、无需 consent。 |
| AC27 | `SummerUiClientPolicyTest` 验证 `summer-ui` 不能借 confidential 配置获得 secret/client_credentials，其他显式 confidential client 仍支持 basic/post 认证及 authorization_code、client_credentials、refresh_token。 |
| AC28 | `completesAuthorizationCodePkceAndKeepsProtocolResponsesRaw` 真实交换无需 secret、不签发 refresh token；`rejectsPlainPkceForSummerUi` 在已登录会话拒绝 plain 与缺失 challenge；`refusesClientCredentialsAndRefreshGrantWithoutBusinessEnvelope` 拒绝其他 grant。 |
| AC29 | 解码实际 ID Token 验证 `sub`、`preferred_username`、`name`，排除 Customer/email/role/authority 等业务 claims。 |
| AC30 | 授权服务记录中的 code 实际签发与到期时间相差 5 分钟；已签发 access/ID Token 的 `exp` 均在当前时间约 15 分钟范围。 |
| AC31–32 | 匿名 `/csrf` 是原生 JSON 且 `no-store`；实际 CSRF 表单登录下，缺失和错误 CSRF 均为 403 且不建立认证；重新获取 CSRF 后错误凭据回 `/login?error`，其后正确登录恢复 saved authorize request 并发出 code。 |

协议端点断言检查 token、CSRF 和拒绝 grant 的响应均无 Summer 业务 `success` envelope。`/api/customers` 仍按 Bearer scope 访问，沿用现有业务响应与权限测试。测试只在内存中解析一次性 code、token 和 CSRF，命令输出与文档不记录原值。

## 验证与回滚

先加入 `rejectsConfidentialSummerUiRegistration`，旧实现下 1 test / 1 failure；原因是保留名可绕过 public client 策略。修复后，聚焦命令 `./gradlew :summer-security-autoconfigure:test --tests '*SummerUiClientPolicyTest' :summer-sample:test --tests '*PublicClientPkceIntegrationTest'` 为 11 tests / 0 failures / 0 errors / 0 skipped。全量门禁 `./gradlew check --rerun-tasks` 为 150 tests / 0 failures / 0 errors / 0 skipped，含 `verifyModuleBoundaries`；在最后一次测试改动后重新执行。测试为后端 MockMvc 协议验收，本轮未重新运行真实浏览器。

回滚时在尚未发布的分支撤销本 ticket 提交即可；没有数据库或配置格式迁移。回滚会恢复 `summer-ui` 保留名可被错误注册成 confidential client 的缺口，因此应先保留对应负例测试。
