# T06 字典管理与表单消费验收

状态：全部原 AC 已有证据，交主会话复核；本会话未提交、推送或关闭 Issue。T04 #112 已完成。

原基础验收使用候选 `9976bf12e231e8ebd5ff2641bd9c67039fac97d334ee7384622e6525914e924c`：140 个实际 API 命名检查与17个实际 Chrome 检查全部通过，覆盖类型/项创建、编辑、排序、启停，公开/发布过滤，表单重取最新选项，MySQL/Flyway、重启持久化、审计、拒绝与实际后端故障恢复。该证据保留，未因无关模块变化重复运行。

AC4 原文“非法项值、停用项和无权限读取均返回统一错误”未改写。原实现只有集合读取，过滤停用项不能替代读取已选停用项时的错误。最小补充公开 `GET /public/enterprises/{enterpriseId}/dictionaries/{code}/items/{value}`（兼容原公开前缀别名），由 `PublishedDictionaryReader` 委托既有有效公开读取，沿用发布快照、公开状态、租户、权限和数据范围。它只从可消费项中查找：停用/未知项统一404 `SUMMER_NOT_FOUND`，非法值400 `SUMMER_DICTIONARY_INVALID`并带value字段原因，权限和企业边界继续统一403。列表继续过滤停用项，管理员仍可维护停用项。

真实表单在初始选择、切换选择及刷新后验证选中值，显示错误/requestId，拒绝读取时清空消费数据；迟到校验不得覆盖新选择。旧候选补验92项有23失败，组件3项全部红灯，均保留。当前合并候选 `442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453` 的AC4补验为84个实际HTTP检查与8个实际Chrome检查，共92/0/0/0。页面先选中有效项，再由真实管理API停用，刷新后列表仍200且过滤该项，单项请求返回统一404并展示requestId；重选有效项重新请求200且错误消失。实际无mock，241份UI源码摘要稳定，停用项错误截图已原生目视确认。

后端新增5项及相关旧6项合计 JUnit 11/0/0/0，包含公开状态、租户与权限、有效发布快照；专用组件浏览器3/0/0/0；Node测试30项及既有契约、类型检查和构建均成功。两次共享测试编译中间态保留，不计执行；一轮旧XML误提取已标stale排除，不用旧结果替代新测试。基础157与补充92包含重复环境/授权设置，分别报告，不累加为单一“用例数”；命名实际检查也不冒称JUnit。

脚本：`scripts/acceptance/t05-t06/run-t06.py`/`t06-browser.mjs` 为基础验收，`run-t06-ac4.py`/`t06-ac4-browser.mjs` 为AC4增量。运行在仓库根，设置新的绝对 `SUMMER_CONFIG_EVIDENCE_DIR`、不可变 `SUMMER_FOUNDATION_CANDIDATE` 与精确 `SUMMER_FOUNDATION_CANDIDATE_SHA256`；所需Python3、JDK21、Node22、Chrome及既有local-base MySQL/Redis由脚本实际路径配置。凭据仅在内存/env/stdin消费，不归档Cookie、Token、私钥或密码；业务授权只走真实角色/能力/成员API，SQL仅查询验收数据及隔离环境DDL。

独立MySQL schema/应用账号、Redis15本轮精确随机namespace、自有JVM/Chrome/Vite/20081–20083/20573/20580端口均清理核验。用户CustomerController格式和共享索引保留。T11完整版本历史、T31实时投递不由本票验收替代；新增单项读取仅验证沿用有效快照。

逐AC、候选、计数和源码摘要见 [dictionary-114-result.json](dictionary-114-result.json)。本机完整红绿证据位于 `.git/summer4-closeout-20261015/frontend/t06-114/`，本轮绿证据唯一目录为 `ac4-fix/actual-green-442e-attempt01/`。
