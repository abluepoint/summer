# T25 共享登录会话验收（2026-10-07）

本轮基于 `summer4` 的 `cb0a989` 工作树进行修复。实现边界见 [ADR 0029](../adr/0029-share-servlet-login-context-and-isolate-bearer-authentication.md)。历史 15 项通过仅覆盖索引和局部仓库，不能替代本轮真实会话链路结果。

## 修复及红灯

- 两个独立 Servlet 上下文、真实 Redis 和 HTTP Cookie 首先复现了跨节点 401、Cookie 写入未校验 CSRF。显式启用 Spring Session、接入 API 共享仓库后消除。
- 故障节点先暴露 Bearer 认证详情与 MVC 请求事件隐式读取 Session Redis。请求级仓库和 Bearer Session 隔离后，有效 JWT 携带旧 Cookie 在该节点仍可成功；错误或过期 JWT 不回退 Cookie。
- 索引不再从元数据重建 JWT 或覆盖时间；共享记录缺失/撤销时拒绝已有 Servlet 身份，续期不能重建缺失记录。Lua 原子更新防止并发续期覆盖撤销。
- 标准及 OIDC 注销在 Servlet 失效前撤销索引。真实签发 access token 仍按原 JWT 规则使用；不会因为登录会话注销而隐式失效。
- 完整 Sample 的真实浏览器 PKCE 复现 `JwtAuthenticationToken` JDBC 反序列化失败；定向 JDBC 往返测试取得相同红灯。明确类型映射后原始 claims、时间和密码因子保留，未授权类仍拒绝。

## 本次验证

所有 Gradle 调用由 `.git/summer4-review-20261005/gradle-execution.lock` 的 `fcntl.flock` 串行保护。

| 验证组 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| Sample 定向回归，含原索引、故障、Session/BFF、Cookie、PKCE、新 JDBC 用例 | 47 | 0 | 0 | 0 |
| Security 自动配置 | 12 | 0 | 0 | 0 |
| 完整 Sample 双进程与真实 Chrome | 7 | 0 | 0 | 0 |

Sample 47 项中，`SharedSessionNodesHttpTest` 占 14 项：真实 Redis、两个随机端口 Tomcat 上下文、跨节点 Cookie/CSRF、撤销与缺失记录、两个独立浏览器会话、空闲过期、并发续期/撤销、Cookie 属性、连接故障 503、有旧 Cookie 的有效/无效/过期 Bearer、resource-only 装配及 PKCE/OIDC 注销。该组使用最小测试应用和内存 OAuth，不冒充完整 Sample。

Security 12 项在本轮独立执行通过；最后组合 Gradle 调用因源码未再变化而复用其 UP-TO-DATE 结果，未计作另一轮新执行。最后 Sample 定向调用实际执行了 47 项（通配 `SessionControllerTest` 还匹配了 5 项 BFF 用例）。`bootJar` 构建成功。

完整应用验证从空的随机 MySQL schema 运行现有 Flyway，使用独立 Redis 容器、实际 JDBC OAuth 和持久化密钥，启动 18081/18082。真实 Chrome 访问 5173 的 Vite，关闭 Mock，由 18580 代理轮换节点：

1. UI 完成真实 public-client PKCE 登录并进入工作台，Token 保留内存。
2. 同一 Cookie 在节点 B 读取登录快照成功。
3. 实际停止节点 A，节点 B 继续接受该 Cookie。
4. 重启节点 A，原 Cookie 仍可读取快照。
5. 浏览器调用会话列表，缺 CSRF 撤销返回 403；带 CSRF 撤销成功，Cookie API 随后 401，原有效 Bearer 仍 200。
6. 新浏览器上下文在节点 B 重新登录。
7. 切换节点 A 完成真实 OIDC 注销，Cookie API 返回 401。

登录及注销使用真实 UI/OIDC 代码；会话列表和撤销使用浏览器 `fetch` 访问真实 API，没有替换后端响应。此记录不声称验证了会话页面所有按钮、权限提示或布局。

## 环境与清理

运行凭据仅从本地 Kubernetes Secret 进入进程内存/环境；没有写入文件、命令参数或验收输出。Redis 集成测试使用 DB15 和随机前缀，仅删除本轮前缀；完整应用使用独立 Redis 容器，未停止共享 Redis。

最后验证的随机 MySQL schema 已删除并查询确认不存在，独立 Redis 容器已删除，18081/18082 无监听；浏览器、Vite 和代理均正常关闭。业务数据库及原 18080 未变动。

机器可读证据位于本地 `.git/summer4-review-20261005/t25-20261007/`：`sample-final-counts.json`、`security-counts.json`、`browser-red-jdbc.json`、`browser-result.json`、`runtime-result.json`。浏览器脚本为 `summer-ui/scripts/verify-t25-shared-session-browser.mjs`；它要求启动器提供真实两节点、进程内凭据和 stdio 停机/重启控制，不能脱离这些条件当作独立通过证明。

## 未完成的合并门禁

- T36 的缓存、事件、任务、备份/恢复及全项目升级门禁未由本轮验证，保持待验收。
- 本轮未执行 T18/T19/T32/T35 的真实浏览器业务流程；不得用会话登录成功代替这些场景。
- 关闭 Mock 后，工作台演示接口 `overview-content`、`popular-contents`、`content-percentage`、`announcement` 返回 404，未在本轮修改或计为通过。
- 真实 Chrome 验证使用 HTTP；生产 HTTPS Cookie、完整授权拒绝矩阵、会话页面所有交互和不同版本制品滚动升级仍需对应环境验证。现有 Secure Cookie 回归通过不等于完整生产代理验收。
- 本轮未逐一注入 Redis 保存超时/序列化故障，连接失败诊断的通过不能扩大为所有故障模型通过。
