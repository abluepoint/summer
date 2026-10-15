# #142 统计错误码 Standards 定向补救

2026-10-16。状态：**定向验证通过，交由root复核与处理提交、推送和Issue状态**。本补充不替代原11批验收或原审查报告。

ADR0009要求业务错误与框架错误分开治理。统计非法输入属于通用参数错误，原控制器的SUMMER_STATISTICS_INVALID改为CommonErrorCode.INVALID_ARGUMENT，使用注入的ErrorStatusResolver解析状态。403和500分支保持原有行为；统计Service/Models/UI、共享入口及T36均未修改。

专用回归先将既有反向窗口断言改为SUMMER_INVALID_ARGUMENT，有效红灯1/1/0/0、exit1，实际码为SUMMER_STATISTICS_INVALID。最小修复后完整StatisticsHttpTest为11/0/0/0、exit0，包含反向、超过366天、无效ISO日期、规范化requestId及未知参数。共享Gradle锁内执行，heap1g、context cache4；每批独立原XML已保留，删除system-out/system-err捕获输出。测试使用Spring/H2和受管合成JWT，与下面的真实环境结果分别记账。

root统一构建的候选SHA256为`6fbb80b774b51f98250b006eee243f6a1153086c77de2d08889e055a1f9c953b`。实际执行standards-probe.py，真实MySQL、隔离Redis、严格PKCE登录（legacy compatibility=false），仅通过正式HTTP配置业务权限，无业务SQL写入或网络mock。统计三份主源与candidate-inputs逐项一致，执行期间输入SHA未变化，执行脚本副本与当前公开脚本字节一致。

| 定向检查 | 实际结果 |
| --- | --- |
| 反向时间窗口 | 400 / SUMMER_INVALID_ARGUMENT，缺省requestId生成为UUID |
| 超过366天的窗口 | 400 / SUMMER_INVALID_ARGUMENT，非法requestId规范化为statistics_invalid__window |
| 无效ISO日期 | 400 / SUMMER_INVALID_ARGUMENT |
| 未知参数 | 400 / SUMMER_INVALID_ARGUMENT |
| 日期参数超过64字符 | 400 / SUMMER_INVALID_ARGUMENT |
| 精确366天窗口（2024-01-01至2025-01-01） | 200 / success=true |

六项均验证响应头与响应体requestId一致，错误响应success=false且data为空。原生结果**6/0/0/0，exit0**，无敏感日志命中；隔离schema、应用账号、Redis容器均已删除，20081/20082/20083/20573/20580五端口无监听。独立结果、cleanup、执行脚本副本和候选来源位于`.git/summer4-closeout-20261015/statistics-standards-fix-01/runtime-01/`，详细摘要及SHA见配套JSON。

唯一生产增量为StatisticsController。冻结源、红绿XML、最终FILES/READY和交接见`.git/summer4-closeout-20261015/statistics-standards-fix-01/`。未自行bootJar或重复执行已通过的11项JUnit。

原11批、故障/恢复8项、空/拒绝5项及共享入口10/11批的成功和失败保持原状，未重复原162项或所有Chrome。现有run.py只加强未来非法参数精确错误码断言，未重新执行完整矩阵；本次真实6项不能冒称历史矩阵在新候选上的全量重跑。
