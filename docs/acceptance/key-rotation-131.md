# T23 手动密钥轮换实际验收

对应 [#131](https://github.com/abluepoint/summer/issues/131)，用户故事 35、37。**READY：三类实际缺陷已修复并通过新候选定向重放。** 提交和关票由协调方完成，本会话未执行。原始唯一依赖 #129 已回读 CLOSED/COMPLETED；#130 不是原票依赖。

机器收据见 [key-rotation-131-result.json](key-rotation-131-result.json)。原始证据、备份、逐 AC 矩阵与 SHA 清单位于 `.git/summer4-closeout-20261015/t23-live/`。

## 改动与最终验证

- `MysqlSummerKeyStore` 在现有 `rotation_id=1` 状态行取得数据库锁后检查版本，避免两个实例同时通过旧版本检查。`KeyRotationStateRepository` 提供该写锁查询。
- JWK 快照与成功幂等回执仅在事务提交成功后发布。回滚后使用相同幂等键重试，会实际提交新 key，并返回数据库 ACTIVE kid。
- `security-keys/index.tsx` 的轮换错误提示显示 requestId，覆盖真实 412 冲突和 500 数据库故障。

本次只修改两个 key 后端文件和一处密钥页面，新增专用 JUnit、组件夹具与验收脚本。没有新增迁移、修改共享授权/参数/菜单或用户 CustomerController。源码和现有 dist 已事前备份；没有重建 UI dist、暂存、提交、推送或写 Issue，外部工作区和索引变化保留。

新固定候选为 `917307813cd8d81476b3e28f36c9b9c0f7465185d55ba053cebb893dfbdbc0d2`。协调方构建记录显示 534 个主输入稳定、迁移资源匹配，两个 key 修复主文件逐项匹配冻结摘要。本会话只重放三个缺陷：**运行时 10/0/0/0，Chrome 4/0/0/0，进程 exit 0，清理 VERIFIED**。262 个 UI、专用脚本和 key 主输入前后无漂移；运行时敏感扫描零命中。实际 412/500 截图已目视确认 requestId、提示和表格完整。

定向六类 JUnit 共 **19/0/0/0**，包含轮换事务、密钥 HTTP、自动轮换兼容和安全失败审计；专用页面组件 **2/0/0/0**，TypeScript 检查 exit 0。组件使用合成 HTTP，只证明页面回归；实际登录由上述 Chrome 重放证明。

## 三类缺陷的红绿证据

| 缺陷 | 原 442 候选实际红灯 | 新 917 候选实际结果 |
| --- | --- | --- |
| T23-D1 失败后幂等回执污染 | 数据库插入失败返回500且原状态保留；恢复后相同幂等键返回200，但响应kid不等于数据库ACTIVE kid。 | 回滚保留原状态；同键重试返回kid存在于唯一数据库行并为ACTIVE，另一节点用该kid真实签发且公开JWKS验签成功。 |
| T23-D2 双节点竞争错误 | 两个实例相同If-Match、不同幂等键得到200/500，最终ACTIVE=1，不能夸成双ACTIVE。 | 一次200、一次412，ACTIVE=1，落败方SUMMER_PRECONDITION_FAILED响应及关联审计一致。 |
| T23-D3 错误UI缺少requestId | 过时页面412、数据库故障500均有提示，但没有显示响应requestId。 | 两种真实Chrome场景均显示相同响应requestId。 |

D1原始收据字段 `reportedKeyPersisted` 只比较ACTIVE kid，并未查询所有历史行。原字段不改写；新重放补充 `reportedKeyRowCount=1` 和 `reportedKeyIsActive=true`，消除该证据范围歧义。

## 真实环境与验收边界

使用生产迁移、真实 MySQL 独立 schema、非root应用账号、独立Redis容器、两个JVM和实际PKCE登录；Chrome加载Summer UI并调用真实后端，无网络mock。SQL只用于本轮schema建立、查询、故障触发器和时间夹具，没有通过SQL写业务授权。

原442候选的正常轮换、两个节点新旧token真实RSA验签、公开JWK不含私钥、GRACE版本/保留截止展示、拒绝和非法输入等绿色证据保留，不因无关模块变动重复执行。并发发布过程跨节点采样5次全部验签通过；这只是本轮采样，不证明全部交错顺序。

故障夹具为本轮key表的BEFORE INSERT失败触发器；竞争夹具为本轮INSERT延时2秒，以产生真实重叠事务。故障恢复后验证相同幂等键，全部触发器随本轮schema清理。

旧key到期通过GRACE截止后退出JWKS和管理列表实现。已使用把本轮旧key的 `graceUntil` 设为过去的明确时间夹具验证过滤；**没有自然等待token的15分钟有效期**。原AC未明确要求独立人工停用按钮，不新增端点，也不把自然等待另加为验收门禁。就绪实例已经初始化状态行，本轮不扩称修复了多个全新实例同时首次生成初始key的问题。

## 实际批次计数

下表为命名验收断言，非JUnit；格式为tests / failures / errors / skipped。原始失败不改判。

| 批次 | 运行时 | Chrome | 退出与清理 |
| --- | --- | --- | --- |
| attempt-01-442e609f | 31 / 4 / 1 / 0 | normal：9 / 2 / 0 / 0；fault：2 / 1 / 0 / 0 | exit1；VERIFIED |
| attempt-02-harness-corrections | 3 / 0 / 0 / 0 | retention：1 / 0 / 0 / 0 | exit0；VERIFIED |
| attempt-03-91730781-replay | 10 / 0 / 0 / 0 | conflict：2 / 0 / 0 / 0；fault：2 / 0 / 0 / 0 | exit0；VERIFIED |

首轮三个脚本问题独立记录：通用审计结果实际为FAILED，初版误要求SUMMER错误码；Arco表头未暴露预期columnheader role，改用中文文本定位；过期夹具淘汰旧admin token后仍用它查元数据导致401，改为事前获取新token。第二批只补验这些范围。

按检查名选取最新有效结果，形成运行时35项、Chrome11项、零失败的跨批次索引；**不是在新917候选重跑整个矩阵**，也不抹去首轮编排错误。

专用事务测试先取得回滚有效红1/1/0/0，再绿1/0/0/0；独立并发红1/1/0/0。中间合并运行2/2/0/0时，并发红灯改变共享测试上下文，造成后续回滚测试连带失败，已改每方法独立Spring上下文。最终六类19项全绿。组件原红2/2/0/0、最终绿2/0/0/0分别保留。

## 原始七条AC对照

| AC | 结论 | 证据 |
| --- | --- | --- |
| 1 管理员创建与切换 | 通过 | 原正常HTTP/Chrome及双节点签发；新候选竞争200/412。 |
| 2 旧key验证与到期停用 | 时间夹具验证通过 | 实际新旧token验签、GRACE展示、截止后JWKS/metadata排除；无自然等待。 |
| 3 失败不覆盖有效key | 通过 | 新候选回滚原状态、同键重试真实提交、另一节点签发验签。 |
| 4 动作、主体、结果审计 | 通过 | 成功、非法、过时版本、故障和新并发冲突均有主体/结果/requestId关联。 |
| 5 迁移、真实HTTP/UI | 通过 | 生产迁移及真实成功、拒绝、非法、故障；三缺陷定向实际重放。 |
| 6 参数、响应、关联、安全 | 通过 | SUMMER错误信封与requestId一致；新UI显示关联；敏感扫描0。 |
| 7 可追溯、计数、保护改动 | 通过 | 故事35/37→设计→key实现→命名检查→原红/修复/重放→本收据，已有改动保留。 |

术语与契约沿用根CONTEXT.md、ADR0004/0008/0009及Parameter contract；本轮未改变这些设计约束。

## 复现与清理

从仓库根执行，先确认21081/21082/21083/21573/21580空闲。需要既有local-base/mysql-0及受控Secret、Docker本地Redis镜像、JDK21、Node22和Chrome。密码、token、master key、数据库凭据仅经内存、环境或stdin使用，不打印原始JVM日志。

```sh
SUMMER_IDENTITY_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t23-live/replay-new-unique" \
SUMMER_IDENTITY_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/frozen-build-03/candidate.jar" \
SUMMER_IDENTITY_CANDIDATE_SHA256=917307813cd8d81476b3e28f36c9b9c0f7465185d55ba053cebb893dfbdbc0d2 \
python3 scripts/acceptance/key-rotation/replay.py
```

原完整矩阵入口为 `run.py`，夹具纠正入口为 `verify-harness.py`，均在同目录。它们的原执行版本及回执保留；本轮没有将改后完整矩阵脚本冒称重新全跑。

三轮均回读确认schema、应用账号、Redis容器不存在，五个专用端口无监听；JVM/Chrome/Vite进程退出，专用缓存清理。原始回执、截图和候选摘要保留，最终13路径SHA清单和READY与公开md/JSON同步。
