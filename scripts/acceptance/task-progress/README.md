# T29 real SSE protocol acceptance

Run from the repository root. `environment.py` provisions a disposable MySQL schema/account and Redis container and uses only ports 23081/23082/23083/23573/23580. It reads credentials in memory; do not print them. A fixed candidate digest is mandatory.

Set `SUMMER_TASK_PROGRESS_EVIDENCE_DIR` to a fresh absolute directory, `SUMMER_TASK_PROGRESS_CANDIDATE` to an immutable production JAR, and `SUMMER_TASK_PROGRESS_CANDIDATE_SHA256` to its digest. First run `python3 scripts/acceptance/task-progress/build-fixture.py`, then choose exactly one runner using the same environment:

- `run.py` (or `run.py --protocol-only` to omit the separate capacity gates): Cookie-only real SSE, actual Quartz progress, correlation, invalid cursors, Last-Event-ID, replay window, heartbeat and connection checks.
- `capacity.py`: targeted tenant capacity and closed-socket reclamation.
- `backpressure.py`: four unread TCP streams with a small receive buffer, actual blocked socket-write stack verification, independent Quartz completion and healthy stream delivery, release and idle-worker retirement.
- `global-capacity.py`: nine real tenants provisioned through management APIs, eight tenants filling 256 connections, global rejection in the ninth, and reclaimed-slot reuse.

Never run these concurrently. Each owns the same dedicated port group. All HTTP business setup uses real login, roles and membership APIs. SSE requests use the actual login Cookie, never Bearer credentials or URL tokens. Error metadata and event data are filtered; credentials remain in memory.

The external `SseTaskConfiguration` is a `RegisteredTaskHandler` bean invoked by real Quartz. It reports progress via the public handler callback to generate a large replay/backpressure window. It does not insert database events, add controllers or replace authentication. It is compiled with `-proc:none`, recorded separately and never included in the production JAR.

Always read `runtime-result.json`, candidate/fixture provenance, HTTP metadata and `cleanup.json` together. Real protocol checks are separate from MockMvc/unit checks and earlier task-page browser evidence. Preserve each failed batch. Closed idle sockets may need heartbeat writes before a peer disconnect becomes observable; report the measured bound and do not infer a leak from an arbitrary one-second delay.

The global-capacity fixture increases only the HTTP request-header ceiling to 64 KiB for one real administrator with nine tenant memberships; its SSE authentication still uses the opaque session Cookie. It does not alter connection limits, timeouts or the production executor. The role API registers unresolved permission codes in each tenant, so no direct capability-write permission is assumed for a new tenant administrator.
