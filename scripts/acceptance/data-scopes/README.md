> 历史验收入口：旧数据权限菜单和 `/data-scopes` API 已于 2026-11-20 退休。以下脚本仅用于匹配的历史制品，不能用于当前版本；当前契约见 [ADR 0040](../../../docs/adr/0040-retire-legacy-data-scope-management.md)。

# T17 actual data-scope acceptance

`run.py` runs real PKCE/HTTP, MySQL readbacks, isolated Redis, policy scopes, two tenants/users, restart persistence and Chrome. `ui-run.py` replays only the UI recovery flow and its required real management API setup. Both use the current shared `summer-ui` workspace and record related source hashes; no synthetic principal, network mocking or SQL business-data writes.

Prerequisites: Java 21 at the path in `environment.py`, Node 22, existing `summer-ui/node_modules`, Chrome, Docker `redis:7-alpine`, and the existing docker-desktop/local-base MySQL infrastructure. Ports 23081/23082/23083/23573/23580 must be free. Credentials are generated or read in memory; do not print them.

Set `SUMMER_DATA_SCOPES_EVIDENCE_DIR` to a fresh absolute evidence directory, `SUMMER_DATA_SCOPES_CANDIDATE` to the immutable JAR, and `SUMMER_DATA_SCOPES_CANDIDATE_SHA256` to its exact digest. Then run `python3 scripts/acceptance/data-scopes/run.py`, or `python3 scripts/acceptance/data-scopes/ui-run.py` for the focused UI check. Do not reuse an evidence directory or run these scripts simultaneously.

The accepted backend candidate is `993ad757888f39802a7c03c1509d20a2f191a8616c5025eaff2712cf2eff320a`. The browser signs in through the actual form, verifies invalid local JSON, invalid server conditions, recovery after a successful save, refresh/preview version, and a real unauthorized user. `browser-lifecycle.mjs` owns and bounds shutdown of its Chrome child.

Read `result.json`, `browser-result.json`, `ui-provenance.json`, and `cleanup.json` together. HTTP/SQL assertion counts and browser scenario counts are distinct. Do not count an exit code alone as success or replace old failures with a later green result. The original full run's missing browser exit gate is documented in `docs/acceptance/data-scopes.md`; the current runner requires browser exit zero.
