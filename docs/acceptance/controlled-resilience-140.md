# T32｜依赖故障下的受控降级

对应 [#140](https://github.com/abluepoint/summer/issues/140)、故事 43/44；前置 #127、#128 已完成。后端与独立真实 Chrome 证据均已复核，七项 AC 功能验收通过；原生末批收尾错误仍按下文单独保留。[结构化分批回执](controlled-resilience-140-result.json)保留每次实际计数及限制，不合并重叠批次。

## 实现与修复

降级仅开放给参数和字典读取，缓存键包含资源版本、主体和有效数据范围。命中旧值后还必须在独立事务中重新证明持久权限、租户、当前公开/启用状态、发布指针及覆盖范围；证明失败或 TTL 到期均拒绝。后台迟到完成不能替换降级缓存。其他操作在进入 loader 前拒绝，不给写入或密钥操作自动重试、重放或假成功路径。

本轮三个有效缺陷均先取得失败证据：

- 诊断接口原来只检查签发时的 scope。现在保留 scope 检查，并通过现有 AuthorizationService 验证当前持久权限和成员上下文；原令牌在持久 DENY 或角色移除后立即拒绝，无法可靠验证时也拒绝。
- 熔断器原来包裹后台 loader，TimeLimiter 在外部等待。真实 Redis 延迟产生五次调用方超时，但 loader 迟到成功使熔断器仍 CLOSED。现在熔断器包裹包含 TimeLimiter 的整个逻辑读，一次调用按调用方结果计数。Retry、RateLimiter 和 Bulkhead 仍限制真实物理读取；超时不提前释放仍在执行的 bulkhead，限流/容量拒绝和业务拒绝不作为依赖故障打开熔断器。
- 诊断原来只有 retryMaxAttempts 配置上限。现在由真实 Retry 的 onRetry 事件递增 retryAttempts，区分实际重试与配置；同一服务实例内累计，重启归零。单测用实际 Retry 注册表验证一次成功重试及一次最终失败重试，计数从 1 到 2，不把 fallback 或容量拒绝算作重试。

原公开配置与发布版本边界沿用 [ADR0028](../adr/0028-enforce-effective-configuration-scopes.md) 和 [ADR0031](../adr/0031-resolve-configuration-tenants-and-version-overrides.md)。密钥不接入本服务，读取/签发失败边界另见 [T21 密钥验收](security-keys-129.md)，本票不冒称重新执行该矩阵。

## 后端分批证据

计数顺序 tests / failures / errors / skipped。JUnit 使用独立报告目录并保留原 XML；原生断言包括真实 HTTP 和环境准备，不等同于 JUnit 个数。

| 批次 | 实际计数 | 结论 |
| --- | --- | --- |
| revocation-red-01 | 1 / 1 / 0 / 0 | 旧 JWT 在持久撤权后仍获准访问的有效红灯 |
| revocation-green-01 | 19 / 1 / 0 / 0 | 新撤权测试及 16 项原降级测试通过；旧正向夹具缺有效 membership 正确收到 403，整批保留失败 |
| revocation-green-02 | 3 / 0 / 0 / 0 | 补合法成员声明后的正向/拒绝/撤权定向回归 |
| circuit-red-01 | 2 / 1 / 0 / 0 | 两次调用方超时但状态 CLOSED；实际限流拒绝隔离检查通过 |
| circuit-green-01 | 21 / 0 / 0 / 0 | 超时熔断、迟到完成、TTL、跨键、版本/权限证明、并发隔离、诊断与撤权 |
| retry-metrics-red-01 | 2 / 1 / 0 / 0 | 实际诊断 JSON 缺少 retryAttempts |
| retry-metrics-green-01 | 11 / 0 / 0 / 0 | 实际 Retry 事件计数、限流/连续并发拒绝不熔断、诊断字段与撤权 |
| native-attempt-01 | 88 / 1 / 0 / 0 | 原 7e 候选，真实五次超时但 CLOSED；其余真实恢复、隔离、限流、写回滚、撤权检查通过 |
| native-attempt-02 | 88 / 1 / 0 / 0 | 339c 候选，唯一失败为只接受 OPEN 的过严断言，实际已 HALF_OPEN；其余检查通过，完整清理验证 |
| native-attempt-03-circuit | 41 / 0 / 0 / 0 | a0ecd6 候选，长开放期确认 OPEN、实际计数字段及版本变更禁止旧 fallback；业务全绿但收尾退出 1，详见下节 |

10 项框架 HTTP 层证明回归单独覆盖版本变化、禁用、非公开、策略不可用、字典指针变化、DataScope 和接口 DENY。它们使用 H2/MockMvc，不冒称真实 MySQL；已在 circuit-green-01 的 21 项中实际执行。最新 11 项与前批重叠，不相加为 32 项。

原生矩阵使用真实 MySQL、隔离 Redis、两个生产 JVM、正式 PKCE 和 HTTP。业务身份、权限、DataScope、参数及发布字典均通过正式 API 创建，无网络 mock 或业务 SQL 写入。Redis CLIENT PAUSE 制造真实读取延迟，低容量和限流配置产生实际拒绝；自有审计触发器只注入事务失败。降级值与预热值逐字段相等；冷键拒绝，30 秒后旧值失效，恢复后读到当前值；独立字典操作不被参数并发占用阻塞。旧令牌撤权与异租户拒绝不变为 fallback 成功，审计失败写入 500 且值/版本不变。

定向末批把开放等待设为一分钟，避免两秒开放期和 2.1 秒采样进入合法 HALF_OPEN 的混淆。仍处真实 Redis 延迟时，正式 API 修改参数版本和字典发布指针，原有参数/字典 fallback 均被拒绝为 503。原 CLOSED 红灯、第二批过严采样失败都原样保留。

## 制品、运行器与清理限制

最终后端候选 `.git/summer4-closeout-20261015/t32-live/frozen-build-03/candidate.jar` SHA-256 为 `a0ecd6e1af2d45453f7575b6a00fed246c4a2cddf1403796846833a4ca7a905c`；541 个构建输入前后稳定，所有 SQL 迁移字节与源码匹配。先前 7e 和 339c 候选及实际执行快照保留，不覆盖旧制品。

末批 41 项业务均完成，但最后 CLIENT UNPAUSE 超过 25 秒，原运行器记录 cleanupError=TimeoutExpired、passed=false、退出 1。随后按该批 resources.json 和具体 JAR/监听端口核验归属，只终止自有 JVM、销毁自有 Redis、删除自有 schema/账号，并回读五个端口均释放；补清结果 VERIFIED，业务用例重跑 0，原 result.json SHA 未变。不能称该批整运行器全绿或宣称 UNPAUSE 根因已经证明。

当前脚本仅在 finally 清理路径补强：恢复或触发器移除失败仍执行环境回收；定向模式结束时直接销毁自有 Redis，不额外等待该模式未要求的恢复。业务断言与执行快照保留。前两轮原生清理全部 VERIFIED；各轮敏感日志检测无命中、相关九个后端/公共入口输入执行期间无漂移。用户 CustomerController 及既有暂存改动保留。

## 真实前端验收与七项 AC

前端补实际 retryAttempts 类型和表格列，使用真实响应原值；其余诊断页拒绝清屏/重试机制沿用原实现。独立脚本用 28081/28573 等自有端口，绑定同一 a0ecd6 候选，七个 resilience 生产源与候选匹配，九个相关 Java/UI 输入执行期间稳定。观察到统计页两项并行修改，保留且不扩称整个前端稳定。

最终 attempt-05 配套 HTTP/环境 **51/0/0/0**、实际 Chrome **12/0/0/0**，父进程和浏览器均退出 0。实际 Redis 延迟与限流得到参数行 calls=5、failures=3、fallbacks=2、rejected=2、timedOut=1、retryAttempts=0、OPEN；页面逐字段比对原响应，真实重试为 0 就显示 0，不制造非零值。恢复后 calls=15、CLOSED，累计故障计数保留。同一浏览器持久 DENY 后真实 403/SUMMER_FORBIDDEN、关联 ID 可见且旧指标隐藏；恢复权限后显式重试正常，错误消失。四张成功/OPEN、恢复、拒绝、恢复权限截图由主会话逐张目视。

前四批均未进入 Chrome，原结果保留：8/1/1/0 为公开参数夹具缺 publish 权限；21/0/1/0 两批分别是 redis-cli 和首命令 RESP UNPAUSE 超时，未证明其底层根因，改有限暂停自然到期；24/1/1/0 为错误要求实际 retryAttempts 必须大于 0，撤回额外门槛。五批资源均 cleanup VERIFIED，敏感检测无命中。最终 Chrome 有界 SIGTERM 回收 forced=true/exited=true，明确区别于优雅关闭；不把该进程数计为业务测试。

| AC | 实际证据 |
| --- | --- |
| 安全读 fallback 与过期边界 | 原生 TTL/冷键检查，定向参数版本及字典指针变更拒旧值，HTTP 层十个证明场景 |
| 写入、权限及密钥失败关闭 | 真实写入审计故障回滚、旧 JWT 持久撤权和异租户拒绝；服务只接受两类只读操作，密钥失败证据沿用 T21 的明确边界 |
| 熔断、重试和限流指标 | 实际 OPEN/HALF_OPEN/CLOSED、拒绝及降级计数，真实 Retry 事件 1→2，Chrome 原值展示 retry=0 |
| 超时、持续失败、恢复和并发隔离 | 原生 88 项中的相关通过项、最终定向 41 项，以及实际物理调用占用容量回归 |
| 迁移、真实 HTTP/UI 与失败路径 | MySQL/独立 Redis/生产 JAR/PKCE/Chrome，成功、403、503、写入500及恢复；无可编辑表单就不捏造非法 UI 字段 |
| 公共契约及敏感边界 | Unified JSON、SUMMER 错误和 body/header requestId，实际权限验证；凭据仅内存/env/stdin，全部敏感扫描无命中 |
| 故事—实现—缺陷—验收追踪 | 本文、JSON、原 XML、执行快照、候选/源码 SHA、真实截图和逐批清理；用户改动保留 |

复现说明见 [受控脚本](../../scripts/acceptance/controlled-resilience/README.md)。实际证据在 `.git/summer4-closeout-20261015/t32-live/` 与 `t32-ui-live/`。各批不相加为一次全量，不以历史全仓绿灯替代本轮最终门禁。
