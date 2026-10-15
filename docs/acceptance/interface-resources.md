# T14 接口资源目录真实验收

本脚本组通过真实 MySQL、独立 Redis、候选 Spring Boot JAR、OAuth 登录和 Chrome 验证接口资源目录。没有 HTTP 拦截、浏览器响应模拟或生产测试端点。外部 fixture JAR 只在验收 JVM 的 PropertiesLauncher classpath 中加载，不属于发布制品。

## 固定输入和隔离边界

本次候选为 `.git/summer4-closeout-20261015/runtime/candidate-4c81/candidate.jar`，SHA-256 为 `4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae`。脚本启动前验证摘要。测试 fixture 使用该 JAR 内的类和依赖编译，不依赖共享工作树正在变化的编译产物。

在仓库根目录运行，依赖本机已配置的 JDK 21、Node 22、Google Chrome、Docker 和 `docker-desktop/local-base` 的 MySQL。凭据由进程内读取 Kubernetes Secret，仅通过环境或 stdin 使用，不写入回执或命令参数。不得把其他环境改成此验收环境，不得打印原始环境、Secret、Cookie、Token 或服务日志。

保留端口为 23081/23082（两个应用 JVM）、23083（独立管理监听器）、23573（Vite）、23580（真实后端代理）。启动前检查端口空闲。每轮创建随机 `summer_t14_*` schema、独立 Redis 容器和 namespace；不会改写共享业务 schema。Vite 使用独立缓存并禁用 HMR。

## 执行

```sh
set -e
export CATALOG_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t14-live/attempt-$(date +%Y%m%d-%H%M%S)"
python3 scripts/acceptance/interface-resources/build_fixture.py
python3 scripts/acceptance/interface-resources/run.py
```

`build_fixture.py` 生成 `.git/summer4-closeout-20261015/t14-live/interface-resource-fixture.jar` 和摘要；编译失败必须停止，不能继续使用历史 fixture。`run.py` 包含完整执行顺序。`finish.py` 用于独立重跑真实 Chrome 与不透明/冲突映射的检查（`--browser-only` 只重跑 Chrome），仍重新创建独立服务和数据库，不复用其他会话的身份或资源。

每次重跑前，应将已有 JSON/PNG 回执及当时脚本版本归档到唯一的 attempt 目录，不能覆盖原始失败证据。生产源码冻结；若真实候选出现缺陷，先报告失败阶段、状态码、requestId 和候选摘要，由实现负责人修复并重新冻结候选。

## 覆盖

- V34 真实 MySQL 首次迁移、不可变历史在重启后保留。
- 独立 management 上下文与实际健康 HTTP；目录字段、显式权限覆盖和静态/协议/业务/基础设施分类。
- 持久化角色和绑定下的真实表单登录、PKCE 授权码交换，目录/历史/详情/差异允许、403 与 401。
- 非法历史参数和缺失版本的错误码、请求关联 ID；协议认证、CSRF 与函数式业务默认拒绝。
- 两节点同一目录快照收敛、启动新增/移除/变更计数。
- 外部测试 Controller 按启动 phase 新增、移除、替换；原自动授权不能由新 handler 继承，旧绑定和历史不被删除。
- 实际管理页历史、比较、快照、非法比较防护；停止实际后端后的 502 和重启恢复；真实无权限主体的页面拒绝。
- 不透明 HandlerMapping 显式显示不完整；冲突路由使启动检查明确失败。

此处的 fixture 是真实服务中的受控测试类，用于改变启动映射，不是生产接口目录实现。`result.json` / `finish-result.json` / `browser-result.json` 记录实际执行检查；失败和未执行项分别报告。浏览器请求记录只保留 method/path/status，不保存凭据和业务请求体；截图只在登录完成且没有密码输入框时生成。

`protocol_probe.py` 不加载任何外部 fixture JAR，单独验证原票协议/基础设施可配置标记。默认仍使用固定4c81候选；协调完成新候选后可通过 CATALOG_CANDIDATE_PATH 和 CATALOG_CANDIDATE_SHA256 显式指定匹配的新制品。当前4c81在此检查为真实红灯，不能把其他检查通过解释为本票READY。

## 清理与证据范围

finally 停止本轮 JVM、浏览器与 Vite，删除本轮随机 schema 和 Redis 容器，检查所有保留端口释放。`cleanup.json` 是回读结果，不能仅凭进程退出认定资源已清理。若清理失败，先定位本轮 resources.json，不要用全局容器/数据库清理命令。

本次回执只绑定上述候选摘要和记录的前端源码。主任务另行处理的 PostgreSQL 大文本映射兼容修复、后续新 JAR、全库构建和上游 T04 门禁，不由本次 MySQL 结果自动覆盖。最终逐项结果与原票映射位于 `.git/summer4-closeout-20261015/t14-live/` 交接文件。
