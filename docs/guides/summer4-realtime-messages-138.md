# T30 WebSocket messages

T30 covers user stories 40 and 42: an authenticated enterprise member can send, receive and acknowledge ordered messages over WebSocket, while the durable HTTP API provides replay after reconnect.

## Contract

- `POST /api/v1/enterprises/{enterpriseId}/messages` requires `enterprise.messages.write` and deduplicates by sender, enterprise and `clientMessageId`.
- `GET /api/v1/enterprises/{enterpriseId}/messages?after={sequence}` and `GET /{messageId}` require `enterprise.messages.read`.
- `POST /{messageId}:ack` requires `enterprise.messages.write` and is idempotent.
- `GET /connections` exposes only the enterprise-scoped connection count and configured limit.
- `GET /ws/v1/messages?enterpriseId=...&after=...` validates the JWT principal, active enterprise membership, capability claim and numeric replay cursor during the handshake.

WebSocket frames use `send`, `ack` and `ping` commands, with `connected`, `message`, `ack`, `heartbeat` and `error` frames. The per-enterprise limit is 32 connections; policy failures close the connection and malformed commands receive a `SUMMER_MESSAGE_INVALID` frame. Messages are persisted before publication. When `summer.sample.realtime.redis-enabled=true`, Redis pub/sub provides immediate cross-node delivery while the relational log remains the replay source.

## Verification

- `:summer-sample:compileJava` passed.
- T30 HTTP tests were executed but the application context was blocked by an unrelated existing `StatisticsService` injection ambiguity (`requestMappingHandlerMapping` and `controllerEndpointHandlerMapping`), so no HTTP assertion is claimed as passed.
- Summer UI `npm test`: 19 tests passed plus the realtime contract check.
- Summer UI `npm run typecheck` and `npm run build` passed.

No credentials or raw token values are persisted in messages, logs, fixtures or this guide.
