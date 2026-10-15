# T10 系统参数发布与回滚验收

Issue #118 对应故事 5、6、49、50，依赖 T09 #117 已完成。本票贯通 MySQL、发布事件、二级缓存与参数版本历史；跨组织授权读取由 T19 另行验收。

逐项结果见 [JSON 收据](system-parameter-release-118-result.json)。后端实际验收固定候选 SHA-256 为 `442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453`，页面使用本次历史刷新修复后的源码。

## 验收结果

| 层次 | tests / failures / errors / skipped | 范围 |
| --- | --- | --- |
| 真实运行时与 HTTP | 18 / 0 / 0 / 0 | 本地 Kubernetes MySQL、两个独立 JVM、独立 Redis、真实 PKCE 和持久化读取 |
| 真实 Chrome | 12 / 0 / 0 / 0 | 草稿、发布、回滚、停用后直接回滚、刷新历史、非法输入和无权限 |
| 相关组件回归 | 9 / 0 / 0 / 0 | 新增历史刷新 4 项，保留投影与字段错误相关 5 项 |
| 既有最终全仓检查中的发布 JUnit | 2 / 0 / 0 / 0 | `SystemParameterReleaseHttpTest`，属于此前 688 项检查，本次未重复运行 |

真实运行器、Chrome、类型检查及构建均退出 0。浏览器无网络 mock、page error 为 0，242 个 UI 源输入首尾无漂移。各层用例单列，不将 HTTP 请求数、夹具设置、进程数或重复运行累计为通过数。

## 修复与失败保留

实际页面曾在停用成功后保留旧的版本历史，随后点击历史回滚提交过时的 `expectedVersion`，返回 409。原浏览器完成前 7 项检查后超时；当时 API 回读为 version 3、INACTIVE，截图和失败回执完整保留，不改判通过。

参数页现在在启停成功后刷新列表、事件及当前打开的同键历史，并等待这些刷新完成后解除写入锁；正在查看其他参数历史时不会切换目标，请求序号继续阻止迟到响应。专用组件先取得有效红灯 4 / 3 / 0 / 0，再完成相关 9 项绿色回归。

修复后的实际 Chrome 未手动刷新旧历史：停用得到 version 3 后直接回滚成功，刷新页面仍为 value 10、ACTIVE、version 4，历史包含 6 条草稿、发布、停用及回滚记录。恢复历史和非法输入截图已经原生目视检查。

同页还保留 T07 已验证的摘要投影缺失值显示修复及其专用组件测试。T07 的独立计数 HTTP 接口不属于本次提交，#115 仍保持 Open。并行 T16 的菜单、路由及客户页面修改也不属于本次提交，本次结果不能用作 T16 验收。

## 原验收条款

| AC | 实际证据 | 结果 |
| --- | --- | --- |
| 1 草稿不影响运行时读取 | 草稿为 20 时两 JVM 和公开读取仍为 10；发布后两节点均读取 20 | PASS |
| 2 发布、停用和回滚审计及缓存失效 | 实际 Redis 版本递增、节点读取变化、停用公开 404、回滚恢复；MySQL 审计和无参数值的已投递事件 | PASS |
| 3 并发冲突与旧版本回滚 | 同一期望版本双 JVM 恰好一 200、一 409，只递增一次；旧修订回滚恢复 | PASS |
| 4 管理历史与当前版本 | 实际 Chrome 保存草稿、发布、回滚、停用、直接再次回滚和 reload；最终 version 4、6 条历史 | PASS |
| 5 迁移、真实 HTTP/UI 及成功、拒绝、无效或故障 | 生产迁移、18 项真实后端与 12 项 Chrome；只读/匿名拒绝、非法草稿和陈旧版本错误、JVM 重启持久化 | PASS |
| 6 参数、响应、关联 ID 与敏感边界 | Unified 信封及匹配 requestId、SUMMER 错误码、字段错误与输入保留；敏感运行日志检测无命中 | PASS |
| 7 故事、设计、实现、缺陷和验收追踪 | 原票、受控脚本、原失败、组件红绿、实际修复回放、清理及源码摘要分批留存 | PASS |

## 执行与隔离

从仓库根目录运行，要求 JDK 21、Node 22、Chrome、Docker 和现有 `docker-desktop/local-base` MySQL 访问。使用本轮独立 schema、非 root 应用账号和独立 Redis 容器，生产数据库账号不作为网页登录身份。以下每个入口都应选择全新的证据目录，执行前确认 19081、19082、19083、19573、19580 空闲。

```sh
SUMMER_PARAMETER_RELEASE_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t10-live/new-protocol-replay" \
SUMMER_PARAMETER_RELEASE_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/candidate-442e609f/candidate.jar" \
SUMMER_PARAMETER_RELEASE_CANDIDATE_SHA256=442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453 \
python3 scripts/acceptance/system-parameter-release/run.py
```

```sh
SUMMER_PARAMETER_RELEASE_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t10-live/new-browser-replay" \
SUMMER_PARAMETER_RELEASE_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/candidate-442e609f/candidate.jar" \
SUMMER_PARAMETER_RELEASE_CANDIDATE_SHA256=442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453 \
python3 scripts/acceptance/system-parameter-release/browser_probe.py
```

协议证据位于 `.git/summer4-closeout-20261015/t10-live/protocol-attempt-01`，修复后页面证据位于同目录的 `browser-attempt-02`，原失败位于 `browser-attempt-01`。组件红绿和构建记录位于 `.git/summer4-closeout-20261015/frontend/t10-history-fix`。

两次有效批次均已回读确认 schema、临时数据库账号、独立 Redis 容器和五个自有端口清理完成。共享 MySQL、Redis 与其他会话资源保留。凭据只经进程内存、环境或标准输入消费，公开材料不保存 Token、密码、私钥及原始 JVM 日志。
