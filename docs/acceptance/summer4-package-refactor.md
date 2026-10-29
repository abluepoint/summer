# Summer4 包结构迁移验收

规格 #146 对应的实现任务 #147–#172 已完成集成及本页列出的验收。可复核的固定提交、逐批计数、Chrome 回执和清理结果见 [结构化验收证据](summer4-package-refactor-evidence.json)。GitHub Issue 状态没有变更。

## 交付与保留边界

- 集成分支：`codex/summer4-integration-20261028`。运行验收固定行为源码 HEAD 为 `8be4c82cc48315056839d7b7c4d86afe6271f171`；最终收尾仅补证据、文档及 45 个文件的末尾空行清理。
- 用户 `summer4` 分支原 HEAD 为 `29d027cd68a786417d771749a83350d9779d3929`，实际工作文件快照为 `62c6182e7052f3e5497b2c8f88227a4089f3638b`。实现以该工作文件快照为起点，既有工作没有作为本次新增成果重新编写。
- 原主工作区真实暂存区 SHA-256 保持 `4c9f4a28064ffb216ae36219e3c4da1c5eb95a2de100a209981d7394f7db83ad`。核对 1451 个基线文件，无实质内容差异；`gradlew.bat` 的原 CRLF 与 Git 规范化 LF 等价。
- 未修改既有 Flyway 迁移文件。物理表名、HTTP 路由、权限码、JSON 字段和幂等契约保持；Java 包名/类型名按迁移清单调整，不提供旧 Java 类的二进制兼容实现。
- 本统筹聊天加 4 个独立执行聊天，共 5 个；各聊天内部禁止子智能体。后续派发明确使用 `gpt-6.1-sol` / `high`。

## 结构结果

15 个 Gradle 模块保留。Sample 按职责划分 30 个外层模块，其中 6 个采用统一技术分层、24 个采用领域分层；Domain 不反向依赖 HTTP、Spring/JPA 实现。Starter 不承载运行实现，框架消费者只依赖明确的 API/SPI 或必要的最小命名接口。

81 项框架类型的唯一归属见 [framework-type-migration.tsv](../architecture/framework-type-migration.tsv)，完整规则见 [包结构与命名规范](../architecture/包结构与命名规范.md)。HTTP 输入、应用输入、查询、视图和持久实体按实际职责命名，避免复制同构输入状态。

最终受影响组合中，`PackageArchitectureTest` 8 项、`CustomerArchitectureTest` 4 项、`ModulithArchitectureTest` 10 项均通过，`verifyModuleBoundaries` 通过。包角色、依赖方向、81 项归属、顶层类型独立文件和模块循环均由实际检查覆盖。

历史接口资源采用 709 条精确类型迁移元数据，包含三个旧嵌套 HTTP 参数的 binary name；原历史快照保持不可变，不以保留旧实现类或宽泛文本替换伪造兼容。真实旧绑定迁包后继续授权，真正的方法替换仍拒绝。

## 执行结果

以下每行属于独立批次，不跨批相加为不同测试数量。计数只取本批生成的 JUnit suite，排除构建目录中的旧 XML。

| 批次 | 固定输入/范围 | 实际结果 |
| --- | --- | --- |
| 完整后端 `final-backend-check-green2` | `57fcd2a1e37561f73992e5c6de53acc211ae2090` | 828 tests / 0 failures / 0 errors / 0 skipped，192 suites，退出 0 |
| 最后修复组合 `final-review-delta` | `8be4c82cc48315056839d7b7c4d86afe6271f171` | 47 / 0 / 0 / 0，10 个本批 suites，退出 0 |
| 原生依赖阶段 `native-dependencies-first` | 真实 MySQL / 自有认证 Redis | 53 / 0 / 0 / 0，14 个本批 suites，退出 0 |
| UI `final-ui-green3` | Node 测试及完整 UI 命令 | Node 82 / 0 / 0 / 0，全部命令退出 0 |
| 源码 Chrome `run-yhnwyvpj` | `bootRun` + Vite，MySQL / Redis | 6 组通过，6 个浏览器正常关闭，清理失败 0 |

完整后端命令为：

```sh
./gradlew check --continue --rerun-tasks --console=plain --max-workers=1
```

该批耗时 1053.47 秒，源码输入 SHA 清单前后一致，47 个 actionable task 全部实际执行，无 FROM-CACHE。生命周期 UP-TO-DATE / NO-SOURCE 不计作新增测试执行。模块测试数为 common 1、common-autoconfigure 3、mvc 82、mvc-autoconfigure 18、data-jpa 7、data-jpa-autoconfigure 2、security 10、security-autoconfigure 18、sample 687。

完整 828 项批次早于最后的嵌套类型历史映射和身份输入去重修复。最终 47 项组合单列覆盖旧绑定真实 HTTP、生产映射配置、绑定语义、身份服务/Claims、重复用户、密钥以及三组架构检查；源码输入前后保持一致。没有将两个批次相加或称最终提交重跑了全部 828 项。

UI 同批完成 `test`、`typecheck`、`lint`、`lint:style`、`test:proxy`、`build`、`test:proxy:production`、`test:mock-boundary`。Node runner 三组为 59 + 11 + 12 = 82 项；其它功能脚本不另编固定测试数。Vite/Caddy 代理各验证 14 个 HTTP 请求、1 次 WebSocket upgrade、2 次 echo，并清理其临时子进程/容器；此受控回显后端批与真实业务 Chrome 批分开。

原生阶段的 MySQL Testcontainers 覆盖 Customer Flyway/审计、Dictionary、Task Flyway；既有本地 MySQL 9.7.1 使用各测试自有 UUID schema，覆盖配置、多租户、投影、持久成员授权、身份升级、组织及任务幂等。Redis 阶段覆盖共享会话 HTTP 5 项、双节点 HTTP 14 项及参数计数 HTTP 5 项；普通端口读取环境，显式故障端口保持。没有借用或清空共享 Redis DB15。

## Chrome 与协议边界

| 组 | 记录的原断言通过数 | 结果 |
| --- | ---: | --- |
| auth | 13 | 登录、并发会话、退出清理、state/nonce/verifier 拒绝通过 |
| customer-create | 11 | 新增及错误恢复通过 |
| customer-edit | 33 | 编辑及错误恢复通过 |
| customer-modal | 33 | 弹窗交互及错误恢复通过 |
| customer-page | 28 | 独立页面及错误恢复通过 |
| protocol | 55 | 原生 OIDC 响应及重定向检查通过 |

前五组执行现有原脚本，SHA 在运行前后相同。客户组的受控 400/401/404/409/网络中断属于页面恢复夹具，认证组另有受控客户端退出失败；这些不当作后端真实错误响应证据。四个真实新建客户均经真实 DELETE 清理，剩余 0；独立 SQL 核对本轮候选客户行数为 0。

原生协议组检查 discovery、JWKS、CSRF、授权码 PKCE 成功、授权码重放 `invalid_grant`、错误 verifier `invalid_grant`，确认协议对象未被业务响应信封包装。authorization/login/logout 真实重定向分别记录 3/1/1。只拦截已注册 callback 的 HTML 文档，防止前端抢先消费 code；发现、认证、token、logout 均访问真实后端。

全新验收库中的引导管理员没有客户操作权限，首次认证批因客户菜单不可见失败。通过真实能力/角色/绑定 API 为隔离验收账号创建 5 项客户权限后，原列表等待通过，未放宽产品授权。随后定位到控制器额外设置的 `BrowserServer.close()` 8 秒超时；改为直接启动并记录自有 Chrome 的 PID、出生时间和命令 SHA 后，最终六组全部正常关闭。此前失败回执保留，不改写为通过。

上述覆盖这六组既有回归及协议检查，不代表全部 Sample 页面和演示数据源已逐页验收。

## 审查修复与失败保留

Standards 从基线到 `8be4c82` 的最终审查无可行动发现。Spec 完整复审到 `57fcd2a` 发现的 P1/P2，已在最终修复及另一聊天的独立增量复审中确认解决，当前未解决发现为 0。

- 幂等指纹：六个真实碰撞红灯后，改为仅兼容外层 record 标签，不改字段原文；定向 HTTP 22 项通过。
- OAuth 管理：实际业务移入 Application/Domain，JDBC 留在适配层，输入不复制同构状态；HTTP/架构/条件装配 34 项通过。
- Quartz：在持久任务读取/启动前精确迁移两种旧作业类名，保留 JobData 和触发器；H2 JDBC 升级 4 项及现有任务 28 项通过。失败保留原因并向上暴露。该证据不扩展为 MySQL 多版本滚动集群升级结论。
- 最后身份修复：三个旧嵌套 HTTP 参数映射的有效红灯为 10/6/0/0，修复扩大批 43/0/0/0，配置加载再验 3/0/0/0；原 706 条映射不改，补至 709 条。

完整后端首批因 Sample 默认测试堆 `512m` 出现 `OutOfMemoryError: Java heap space`，退出 137/Gradle 1，并非通过批。只将 Sample 测试 task 堆上限改为 2g 后执行完整绿灯；生产 JVM、断言、跳过条件和上下文缓存大小未改。UI 前两批的空 root 夹具和构建顺序失败也保留，修复限于校验 SPA 根/入口及先构建再检查生产代理。

## 清理与证据位置

源码后端/UI 自有进程组已退出，两个自有服务端口已释放；专属 Redis 容器、MySQL schema/账号、临时凭据及浏览器密码副本已删除。清理脚本遇到已消失进程组的 `killpg(pid, 0)` 权限返回时，先枚举确认无组成员、无监听端口，再跳过不存在的组，未扩大终止范围。

清理后共享 `local-base` 的 MySQL/Redis Pod UID 与启动前相同且 Ready，测试临时 schema 前缀匹配 0。对 161 个生成产物扫描当前运行/依赖凭据，匹配 0。未重置、清空或停止共享依赖。

逐批门禁日志、冻结 JUnit、固定输入清单、独立审查、失败回执和完整清理回执保存在本机 Git 元数据目录 `.git/implement-20261028`；仓库中的 [结构化验收证据](summer4-package-refactor-evidence.json) 保留可共享摘要。最终收尾的 45 处变更仅删除文件末尾多余空行，另用差异检查确认没有行为内容变更。
