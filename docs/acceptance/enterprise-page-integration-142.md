# #142 角色与授权事件：共享租户集成补救

2026-10-16。两页 UI 集成已验证；随后主会话将后端事件 B 持久撤权问题纳入本次补救，现已在新候选完成独立验证，详见 `enterprise-event-authorization-142.md`。Issue 整合与关闭由主会话执行。机器结果见 `enterprise-page-integration-142-result.json`。

## 最终变更

- 角色页、授权事件页使用现有 `useActiveEnterprise`，显示当前租户，未选择时不读取业务数据。读取、创建、绑定、重试均捕获选择的 enterpriseId。
- 角色编辑通过共享 dirty 守卫确认放弃；写入与事件重试使用共享 busy 守卫。租户切换以 keyed 内容卸载旧状态；生命周期和请求代次检查阻止迟到响应回填。
- 403 清除旧目录、绑定或事件数据，显示 requestId。角色 412 保留输入并读取最新版本，要求明确再次保存。
- 真实目录中多个接口可共享 permissionCode。角色表格与拒绝项的 React key 改用 HTTP 方法和路径组合，授权 value 保持原码，修复翻页后 403 仍残留旧行。

生产变更只有 `summer-ui/src/pages/roles/index.tsx`、`summer-ui/src/pages/authorization-events/index.tsx`。API、共享 hook、主入口、导航、后端授权及旧统计报告均保持。选择属于 UI 内存状态，不是 JWT 或服务端组织 context 切换。

## 验证证据与边界

组件首次 8 项全部失败，修复后最终专用组件 10/0/0/0；既有绑定版本测试 2/0/0/0。均为真实 React/Arco、合成身份/传输，不能替代真实 HTTP。

重复目录独立探针：首版 TimeoutError 无有效断言；第二版取得 1/1/0/0，403 后残留 1 行。内存替换两处 key 对照为绿，仅证明方案。实际源码应用后独立 1/0/0/0、残留 0 行。此前组件 10 项未覆盖重复目录，不能用其全绿否定真实失败。

后端候选固定 SHA-256 `4dd825e8df88aa35cb5f6b16563d39a659c26034513889d17f43c7be8fd944cf`，每轮授权 Java 输入与 candidate-inputs 一致。真实 MySQL 独立 schema、独立 Redis、严格 PKCE、Chrome；所有 fixture 授权和业务写入通过正式 API，无业务 SQL 写入、无网络替身。

| 批次 | 原始结果 tests/failures/errors/skipped | 结论 |
| --- | --- | --- |
| native-01 | 35/2/2/0；Chrome 1/0/1/0 | B 读取通过；脚本漏目录翻页，后续持久化检查依赖未完成创建。原失败保留。 |
| native-02 | 1/0/1/0 | OAuth authorize 401，未进入页面。主会话确认与另一运行占用同组端口的时间重叠，但原因未证实。 |
| native-03 | 26/0/2/0；Chrome 3/0/1/0 | B 读取、dirty 取消切换、创建通过；角色下拉定位错误。 |
| native-04 | 无原始最终汇总；stdout 28 fixture checks/1失败；Chrome 6/1/1/0 | B 创建和绑定、保存 busy 通过；真实 403 旧行残留失败。恢复夹具错误地读取已撤权绑定，随后 finally 超时。恢复清理另有回执，不伪造正常退出。 |
| native-05-supplement | 41/0/2/0；Chrome 6/0/1/0 | 实际源码角色 403 清空通过；B 事件读取、重试、busy 通过。B 持久撤权后事件仍 200，等待 403 超时，整批不改判通过。 |
| native-06-final-supplement | **54/0/0/0**，其中 Chrome **7/0/0/0** | 使用接口现有 A 授权门禁取得真实 403，验证 B 事件旧行清空、requestId、切换 A、返回角色页和无服务端 context 写入。父进程/Chrome exit0，closed=true。 |

最后两批预置角色和绑定属于正式 HTTP 夹具，不声称重新执行 Chrome 创建。native04 已绿写入与 native05 已绿重试保留并复用；最后 54 项仅是定点补验，不是把六批合并成一次全矩阵绿灯。六批资源均已清理，native04 为单独恢复清理；正常结果批次均无捕获源码漂移。

角色 403、事件列表/重试和事件 403 截图已逐张目视：当前租户、中文、空态及 requestId 可读，未见裁切或重叠。原角色权限目录很长，非本次布局调整范围。

主会话 frontend02 七门禁退出 0，39 Node 测试通过，330 捕获输入稳定；唯一键修复后 frontend03 再执行 typecheck、lint、生产 build，均退出 0，330 输入稳定。其他已绿门禁按影响范围复用。此处记录主会话证据，不冒称本会话重跑。

## 后端发现及后续闭环

`/api/authorization-events?enterpriseId=B` 的数据范围及重试正确使用 B；但 `AuthorizationRequestInterceptor` 对无企业路径的入口仍按固定 ent-acme 作持久角色决定，控制器的 B 检查使用活动成员状态和令牌能力。native05 中正式 API 撤销 B 的 `enterprise.roles.read` 后，旧令牌的事件 GET 仍返回 200。该现象与 UI 选择没有写回组织 context 无关，也不能泛化为所有全局端点的问题。

本轮没有修改后端来扩大范围。native06 的 403 来自实际 A 持久授权门禁，**不证明 B 撤权已修复**。该发现后来经主会话授权精确修复，H2/MockMvc18绿，新候选首批117/0/2/0（HTTP/配套检查通过但Chrome未完成）及独立补批42/0/0/0（含5项Chrome）分批留存，详见[后端补救报告](enterprise-event-authorization-142.md)；原发现文件 `BACKEND_BOUNDARY_FINDING.json` 作为历史证据保留。

## 交接与复现

交接目录：`.git/summer4-closeout-20261015/enterprise-page-integration-fix-01/`。保留原批执行副本、SHA、截图、失败与清理回执。最终 UI 清单为 `MAIN_SOURCE_FROZEN-v2.json`；旧清单、frontend02 与旧统计 11 批均保留。

执行入口与前置条件见 `scripts/acceptance/enterprise-page-integration/README.md`。每次使用新证据目录；不得覆盖历史批次。没有执行本会话 Git 暂存、提交、推送或 Issue 更新，整合与 Issue 状态由主会话负责。
