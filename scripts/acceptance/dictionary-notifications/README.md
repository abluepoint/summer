# T31 actual dictionary notification acceptance

Run from the repository root with JDK 21, Node 22, Google Chrome, the existing `docker-desktop/local-base/mysql-0` dependency and local `redis:7-alpine` image available. Credentials are consumed in memory from the authorized Kubernetes Secret and passed only through process environment/stdin. Never print credentials, raw browser network headers or JVM logs.

Set `SUMMER_DICTIONARY_NOTIFICATIONS_EVIDENCE_DIR` to a fresh absolute evidence directory, `SUMMER_DICTIONARY_NOTIFICATIONS_CANDIDATE` to an immutable production JAR and `SUMMER_DICTIONARY_NOTIFICATIONS_CANDIDATE_SHA256` to its exact digest. Then run:

```sh
python3 scripts/acceptance/dictionary-notifications/run.py
```

The runner provisions a disposable MySQL schema/account and isolated Redis container, uses ports 23081–23083/23573/23580, and runs the real shared Summer UI source through Vite. Business roles, users, dictionary drafts and lifecycle mutations use authenticated HTTP. SQL is limited to infrastructure DDL and read-only evidence. No synthetic identity, mocked network, event insertion or production-JAR fixture is used.

The subscriber page runs against one JVM while publishing occurs through a second JVM. Chrome observes the actual native EventSource frames, public rereads and rendered state. The test edits an unsaved form, disables its selected value, then destroys only the real dictionary SSE upstream socket through a local transport proxy. Both JVMs, menu reads and business HTTP stay live. Another publication occurs while SSE transport is blocked; restoring transport lets native EventSource resume with Last-Event-ID. The proxy propagates upstream aborts and pre-response errors by closing downstream sockets, without synthesizing responses. Whole-dictionary disable and rollback exercise automatic unavailable/current-snapshot reads. API checks separately cover anonymous/unauthorized reads, invalid cursors, correlation and migrations.

Read `runtime-result.json`, `browser-result.json`, candidate provenance, source/script digests and `cleanup.json` together. Report their actual named-check counts separately; HTTP request count and process count are not test counts. A completed runner or browser with failed checks does not satisfy acceptance. Preserve failed batches and their executed script snapshots. Never run multiple batches concurrently on this port group.

The browser owns its Chrome process and records whether forced termination was needed. Screenshots are taken only after the password form has gone. The temporary Chrome profile, runtime resources and caches must be removed after inspection. Changes in unrelated shared source must be reported separately from the dictionary inputs under test.

Set `SUMMER_DICTIONARY_NOTIFICATIONS_RECOVERY_ONLY=1` to run only the affected recovery matrix after retaining the initial contract evidence. Initial preparation still uses real HTTP/UI but is not counted as repeated business checks. Do not treat a whole-JVM authorization outage as a reason to retain stale menu permissions.

For a standalone process-cleanup probe, use a fresh evidence directory and run `browser.mjs` from `summer-ui` with `SUMMER_DICTIONARY_NOTIFICATIONS_LIFECYCLE_ONLY=1`. It opens actual Chrome at about:blank, follows the same cleanup path and records zero business checks. Capture its process exit separately; it cannot establish notification behavior. Current cleanup checks both exitCode and signalCode and closes owned child stdio handles. Preserve any prior forced runner exit independently from this probe.
