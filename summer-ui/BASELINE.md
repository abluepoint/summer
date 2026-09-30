# 官方基线记录

| 项目 | 固定值 |
| --- | --- |
| 上游仓库 | `https://github.com/arco-design/arco-design-pro.git` |
| 上游版本 | `v2.8.1` |
| 上游提交 | `bb6aebcceca6b294b438be4c13dc7328ee80d70b` |
| 模板 | `arco-design-pro-vite` |
| 包管理器 | Yarn 1，锁文件为 `yarn.lock` |
| Summer 需求 | GitHub #84，父方案 #70，UI 规格 #69 |

## 生成与校验

基线由官方仓库的 `scripts/vite.js` 从同一提交生成，再加入 Summer 自有
导航和本地化资源。当前 `src/routes.ts` 保留生成模板中的官方页面清单，
只增加 `Arco Pro 示例` 菜单分组；官方页面的直接 URL 不改变。

本票验收命令：

```bash
yarn install --frozen-lockfile
yarn typecheck
yarn lint
yarn lint:style
yarn build
```

## 2026-09-30 安装与文案续验

官方模板在上述固定提交中的 `prepare` 为 `husky install && husky add`。
在 `summer-ui/` 的 Git worktree 内执行普通冻结安装时，依赖安装完成后
该步骤报 `.git can't be found`。Summer UI 移除自动 `prepare`，保留
`pre-commit` 手动检查脚本，不修改仓库根或用户的 Git hooks。

删除首次失败安装产生的 `node_modules` 后，重新执行
`yarn install --frozen-lockfile` 成功；`yarn.lock` 的 Git blob 与提交版本
一致（`071ee46af968164b3c01e180369ad9394930773b`）。`typecheck`、
`lint:style`、19 项前端测试、production build、Mock 边界与 Vite/Caddy
各 12 条代理分流均通过。`lint` 为 0 错误、40 条既有警告。
双语 username 提示现与 `3–64` 位、仅小写、允许中间点号及首尾字母或
数字的最终正则一致；运行时字典读取检查覆盖 `en-US`、`zh-CN` 与
`summer.demo`、末尾点号、大写字母样例。导航栏的语言选择通过
`setLang` 更新上下文，CustomerForm 从当前语言字典读取此提示。

审阅：Standards 轴无新增违反仓库约定的改动；Spec 轴对应 #84 安装、
单锁文件与 #69 AC01/AC05 的这两处遗漏已补齐。该续验未重新运行真实
浏览器登录流程；完整浏览器验收沿用 #93 的独立证据。

回滚时删除本票新增的 `summer-ui` 目录即可，不影响 Gradle 模块、Summer
Sample 或其他后端能力。
