# T26 定时任务配置与执行验收

对应 [#134](https://github.com/abluepoint/summer/issues/134)、故事38/39，前置#123已CLOSED/COMPLETED。原七项AC通过；机器结果见 [task-configuration-134-result.json](task-configuration-134-result.json)。本轮无任务后端或任务页面改动，历史修复见 [任务执行、恢复与SSE记录](../guides/summer4-task-progress-closeout-20261015.md)。

## 原验收条款

| AC | 实现与实际证据 |
| --- | --- |
| 管理员配置任务、权限、状态及参数 | 角色和成员通过真实API配置，任务scope与method:path接口权限同时授予；管理员创建及版本编辑cron/参数、暂停恢复，只读主体创建/触发/暂停均403。 |
| 定义和执行记录落MySQL | 生产迁移建立Quartz和任务表，Quartz实际执行SUCCESS；重启JVM后定义、参数和完成历史仍可读。 |
| 启动、暂停、立即执行写审计 | CREATED、UPDATED、PAUSED、RESUMED、TRIGGERED五种动作的成功审计均持久化。 |
| 页面显示运行中、成功和失败 | 实际Chrome/PKCE/API/EventSource显示RUNNING、中途进度、SUCCESS、FAILED及尝试次数2；结束时间与持久历史逐字比较。 |
| 真实迁移、HTTP/UI及失败路径 | 实际MySQL、独立Redis、生产JAR；非法cron、未注册类型、陈旧版本、跨租户和只读写入均拒绝；页面非法JSON未发请求、只读403可见。 |
| 公共契约与敏感边界 | 成功/失败统一信封、SUMMER错误、响应头/body匹配requestId；只读页面展示相同ID；已知运行凭据日志检测零命中。 |
| 故事到证据及保留用户改动 | 故事38/39→任务实现/既有修复记录→新脚本及原失败→本轮13+7验收→本文/JSON；源码和真实暂存区保留。 |

只调度注册的受信任处理器；实际拒绝任意未注册任务类型，没有接受可执行代码。权限在既有角色/接口映射中配置，不在任务参数里另造授权规则。

## 执行结果与制品

固定候选SHA-256：`917307813cd8d81476b3e28f36c9b9c0f7465185d55ba053cebb893dfbdbc0d2`。25个任务/Quartz主输入匹配候选；本次执行前后243个UI输入一致，包含最终T16菜单/路由补丁。没有声称全工作区均稳定。

- 真实后端 **13/0/0/0**；实际Chrome **7/0/0/0**，运行器/浏览器退出0。
- 既有688项全仓检查中的TaskSchedulerHttpTest **4/0/0/0**仅作历史关联，不作为本轮新执行。
- 原生截图已目视核对失败历史、次数、时间和只读403/requestId；未发现文字裁切或重叠。
- 自有schema、应用账号、Redis容器与26081/26082/26083/26573/26580均回读核验清理；JVM、Vite和Chrome退出。

13和7分别为命名断言，不是合计HTTP请求数，不与旧批重复累计。旧任务接管、重试和SSE证据继续原样保存；本票不替代T27/T29验收。

## 原失败及边界

attempt01/02在任务步骤开始前失败：验收脚本漏成员请求的Idempotency-Key，实际返回500。补头后成员创建通过；缺头错误映射另由MVC专用红绿回归修复，不把初始化失败当任务产品缺陷或删除原记录。

attempt03的读者只获task scope，漏授method:path接口映射，服务端正确403。attempt04错误假设所有映射必须预先出现在Capability目录；既有角色API可接收映射码并持久化。仅修夹具授权，未放宽后端授权。四批均为0项业务检查/1个编排错误，保留各批清理记录；attempt05才是完整13+7通过。

实际串流关闭后出现脱敏ClientAbortException等断开诊断；它们不作为本票SSE性能或全故障验收结论。公开材料不含凭据、token、原始JVM日志或业务请求敏感内容。

## 复现

从仓库根使用scripts/acceptance/task-configuration/run.py，配置SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR为新的唯一目录、SUMMER_TASK_CONFIGURATION_CANDIDATE为上述不可变候选绝对路径、SUMMER_TASK_CONFIGURATION_CANDIDATE_SHA256为其SHA。需local-base/mysql-0、Docker的本地Redis镜像、JDK21、Node22和Chrome。Kubernetes Secret只在内存读取，凭据仅经环境或stdin。

入口环境创建独立MySQL schema/应用账号及仅绑定回环的Redis容器；所有业务角色/成员/任务变更通过真实HTTP，SQL仅基础设施DDL与只读验收。浏览器使用真实PKCE和代理，未拦截网络或伪造身份。脚本和脱敏回执保存在.git/summer4-closeout-20261015/t26-live/各attempt，原失败不覆盖。
