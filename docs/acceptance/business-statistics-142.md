# T34 基础业务统计验收

关联 [Issue #142](https://github.com/abluepoint/summer/issues/142)，用户故事46、52；前置#114、#135、#141已核实关闭。更新日期：2026-10-16。

**状态：READY，七项验收证据齐全，交root合并与关票。** 统计三份生产Java及统计页面保持冻结。真实后端162项、真实Chrome正常/故障/恢复8项、空数据/拒绝5项分别通过；T34-CTX-01共享入口由root协调修复，第10批保留10项有效Chrome绿灯，第11批仅补3项全部通过。不同批次不相加冒称一次完整运行通过，全部原始失败/error保留。本会话未修改共享main/menu/hook、未提交推送或关闭Issue；共享owner清单由root另行合并。

## 实现与统计口径

旧7e候选固定查询不存在的AICT账本表，使真实MySQL管理员读取500。修复使用正式业务审计 `summer_audit_event`，并复用实时AuthorizationService、DataScope SQL条件、FieldContract、RawResponse和请求ID；无新迁移，不改任务执行、审计写入、共享授权或DataScope实现。

- 用户按企业成员subject去重；当前组织存在时，验证持久上下文、有效组织及成员关系，并限定用户/字典/审计。角色与任务定义为租户级库存，包括各状态；任务模型没有组织归属，不臆造组织过滤。
- 字典在租户/组织边界内进一步应用当前有效DataScope。缺相应读取权限的指标返回null，页面显示“无权读取”，区别于真实0。
- 执行开始时间和业务审计发生时间使用 `[from,to)`；默认to为当前时间、from为to前30天，最多366天。库存另报asOf，不能把窗口解释为库存创建时间筛选。
- 任务成功率为窗口内SUCCESS执行数÷全部执行数，含RUNNING和FAILED；按执行ID去重，重试次数不增加执行数，无执行为0%。
- 两个接口计数字段均为当前实时授权的MVC method/path去重数量，排除统计自身；实际调用还须满足各接口的数据范围与业务约束，不把资源授权数承诺为任意调用成功数。
- 统计页显示窗口、asOf和公式；加载、查询失败、拒绝、真实空数据分别处理，清除旧数据并忽略迟到响应。空数据判断不因接口目录有条目而失效；私有locale承载文案。

## 固定候选与测试分层

真实修复后候选SHA256：`28a48d6cda86996d2d1b4d396c854e3d51d671ccc7994a41029daa567df02d18`。root构建记录541项输入前后稳定、39条MySQL迁移资源与源码一致，相对前一a0候选仅统计三份main变化。第3至11批使用该候选；本票生产与执行输入均无漂移。不切换后续审查候选冒称已验证。

JUnit有效红灯red-02为8/8/0/0，最终green-02为8/0/0/0。测试为Spring/H2受管JWT与持久授权夹具，不能替代MySQL实际联调。原始suite/case XML在共享Gradle锁内归档，删除system-out/system-err捕获输出，非从JSON重建。red-01编译失败为零用例，不计产品红灯。

组件Chrome最终3/0/0/0，使用合成身份和HTTP；首轮空状态文案断言错误修正后，修改产品前另取有效空状态红灯。类型检查与前端构建exit0；dist事前备份，244项UI构建输入稳定。该构建发生在共享入口修复前；共享修复后的类型/lint门禁由owner单列，不能冒称已构建最终共享UI。本会话后续未重复Gradle或bootJar。

以下为真实MySQL/PKCE/HTTP/Chrome命名断言，计数顺序tests / failures / errors / skipped，**不是JUnit**。原始失败不改写，重复夹具不累加。

| 批次 | 实际计数 | 说明 |
| --- | --- | --- |
| attempt-01 | 24 / 1 / 1 / 0 | 旧7e候选有权请求500；提前终止。 |
| attempt-02-query-failure | 7 / 2 / 0 / 0 | 旧7e候选缺AICT表且有权请求500；内含真实Chrome失败页4项通过。 |
| attempt-03-fixed | 80 / 2 / 1 / 0 | 夹具错误：MySQL会话+08时间误标UTC；未初始化角色绑定GET409误要求200。保留原批。 |
| attempt-04-fixed | 162 / 0 / 1 / 0 | 真实后端162项全部通过；Chrome首请求超时，0项完成，整批保留error。 |
| attempt-05-ui | 35 / 0 / 1 / 0 | 配套35项通过；无活动租户上下文时选择器不可达，浏览器0项完成。 |
| attempt-06-ui-context | 47 / 0 / 1 / 0 | 配套43项与正常Chrome4项通过；仅REVOKE未使既有连接产生预期故障，故障等待超时。 |
| attempt-07-ui-fault | 46 / 0 / 1 / 0 | 配套46项通过；登录前故障注入后浏览器停在OAuth回调，0项完成；具体根因未证实。 |
| attempt-08-ui-connected | 57 / 0 / 1 / 0 | 配套49项与正常/故障/恢复Chrome8项通过；随后reader POST /oauth2/token 500造成回调超时，整批保留error。 |
| attempt-09-ui-empty | 54 / 0 / 0 / 0 | 配套49项与空数据/拒绝Chrome5项通过，exit0；另行context探针确认共享入口缺陷，不计为功能绿灯。 |
| attempt-10-context-retest | 43 / 1 / 1 / 0 | 配套32项通过；Chrome11项10绿，初始请求过滤夹具误判1项，随后Fetch status误当函数产生Error。原批保留 |
| attempt-11-context-supplement | 35 / 0 / 0 / 0 | 配套32项与仅3项Chrome补验通过，父/浏览器exit0、正常关闭；不重跑第10批已绿跨页矩阵 |

第4批162项真实后端覆盖：匿名/未授权拒绝、未知/非法/反向/超长窗口、明确to时默认from、任务成功/失败与重试去重、执行窗口左右边界、实际业务审计数量、持久重启、SELF字典过滤、旧JWT接口/角色能力撤销、有效组织与组织关系撤销、统计能力撤销、新企业和跨租户边界。只读SQL与实际返回逐项对照，业务数据仅通过正式HTTP写入。

第8批先真实登录，再对本批独立数据库账号移除审计SELECT；新连接明确收到1142，精确核对USER+DB+连接ID后只回收本批连接。菜单GET继续200、统计GET真实500，浏览器展示失败与requestId、清除旧卡片，恢复后重试200。8项绿之后reader新登录的token端点500属于保留的后续错误；不能把统计恢复成功当成整批完成，也没有证据将其具体原因归为产品缺陷。后来脚本补全schema SELECT恢复，尚未重跑此全流程，不冒称已验证该修改。

第9批独立无故障环境完成reader真实空数据、无权指标null、旧JWT统计撤权403、旧内容清空及无页面异常5项，浏览器正常关闭；整批54/0/0/0。空态/拒绝截图顶部另有共享菜单PUT403的权限提示，内容可读，未隐藏或改写截图。

## T34-CTX-01：真实红灯、共享修复与正向补验

正式API清除管理员组织上下文后，开启全新PKCE会话进入 `/statistics`。仅返回安全布尔：多个ACTIVE成员=true、统计授权=true、active enterprise声明=false、组织context=false。界面无活动租户选择器、显示“对不起，您没有访问该资源的权限”，空侧栏；菜单请求只有平台manifest PUT200，未发出企业菜单GET。

原始探针 `attempt-09-ui-empty/context-probe-result.json` 的complete=true表示采证完成，**不表示该场景通过**。截图 `statistics-no-active-context.png` 已原生目视。JSON与截图SHA分别为 `f90376db0cbd2992ca26b062bdc345fa87321f8b2f71abf2848ff07b1ac84e9b`、`9d18ce3fceebe64fa6c6925df16c7534f9dc94c63b3578050d2450b604f18f1d`。未保存Token、授权码或敏感查询串。

源码关联：main在没有enterpriseId时使用空菜单，统计页内租户选择器因此被共享权限守卫挡住。root授权共享owner仅在main.tsx、NavBar/index.tsx和useActiveEnterprise.ts实现入口与共享内存选择：无选中时先展示外层选择卡片，不自动选首个多成员租户；选择后导航栏及各页共用状态，contextVersion立即卸载旧页面，异步菜单响应按代次保护。

共享owner的Node/真实React custom-host测试保留red-01的1/1/0/0及guard-red-01的9/1/0/0，最终guard-green-01为9/0/0/0，类型检查和lint exit0。该测试使用既有transitive reconciler，不是ReactDOM/Chrome；原始RESULT及各阶段失败在 `shared-enterprise-entry/`，不由本会话改写。

三份共享生产文件由root冻结，前后SHA核对一致。第10批真实Chrome已验证：无active/org声明、选择后真实菜单与统计200、全局/页内选择一致、改选前卸载旧卡片、第二企业响应scope正确、组织页保持选择、页内选择反向同步全局、返回统计沿用最新选择、未改active/org声明或发送context PUT、新reader不能选择前一管理员的其他租户。为观察卸载使用CDP给真实网络增加300ms延迟，无拦截或替换响应。

第10批初始无预选断言将页面HTML的GET `/statistics`误计为统计API；末尾原生Fetch的status数值又被误当函数调用，虽然网络已经403，仍使流程Error。完整43/1/1/0保留，不把这些夹具错误归为产品缺陷。清理后第11批只补“初始未发企业统计/菜单API”“真实跨租户403”“无未处理页面异常”3项，全部通过；配套32项，完整35/0/0/0、父与浏览器exit0、browserClosed=true。没有重跑162、8、5或第10批已绿跨页场景。

选择页、第二企业、跨页返回ent-acme及reader隔离截图已逐张目视，正向截图SHA见配套JSON。reader图中的403权限toast是预期拒绝反馈。新两批changedInputs=[]，共享冻结摘要一致，全部资源清理通过。

**前端企业选择只保存在UI内存，不修改JWT，也不等同于服务端组织上下文切换。** 组织选择仍走正式API与重新OIDC登录；本补验没有放宽服务器权限，reader直接访问其他企业仍403。原生红灯→共享源码/组件修复→实际正向分批补验链已闭合。

## 七项验收标准

| 原票标准 | 当前证据与结论 |
| --- | --- |
| 当前用户、租户、组织权限过滤 | JUnit与真实162矩阵覆盖实时授权、DataScope、组织和跨租户；T34-CTX-01共享入口修复后已通过定点真实补验 |
| 口径、时间范围、聚合边界 | 明确上述库存/窗口/去重/成功率语义；真实任务与左右边界通过 |
| 实际持久化任务与审计 | 39迁移；任务成功/失败/重试、业务审计只读对照、重启持久性通过 |
| 加载、失败、拒绝、空数据区分 | 组件3项；实际Chrome8项正常/故障/恢复与5项空/拒绝分批通过；共享入口正向分批补验完成 |
| 迁移、真实HTTP和Summer UI | 固定候选MySQL/PKCE/Chrome成功、拒绝、非法/故障及恢复已取证；共享入口缺陷已补验闭合 |
| 参数契约、响应与请求关联 | FieldContract及未知/非法参数400，统计403/500固定SUMMER错误、RawResponse统一信封、requestId一致，敏感扫描通过 |
| 故事/实现/测试/缺陷/准确证据 | 本文与配套JSON保存11批原计数、XML来源、截图SHA和清理；READY交root，未自行提交或关票 |

## 执行入口与清理边界

脚本位于 `scripts/acceptance/business-statistics/`，候选、摘要、唯一新证据目录分别由 `SUMMER_T34_CANDIDATE`、`SUMMER_T34_CANDIDATE_SHA256`、`SUMMER_T34_EVIDENCE_DIR`提供。执行前冻结脚本副本和SHA，旧目录不覆盖。需要本机既有Kubernetes MySQL、Docker、已安装JDK21、Node22、Chrome及summer-ui依赖，凭据仅在内存/env/stdin。

`run.py`保留第4批完整后端矩阵及原组合UI流程；该组合UI阶段未完整通过，当前不作为全绿一键命令。`ui-run.py`提供独立实际UI环境，`SUMMER_T34_BROWSER_MODE=empty`对应第9批；默认故障分支的最终schema权限恢复改动仅完成语法核对，未声称已实跑。`context-browser.mjs`是独立无故障诊断探针；`empty-browser.mjs`只验真实空/拒绝。`missing-ledger.py`与`failure-browser.mjs`保留旧候选缺表证据用途。新增 `context-run.py` 为独立无故障补验入口，不依赖先前browser.complete；已实际执行第10批与第11批；执行前强制核对SUMMER_T34_SHARED_UI_MANIFEST三份生产SHA，结束有任何捕获输入漂移即失败。正向脚本context-retest-browser.mjs的SUMMER_T34_CONTEXT_SUPPLEMENT=1只补三项缺口。实际脚本副本与摘要按批保存，最终源码相对第10批仅含夹具纠正与补验模式，第11批逐字节匹配。

十一批均回读确认本批随机schema、独立数据库账号、Redis容器及自有JVM/Chrome/Vite清理，20081/20082/20083/20573/20580无监听。故障SQL仅作用于自己的基础设施权限，业务SQL写入为false。敏感扫描零命中；共享索引、用户CustomerController及并行改动保留，未暂存或回滚。

本地原始结果、执行脚本副本、截图、输入摘要、XML及cleanup位于 `.git/summer4-closeout-20261015/frontend/t34-142/`；可提交的脱敏结构摘要见 [配套JSON](business-statistics-142-result.json)。报告只覆盖本票边界，不把历史全仓check当成本轮验证。
