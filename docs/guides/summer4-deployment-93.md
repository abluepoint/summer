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
| #70 TS-QA | 后端、前端、模块边界、真实 PostgreSQL 浏览器和静态代理各自报告，不互相替代 |

## 当前阶段证据与待合并项

在 #91 页面模式并入前，#93 的前后端 username 合同红灯已复现，修正后
MockMvc 专项 1/1、前端单测 17/17 通过。Vite 与 Caddy 路由测试各 12/12 请求
通过；前端 typecheck、stylelint、production build 通过，lint 为 0 错误、
40 条既有官方示例警告。Caddy 配置在 `caddy:2` 中通过解析和启动。上述路由
测试使用假后端，完整真实 PostgreSQL 浏览器 E2E 待 #91 并入后另行记录。

## 回滚

将 #93 的部署示例、Vite 代理配置和 Mock 开关作为一个独立变更回退，恢复上版
前端构建与反向代理配置；issuer、redirect 和 logout URI 必须随公开 origin 一起
原子恢复。username 规则回退会破坏本版已接受的点号、小写和首尾约束，发布前需
核对调用方与数据；Sample 的 `create-drop` 回滚只适用于可丢弃数据，真实持久
数据库由业务应用编制迁移和反向迁移。无 JWK/authorization 持久化的 Sample
重启会使旧会话和令牌失效，不能把此行为当正式撤销机制。
