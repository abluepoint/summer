# T30 实际验收

在仓库根运行，要求 Python3、JDK21、Node22、Chrome、kubectl 和现有 local-base MySQL/Redis。脚本从现有 Secret 在内存取凭据，创建随机独立 schema、非 root 应用账号和精确 Redis namespace；不要将密码写进命令或文档。保留 19081–19083、19573、19580 给本次验收。

设置 `SUMMER_REALTIME_CANDIDATE` 为不可变候选 JAR 的绝对路径，`SUMMER_REALTIME_CANDIDATE_SHA256` 为其精确 SHA-256，`SUMMER_REALTIME_EVIDENCE_DIR` 为全新证据目录。当前验收候选摘要为 `442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453`。

分别运行 `python3 scripts/acceptance/realtime/run.py` 和 `python3 scripts/acceptance/realtime/browser_probe.py`；每个入口使用不同新证据目录。前者验证真实 HTTP/WebSocket、双 JVM、并发有序、撤销、限流、超时与存储故障恢复；后者启动实际 UI，通过 Chrome 完成 PKCE、同源 WebSocket、发送确认、刷新恢复、拒绝和无效输入。`socket.mjs` 使用现有 Playwright 的 WebSocket 依赖，`ui-server.mjs` 提供实际同源 HTTP/upgrade 代理，均不模拟业务响应。

检查最终结果及 cleanup 回读，不能凭启动成功或中间输出判通过。临时环境全部由入口 finally 清理，业务授权不以 SQL 注入；故障脚本仅改名本轮独立消息表并恢复。完整验收条款、结果与已知验证边界见 [验收记录](../../../docs/acceptance/realtime-messages-138.md)。
