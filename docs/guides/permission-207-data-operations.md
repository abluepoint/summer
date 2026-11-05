# 数据权限操作执行契约（#207）

`applications` 沿用 #205 的申请单资源。本人是 `applicant_id`，代录人是
`created_by`；域、租户和 `business_visible=true` 始终作为上限。范围由
`DataReadService` 计算，本票直接消费 `Scope.predicate()`、`bindings()`，不重写预设算法。
部门预设及有效组织事实由 #206 提供，最终组合验收归集成票。

## 正式入口

前缀是 `/api/v1/platform` 或 `/api/v1/enterprises/{enterpriseId}`。
接口授权与 DATA 授权分别生效，创建权限不会自动授予角色。

| 方法与路径 | DATA action | 约束 |
|---|---|---|
| GET `/data-records/applications` | read | SQL 范围先于计数和分页 |
| GET `/data-records/applications/jpa` | read | 同一范围的 JPA Specification |
| GET `/data-records/applications/{id}` | read | ID 与范围同时查询，隐藏/不存在均 404 |
| POST `/data-records/applications/batch-read` | read | `{recordIds:[...]}`，1–100 项，仅返回可见记录 |
| GET `/data-records/applications/{id}/cached` | read | 返回当前可见投影，含 note、version |
| GET `/data-records/applications/export` | export | CSV，page 默认 1、size 默认 1000，最大 1000；`X-Total-Count` 是导出范围总数 |
| POST `/data-records/applications` | create | 创建候选也必须满足创建范围 |
| PATCH `/data-records/applications/{id}` | update | `{note,version}`；实际 DML 同时约束范围和版本 |
| DELETE `/data-records/applications/{id}?version=…` | delete | 实际 DML 同时约束范围和版本 |
| POST `/data-records/applications/batch-update` | update | `{items:[{recordId,note,version}]}`，整批事务 |
| POST `/data-records/applications/batch-delete` | delete | `{items:[{recordId,version}]}`，整批事务 |

批量修改/删除最多 100 个唯一目标；任一项不可见、不存在或版本过期，整批 404
并回滚。成功修改递增记录版本；没有匹配项不会报告成功。空查询范围返回空集，
空导出范围只返回 CSV 表头。未注册资源/动作拒绝，不回退全表。
导出在内存中完成本页查询和授权版本复核后才响应，不边查边输出旧数据。
CSV 字段转义双引号并处理公式前缀。

创建字段是 `{recordId,note,applicantId?,departmentId?}`。域、租户、代录人和业务
可见标记由服务端固定；缺省申请人为当前身份。显式申请人必须是当前域有效用户，
部门必须是该申请人在当前企业的有效任职部门；平台不接受企业部门。未知字段、
伪造归属或租户字段拒绝。拟写入行通过绑定参数构造候选，再消费当前 create 范围。
写入要求有效认证身份，PUBLIC DATA 权限不能凭空产生匿名写入身份。

V120 仅为申请单增加 `note` 和 `record_version`，保留原迁移。原有 SQL/JPA 列表
和详情的投影保持不变；需要记录版本的操作客户端使用带版本的 cached 投影。

## 可复用写事务接缝

`DataOperationExecutor.write(domain, resource, action, actor, mutation)` 接受已注册的
create/update/delete 动作。它开启独立可写 READ_COMMITTED 事务，先锁已存在的授权域行，
再通过独立只读事务获取不可变 Scope。mutation 回到可写事务执行；必须把范围放进
实际 DML、检查影响行数，并在任何目标不匹配时抛错以整体回滚。

不要在 `DataReadService.read` 的只读回调里写入。新增消费者应保持授权域先于业务行
的锁顺序；权限变更也采用相同域锁，所以已获得范围的写入与撤权按提交顺序串行。
资源表名/列名和动作由代码注册，不能来自表单、令牌或任意请求参数。

## 缓存和故障恢复

缓存入口每次先完成当前范围下的数据库投影读取，再访问既有二级缓存。键含可信域、
资源、read 动作、主体、授权 revision、范围/绑定值及当前完整业务投影的摘要；返回
缓存值前还与当前投影比对。摘要只用于定位缓存，授权决定始终来自当前数据库读取。
因此同 revision 下业务上限改变、记录更新或跨节点撤权都不能复用旧允许结果。

Redis 错误或缓存值异常只回到本次已经授权的当前投影；权限权威读取失败返回 503，
没有旧允许数据降级。该路径优先保证授权正确性，不承诺免数据库读取的性能收益。

## 消费者边界

本票没有修改既有字典、系统参数、配置覆盖、统计或审计业务。它们的完整入口清单
在 `execution/08/controlled-path-matrix.csv` 和 `legacy-consumer-routes.csv`，交由 10
逐项迁移；不是本票已通过的覆盖声明。字典 draft 集合替换及组成员删除须按业务整体
在可写事务接入，不能把读集合可见等同于 update/delete/create 授权。配置缓存、
fallback、public read、名称/选项解析及 Query count 都必须经过当前对应范围。
Customer 和审计导出没有本资源的租户/归属注册，不能因有查询或 CSV 就自动套用申请单映射。

真实运行、故障注入、浏览器持久结果、双节点旧身份撤权和锁等待证据见本票 handoff；
快速 Servlet 测试不替代这些验收。
