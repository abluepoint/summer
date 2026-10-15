# T30 WebSocket 双向消息

T30 covers user stories 40 and 42: an authenticated enterprise member can send, receive and acknowledge ordered messages over WebSocket, while the durable HTTP API provides replay after reconnect.

## Contract

- `POST /api/v1/enterprises/{enterpriseId}/messages` requires `enterprise.messages.write` and deduplicates by sender, enterprise and `clientMessageId`.
- `GET /api/v1/enterprises/{enterpriseId}/messages?after={sequence}` and `GET /{messageId}` require `enterprise.messages.read`.
- `POST /{messageId}:ack` requires `enterprise.messages.write` and is idempotent.
- `GET /connections` exposes only the enterprise-scoped connection count and configured limit.
- `GET /ws/v1/messages?enterpriseId=...&after=...` validates the JWT principal, active enterprise membership, capability claim and numeric replay cursor during the handshake.

WebSocket frames use `send`, `ack` and `ping` commands, with `connected`, `message`, `ack`, `heartbeat` and `error` frames. The per-enterprise limit is 32 connections; policy failures close the connection and malformed commands receive a `SUMMER_MESSAGE_INVALID` frame. Messages are persisted before publication. When `summer.sample.realtime.redis-enabled=true`, Redis pub/sub provides immediate cross-node delivery while the relational log remains the replay source.

## 运行与验证

连接持续校验活动成员、持久化权限和 Cookie 会话 lease；撤销后以 1008 关闭。页面使用当前活动企业，只读成员没有发送和确认按钮。消息先持久化，再按每条连接的游标串行补读，避免本地和 Redis 重复通知造成重复或乱序；客户端确认帧不推进消息游标。

`summer.sample.realtime.idle-timeout-ms` 默认 60000，`summer.sample.realtime.send-timeout-ms` 默认 5000。客户端每15秒 ping；空闲1001、连接数超限1013、权限撤销1008、传输或存储故障1011。连接上限32按每节点每企业计算，未声称集群全局限额。发送阻塞由容器写超时和独立 watchdog 限制。

当前固定候选真实协议16/0/0/0、Chrome7/0/0/0；JUnit定向与模块结构17/0/0/0，Node30项、类型检查和构建均通过。实际计数、原缺陷、逐AC、候选摘要及清理结果见[验收记录](../acceptance/realtime-messages-138.md)，复现见[scripts/acceptance/realtime](../../scripts/acceptance/realtime/README.md)。历史 Statistics 注入阻塞已不再代表当前验收结果；原始失败证据仍保留。

No credentials or raw token values are persisted in messages, logs, fixtures or this guide.
