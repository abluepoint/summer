# 业务字典草稿（#192）

管理入口 `/dictionary/manage` 默认读取基础生效快照，草稿页编辑名称、公开/启用标记、项值、显示名称、说明、启停状态和默认拖拽顺序。保存不发布；专用发布/停用/回滚接口仍负责生效指针。历史交互和双重发布修订校验由任务03继续实施，字典组由任务04实施。

## HTTP 契约

`GET /api/v1/enterprises/{enterpriseId}/dictionaries/{code}/draft` 返回草稿和独立 `draftRevision`。`PUT` 同路径显式提交 `expectedDraftRevision`、`name`、`publicReadable`、`enabled`、`items` 和 `itemOrder`。items 每项含稳定 `id`、字符串 `value`、`label`、可空 `description`、`enabled`；新项使用客户端临时 `new:` ID，保存后改用服务端返回ID。itemOrder 必须恰好包含提交项的全部ID，禁止重复/遗漏/未知项。已存在但从未发布的项可从完整列表删除；曾发布项不可删改值。

项值保留安全字符和80字符边界，支持单字符及“1”与“01”，拒绝数字/布尔输入和隐式trim。显示名称最多200，说明最多2000；缺省说明返回空字符串。新增项、内容/排序修改、启停分别校验创建、修改、停用/发布能力及OAuth scope；聚合端点还要求读取能力。完整维护要求全部项可见；无差异保存只读且不推进修订。

旧元数据PATCH、项POST/PATCH同样必须显式提供整数 `expectedDraftRevision`，初始新建草稿为0。它们与聚合保存共用字典行锁及数据库CAS；非法/缺失修订返回400，过期返回409和requestId。旧发布请求的 `version`/`revision` 仍只表示 expectedVersion，不能代替草稿修订。仓内消费页仅写组织覆盖，不包含字典CRUD调用，本票未改其既有未提交UI差异。既有HTTP夹具通过管理HTTP读取修订后发送条件写入；十二个既有验收脚本显式采用scripts/acceptance/dictionary_drafts.py适配同一契约。保留项相对顺序不变的追加只需创建能力，存储重编号不额外要求修改能力；真正重排既有项仍要求修改能力。

## V40 升级与恢复

先完成数据库备份，并确认V39已迁移无快照的旧生效数据。V40保留 `summer_dictionary_v40_type_backup`、`summer_dictionary_v40_item_backup` 检查点，新增草稿修订、说明和ever_published，从所有不可变历史快照而非当前回滚指针回填曾发布身份，扩展快照列为LONGTEXT。历史JSON原文和版本号不改写。新发布在同一事务中保留说明并标记曾发布，回滚不清除此标记。

MySQL DDL不能整体回滚；失败后停止新写入并核对Flyway历史及各列/备份表状态，从升级前备份恢复到独立库验证，再执行受控恢复。不得直接重复已有CREATE/ALTER，也不得用清空历史或ever_published解除身份保护。检查点用于重建原有类型/项字段和默认位置，不等同于整库灾备。成功验收后由运维按留存策略处理检查点。

## 验证记录

本票执行证据位于 `.scratch/business-dictionary-tickets/20261104-011212-129812/execution/02`，以handoff.json中冻结摘要和逐批tests/failures/errors/skipped为准。HTTP MockMvc、真实TCP/MySQL、Chrome、静态检查和完整回归分别记录；原始失败及环境跳过保留，不相加冒充全量通过。生产整库切换恢复未执行。
