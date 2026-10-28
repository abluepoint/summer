# Version authorization changes through an outbox and Redis Stream

Authorization role, capability, menu and data-scope mutations append a MySQL
outbox row in the same application transaction. A scheduler publishes rows to
`summer:authorization-change-events` with at-least-once delivery and bounded
retry/dead-letter state. Every node consumes through its own Redis Stream
consumer group and evicts its local authorization cache projection before
applying a shared receipt idempotently.

The event carries the enterprise boundary, resource identity, event type,
monotonic resource version and request correlation identifier. A node that
missed a stream delivery reloads its persistent authorization projection at the
HTTP decision seam, so a Redis restart or lost notification cannot preserve an
indefinitely stale permission decision. Audit rows are written before the
outbox event is enqueued, preserving the mutation-to-event order.

## Redis 重建与前端权限收敛

前端定时读取策略已由 [ADR 0033](0033-refresh-menus-on-context-and-return-events.md) 替代；以下保留原决策背景。服务端 outbox、Redis Stream 与鉴权策略保持有效。

消费组不存在时从 `0-0` 建组，先处理已有 pending，再读取新条目。不能从
`latest` 建组：Redis 丢失数据后，outbox 可能先恢复发布首条事件，晚建组会跳过它。
已有流记录可重新投递；持久化 receipt 保持幂等，节点仍先清除本地投影，
不依赖共享 receipt 代替每个节点的失效处理。

前端在可见页面每 10 秒重读服务端菜单与动作权限，并在 focus/恢复可见时补读；
初始加载以外不重复提交 manifest。单次读取超时为 10 秒，失败清除旧权限，
恢复后重新取得当前授权。此周期是健康网络及活跃页面下的调度值，并非后台
标签页或网络拥塞时的硬实时保证；后端每次请求仍实时判定持久授权。
菜单和动作内容未变化时不重建页面，保留未提交表单；读取失败或权限真正撤销
时优先关闭访问，表单保留不覆盖此安全边界。
