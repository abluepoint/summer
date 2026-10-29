# Summer4 包结构迁移验收

本页记录规格 #146、实现任务 #147–#172 的集成验证。当前处于 #172 最终验收阶段；以下结果只覆盖明确列出的检查，完整运行验收仍在进行。

## 输入与保留边界

- 初始用户分支：`summer4`，Git HEAD `29d027cd68a786417d771749a83350d9779d3929`；实际工作文件快照 `62c6182e7052f3e5497b2c8f88227a4089f3638b`。
- 集成分支：`codex/summer4-integration-20261028`；本轮结构门禁输入 HEAD `75c93d84f4829737b426de3bd9a5bb277173c09d`，另含 release/plugin 命名依赖修正及完整类型迁移元数据。
- 用户原工作区和真实暂存区保留；既有 Flyway 迁移、物理表名、路由、权限码、JSON 字段和幂等契约需保持兼容。
- 多聊天协作最多 5 个聊天，每个聊天内部不使用子智能体。

## 已取得的结构与兼容证据

`final-structure-green` 于 2026-10-29 退出码 0，实际执行 22 个测试，failures=0、errors=0、skipped=0；并完成所有模块测试源编译及 `verifyModuleBoundaries`。

| 检查 | 实际数量 | 证据范围 |
| --- | ---: | --- |
| `PackageArchitectureTest` | 7 | 30 个职责模块的包角色、Domain 依赖、框架 internal 消费、技术分层、Starter 无运行代码、81 项框架类型归属、顶层 Java 类型独立文件 |
| `CustomerArchitectureTest` | 4 | Customer 技术分层和依赖 |
| `ModulithArchitectureTest` | 10 | 模块循环、显式依赖和最小命名接口 |
| `InterfaceResourcePackageUpgradeHttpTest` | 1 | 首次扫描前装载旧 FQCN 历史绑定，迁包后同一 HTTP 资源仍可授权访问 |

复核命令（在集成源码根目录执行）：

```sh
./gradlew testClasses :summer-sample:test \
  --tests '*PackageArchitectureTest' \
  --tests '*CustomerArchitectureTest' \
  --tests '*ModulithArchitectureTest' \
  --tests '*InterfaceResourcePackageUpgradeHttpTest' \
  verifyModuleBoundaries --console=plain --max-workers=1
```

81 项框架清单以 [framework-type-migration.tsv](../architecture/framework-type-migration.tsv) 为单一来源。Sample 历史资源类名映射使用实际类型迁移元数据，不保留旧 Java 包作为兼容实现，也不重写既有历史快照。

## 审查与最终门禁状态

阶段 Standards/Spec 审查使用相同固定 HEAD `2ca6616`，发现并正在修复三项问题：外层 record 名称兼容处理误改字段内容、OAuth 管理业务仍滞留 HTTP 层、持久 Quartz 任务旧类名升级。最终审查必须覆盖这些修复及后续字典/参数集成增量。

完整后端检查、UI 测试/类型检查/lint/build、真实 MySQL/Redis、源码启动和 Chrome 验收尚待最终批次，不能用上述结构门禁替代。原生验收需使用自有临时 schema 和独立 Redis，不清理共享数据。

逐批门禁日志、实际 JUnit 计数、任务缓存状态及交接保存在本机 Git 元数据目录 `implement-20261028`；最终批次完成后在本页补齐可复核结果和清理回执。

## UI 完整批次

`final-ui-green3` 于 2026-10-29 实际执行并退出码 0。`yarn test` 中 Node test runner 三组共 82 项（59 + 11 + 12），failures=0、skipped=0；另完成各功能脚本断言，不把这些脚本另编造为固定用例数。

同批 `typecheck`、`lint`、`lint:style`、`build`、`test:proxy`、`test:proxy:production`、`test:mock-boundary` 均退出 0。开发 Vite 与生产 Caddy 代理分别验证 14 个 HTTP 请求、1 次 WebSocket upgrade 和 2 次 echo，临时子进程/容器均有清理回执。该代理批使用受控回显后端，不能替代真实业务浏览器验收。

前两批保留失败证据：SPA 夹具要求空 root，与既有启动占位内容不符；生产代理检查前未构建静态目录。修正仅调整夹具的 SPA 入口断言及运行前提，未修改页面行为。

## 原生依赖首批

`native-dependencies-first` 实际执行 53 项、failures=0、errors=0、skipped=0，输入源码在批次内未变化。只统计本批生成的 14 份 JUnit suite，排除构建目录中旧批次的 XML。

- MySQL Testcontainers：Customer Flyway/审计、Dictionary、Task Flyway；使用隔离容器。
- 既有本地 MySQL 9.7.1：配置、多租户、投影、持久成员授权、身份升级、组织及任务幂等测试；各测试拥有临时 UUID schema。
- 自有认证 Redis：`SharedSessionHttpTest` 5 项、`SharedSessionNodesHttpTest` 14 项、`SystemParameterCountHttpTest` 5 项；保留双节点真实 HTTP、显式故障端口及幂等/鉴权断言。

本批没有借用或清空共享 Redis DB15；测试连接本轮专属 Redis 容器。它是阶段原生证据，完整 `check` 和最终修复集成后的检查仍需单列。

## 阶段审查修复集成

- 幂等指纹：六个 HTTP 碰撞用例取得有效失败证据，修复后定向 HTTP 22 项通过；只兼容外层 record 标签，字段原文保持。
- OAuth 管理：API、Application、Domain 和 JDBC 适配职责拆分；HTTP、架构、条件装配定向 34 项通过。
- Quartz：两种旧持久化作业类在读取/启动前精确迁移，保留 JobData 和触发器；H2 JDBC 升级 4 项及现有任务 28 项通过。迁移/调度失败保留原因并向上暴露。

这些修复均已集成；各聊天扩大批中的 release/plugin Modulith 失败已由集成树既有命名接口修正处理，最终完整检查需再次验证该组合。
