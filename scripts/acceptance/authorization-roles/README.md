# T15 角色与接口授权真实验收

> 2026-11-20：授权事件页面、列表/重试和授权解释接口已退役，见 [ADR 0041](../../../docs/adr/0041-retire-authorization-event-management.md)。本目录涉及旧页面或 `/api/authorization-events` 的脚本保留为历史验收材料，不能用于当前版本验收；内部事件写入、事务一致性与投递测试继续保留。

固定候选 JAR + 独立 MySQL schema/账号、Redis 容器、真实表单/PKCE 登录、两个 JVM 和 Chrome。所有业务角色、能力选择、用户绑定与显式拒绝通过管理 API 建立；SQL 仅用于基础设施创建/清理和只读证据，脚本拒绝业务 SQL 写入。不加载外部业务 fixture，不拦截网络，不使用合成 JWT。

在仓库根目录执行，并为每次运行使用全新证据目录：

```sh
export SUMMER_AUTHORIZATION_ROLES_CANDIDATE="$PWD/.git/summer4-closeout-20261015/runtime/candidate-442e609f/candidate.jar"
export SUMMER_AUTHORIZATION_ROLES_CANDIDATE_SHA256="442e609f2962febb86d84adadb7d96940f5ae7713a06b023ef84b21404c2a453"
export SUMMER_AUTHORIZATION_ROLES_EVIDENCE_DIR="$PWD/.git/summer4-closeout-20261015/t15-live/attempt-$(date +%Y%m%d-%H%M%S)"
python3 scripts/acceptance/authorization-roles/run.py
```

保留端口：23081/23082（应用）、23083（预留）、23573（Vite）、23580（代理）。依赖 JDK21、Node22、Chrome、Docker、`docker-desktop/local-base` 中的 MySQL。敏感配置只通过进程内读取、环境或 stdin 传递，禁止回显 Secret/Token/完整日志。不得启动或停止其他任务资源。

- `run.py`：完整后端/双节点/真实Chrome流程；每个断言独立计数，产品红灯不覆盖旧回执。
- `browser_probe.py`：重新建立真实API授权引导后，仅重放Chrome；不重复完整后端套件。
- `concurrency_probe.py`：两个真实节点用相同If-Match更新同一角色，预期一个200和一个412。同时验证用户绑定竞争、条件更新后的事务回滚、幂等回执、唯一胜者审计/outbox、节点重启持久化和错误关联ID，共16个断言。此入口是否执行以独立回执为准。
- `browser.mjs`：真实角色创建、首次分配、显式拒绝及撤销、无效输入、无权限和真实停服恢复。逐步保存回执，等待响应的Promise带拒绝处理，避免定位失败掩盖后续异常。

`cleanup.json` 必须验证随机schema、专用数据库账号、独立Redis容器消失且所有保留端口释放。失败轮次及对应脚本副本保留；仅删除本轮临时Vite缓存。后端与前端实际输入分别记录，其他owner不相关参数页面改动不能冒称影响角色页，也不能假称整个UI完全稳定。

本目录只负责受控验收。发现产品红灯先保存状态码、If-Match、requestId、实际回读及最小修复建议，不修改共享生产/JUnit/UI源码，不提交、推送或关闭Issue。

无权用户会先被前端路由拦截，脚本核验真实拒绝页面，再以该用户真实PKCE登录请求角色API确认403；不假定拒绝页一定发出角色列表请求。`ROLES_DENIED_ONLY=1` 可配合 `browser_probe.py` 定向复验该流程。浏览器逐步回执使用临时文件原子替换，退出有15秒上限；收尾异常与已执行断言分别记录。

本轮新候选针对性复验使用上述候选变量与全新证据目录，执行 `python3 scripts/acceptance/authorization-roles/concurrency_probe.py`。公开验收映射与独立批次计数见 `docs/acceptance/authorization-roles.md` 和对应 JSON。

`ROLES_SHUTDOWN_ONLY=1` 只启动真实 Chrome 并执行有界退出，用于验证脚本生命周期。其 browser-result 的业务 tests=0，不可作为角色功能验收；退出码和独立 lifecycle-result 单独记录。
