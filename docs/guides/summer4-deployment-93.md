# Summer 4 单 origin 部署与 #93 验收

## 构建和配置

后端从仓库根目录执行 `./gradlew :summer-sample:bootJar`；前端在 `summer-ui/`
执行 `yarn install --frozen-lockfile --ignore-scripts`、`yarn build`。两套构建互不调用。
Sample JAR 不包含 `summer-ui/dist`。前端使用官方 Arco Pro v2.8.1 模板，固定提交
`bb6aebcceca6b294b438be4c13dc7328ee80d70b`、Yarn 1 和唯一的 `yarn.lock`；
上游路由清单和版本来源见 `summer-ui/BASELINE.md`。

| 场景 | 浏览器 origin / issuer | 后端地址 | redirect URI | post-logout URI |
| --- | --- | --- | --- | --- |
| 后端独立测试 | `http://127.0.0.1:8080` | `http://127.0.0.1:8080` | 测试配置固定值 | 测试配置固定值 |
| 本地联调示例 | `http://127.0.0.1:5193` | `http://127.0.0.1:8093` | `http://127.0.0.1:5193/oauth2/callback` | `http://127.0.0.1:5193/login?logout` |
| 生产 | `https://<public-host>` | 内部 Spring 地址 | `https://<public-host>/oauth2/callback` | `https://<public-host>/login?logout` |

本地联调需要对同一个 Sample 进程设置
`SUMMER_SAMPLE_ISSUER`、`SUMMER_SAMPLE_REDIRECT_URI`、
`SUMMER_SAMPLE_POST_LOGOUT_REDIRECT_URI` 为表中的外部地址，并用
`SUMMER_BACKEND_ORIGIN=http://127.0.0.1:8093 VITE_ENABLE_MOCK=false yarn dev --host 127.0.0.1 --port 5193 --strictPort`
启动 Vite。生产设置相同三项为公开 HTTPS origin，并把
`SUMMER_SAMPLE_SESSION_COOKIE_SECURE=true`。数据库由 `SPRING_DATASOURCE_URL`、
`SPRING_DATASOURCE_USERNAME` 和安全注入的 `SPRING_DATASOURCE_PASSWORD` 提供。
账号密码、Token 和密钥不要写入 Vite 变量或部署文件。

生产示例为 `summer-ui/deploy/Caddyfile`。设置 `SUMMER_PUBLIC_HOST` 为公开域名，
`SUMMER_UI_DIST` 为前端构建产物的绝对路径，`SUMMER_BACKEND_ORIGIN` 为后端内部
HTTP origin，然后运行 `caddy run --config summer-ui/deploy/Caddyfile`。Caddy
使用公开域名自动提供 HTTPS；证书和 DNS 按部署环境准备。反向代理仅把
`POST /login`、`/api/**`、`/oauth2/**`（排除 SPA callback）、
`/.well-known/**`、`/connect/**` 和 `/csrf` 发往 Spring。`GET /login`、
`/oauth2/callback`、`/customer/**`、官方页面深链接及未知前端路径由静态文件和
`index.html` fallback 处理。前端的 OIDC authority 默认取浏览器 origin；
只有部署在等价公开地址时才设置 `VITE_OIDC_AUTHORITY` 等覆盖变量。

## 可重复路由验证

`yarn test:proxy` 启动独立的 Vite 和假后端，对五条 SPA GET 与七条后端请求
逐条断言实际分流，测试时关闭 Mock。`yarn test:proxy:production` 在 `yarn build`
后通过 Caddy 容器加载同一份生产配置，对同样的 12 条请求做实际 HTTP 验证；
这需要可用的 Docker daemon 和 `caddy:2` 镜像。独立的假后端只验证代理路由，
不能代替真实 Spring/PostgreSQL 浏览器验收。

## 合同修正与追溯

| 来源 | #93 实现和验收 |
| --- | --- |
| #69 AC61–63；ADR 0014/0015 | Vite 同源代理、Caddy 可运行配置、deep link 与 callback 优先级、外部 issuer 矩阵、独立构建检查 |
| #69 AC08/AC13；`CONTEXT.md` Customer username；ADR 0016 | `CustomerParameterConfiguration` 与共享 `CustomerForm` 均使用 `^[a-z0-9](?:[a-z0-9._-]{1,62}[a-z0-9])$`；MockMvc 和前端单测覆盖大小写、点、首尾符号及 3/64/65 长度边界 |
| #69 AC07/AC11 输入保真 | 新建表单不裁剪 displayName；email 与 displayName 原样校验、提交和存储 |
| #69 AC03/AC55 | Sample 种子用户名为 `summer.demo`；三个 Customer 模式从各自列表进入 `/customer/detail/:username`，保留查询参数和返回来源；Drawer 显示六维取舍与模式入口，Modal/Page 保留各自说明 |
| #69 AC41–45；ADR 0023 | 401 后仅保存白名单内的表单草稿与 Modal/Drawer 模式，重新登录后恢复编辑且不自动重放写请求；短会话重新登录和内存 Token 边界由真实浏览器验证 |
| #70 TS-QA | 后端、前端、模块边界、真实 PostgreSQL 浏览器和静态代理各自报告，不互相替代 |

## 三种交互模式对比

| 模式 | 操作效率 | 列表上下文 | 表单容量 | 390px 窄屏 | 键盘与焦点 | 导航与分享 |
| --- | --- | --- | --- | --- | --- | --- |
| Drawer | 同页打开，适合短表单 | 关闭后保留筛选与分页 | 侧边宽度有限 | 抽屉铺满可用宽度 | 自动聚焦；脏草稿离开确认 | 列表 URL 保留状态，详情使用统一 username 深链接 |
| Modal | 同页居中处理，适合短任务 | 遮罩下仍能辨认列表 | 空间最有限 | 宽度随视口收缩 | Tab 焦点锁定；Esc/遮罩与脏草稿确认 | 列表 URL 保留状态，详情使用统一 username 深链接 |
| Page | 独立页面，适合更长表单 | 离开列表但返回时恢复完整 URL | 可用空间最大 | 表单卡片收缩，无页面横向溢出 | 键盘提交、首错聚焦与导航拦截 | 创建/编辑路由可直达和刷新；`from` 保留来源 |

三个模式共用 `CustomerForm`、Customer API、username 校验和错误映射；选择取决于
表单长度、列表参照需求与是否需要直接分享编辑入口。以上是实现行为与浏览器
验收观察，非用户绩效量化结论。

## 验收证据

2026-09-30 在包含 #91 的 `summer4` 基线 `ce9547f` 上验证：

| 门禁 | 结果 |
| --- | --- |
| `./gradlew check :summer-sample:bootJar --rerun-tasks` | 142 tests，0 failure/error/skipped；执行 `verifyModuleBoundaries`、`ModulithArchitectureTest`、`summer-security-autoconfigure:test`；Gradle 未调用 npm/Yarn，JAR 不含前端 dist |
| `yarn test`、`typecheck`、`lint`、`lint:style`、`build` | 19 tests，均通过；lint 0 error，40 条官方示例基线 warning |
| `yarn test:proxy`、`test:proxy:production` | Vite 与 Caddy 各 12/12 条真实 HTTP 分流通过，包括 callback、POST login、SPA deep link |
| `yarn test:mock-boundary` | 官方示例 Mock 开关有效；Summer API 始终走后端 |
| `test:e2e:auth`、`recovery`、`customer`、`delete`、`customer-create`、`customer-edit`、`customer-modal`、`customer-page`、`quality` | 真实 PostgreSQL + Spring + Mock 关闭的本地同源 Vite，九套浏览器脚本通过；含 OIDC PKCE、失败分类、无写请求重放、三种 Customer 模式、桌面/390px、控制台与网络、同源及 Token 存储检查 |
| `test:e2e:session-expiry` | 独立的 15 秒服务端 session 配置；等待服务端超时粒度后，新登录流程和原路径恢复通过 |

真实浏览器脚本在本地独立的 `127.0.0.1:5193` 与 `127.0.0.1:8093` 进程运行，
数据库是临时 PostgreSQL。受控 400/401/403/409/500 和网络故障由浏览器拦截，
正常 CRUD 与种子数据读取落在真实后端。`caddy:2` 容器测试使用构建后的 dist
及假后端，仅证明代理配置的路径分流。测试脚本创建的 Customer 已清理。

## 审阅与发布边界

以 `ce9547f` 为固定审阅点，按仓库规矩、`CONTEXT.md`、ADR 0014/0015/0016/0023
检查了模块界线、公开 username 合同、OIDC 单 origin、会话与草稿存储边界；
再按 Issue #93 和 #69/#70 验收项核对路由、三模式、构建与部署测试。
`git diff --check` 无空白错误；标准轴和需求轴均未发现阻断项。
此次只提供部署配置与本地验收证据，未执行公开域名部署。实际发布时仍须验证
目标环境 DNS/TLS、反向代理、外部 issuer/redirect 和持久数据库迁移。

## 回滚

将 #93 的部署示例、Vite 代理配置和 Mock 开关作为一个独立变更回退，恢复上版
前端构建与反向代理配置；issuer、redirect 和 logout URI 必须随公开 origin 一起
原子恢复。username 规则回退会破坏本版已接受的点号、小写和首尾约束，发布前需
核对调用方与数据；Sample 的 `create-drop` 回滚只适用于可丢弃数据，真实持久
数据库由业务应用编制迁移和反向迁移。无 JWK/authorization 持久化的 Sample
重启会使旧会话和令牌失效，不能把此行为当正式撤销机制。
