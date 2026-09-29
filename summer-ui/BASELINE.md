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

回滚时删除本票新增的 `summer-ui` 目录即可，不影响 Gradle 模块、Summer
Sample 或其他后端能力。
