# Summer UI

Summer UI 是独立的 React/Vite 前端，基于官方 `arco-design/arco-design-pro`
`v2.8.1` 生成。官方模板在基线生成时固定到提交
`bb6aebcceca6b294b438be4c13dc7328ee80d70b`，详见
[`BASELINE.md`](./BASELINE.md)。本目录不复制或依赖仓库外的旧 UI 项目。

## 开发

使用仓库提交的 `yarn.lock` 和 Yarn 1 安装依赖：

```bash
yarn install --frozen-lockfile
yarn dev
```

本子项目安装依赖时不会自动安装 Git hooks；需要本地检查时运行下方检查命令。
官方模板的 `prepare` 会在安装时执行 `husky install` 和 `husky add`，
但本目录位于仓库子目录，在 Git worktree 中执行会因找不到 `.git` 而失败，
也可能改动仓库根的 hooks。因此 Summer UI 移除了该自动步骤。

检查和生产构建：

```bash
yarn typecheck
yarn lint
yarn lint:style
yarn test
yarn build
yarn test:proxy
yarn test:proxy:production
```

Summer UI 由 Node/Vite 独立构建。Gradle 不调用前端包管理器，也不把
`summer-ui/dist` 嵌入 Summer Sample JAR。浏览器只访问 Summer UI 的单一
origin；开发时 Vite 在 `5173` 提供页面并把认证、协议和业务请求代理到
内部 Spring 服务（默认监听 `8080`），因此 `8080` 不是用户登录入口。

## 导航基线

- `Customer` 位于首个业务导航组，当前保留 `/customer/drawer`、
  `/customer/modal` 和 `/customer/page` 三种交互模式；它们共用公开的
  `/customer/detail/:username` 详情深链接，Sample 示例用户名为 `summer.demo`。
- `/oauth2/callback` 完成 OIDC PKCE 回调，不在示例导航中展示。
- 官方示例路由仍使用其原始 URL，菜单统一放在 `Arco Pro 示例` 下，便于
  对照上游页面和开发 Mock。
- 默认语言为简体中文，导航栏保留 `zh-CN` 和 `en-US` 切换。

浏览器真实登录、同源代理、令牌存储、验收和回滚说明见
[`AUTH.md`](./AUTH.md)。部署路由、外部 issuer 和完整验收见
[`docs/guides/summer4-deployment-93.md`](../docs/guides/summer4-deployment-93.md)。
官方示例可以使用上游开发 Mock；Summer 业务和安全路由没有 Mock 回退。
