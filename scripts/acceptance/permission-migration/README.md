# Native migration protocol

The repeatable entry point for these original classes is `run_native_protocol.py`:

* `AuthorizationMigrationMySqlTest`: seven original cases.
* `AuthorizationDataMigrationMySqlTest`: four original cases.
* `AuthorizationMigrationRuntimeMySqlTest`: one original case.

All twelve method names, assertions and original environment gates remain in those classes. There are no `FrozenTarget*` case copies in the repository. Both `SUMMER_PERM12_MYSQL=true` and `SUMMER_PERM13_MYSQL=true` must be explicitly enabled. The runner requires all twelve cases to execute without skips and writes one JUnit XML file per original class. Any failed, skipped, missing or aborted case, initialization error, input drift or failed process release returns nonzero.

Nine cases need a valid historical producer. Their fixture SQL runs first; a separate JVM using the **complete pinned old bridge artifact** captures the original persisted snapshot, exits, and only then does the current application start using its own **complete pinned current artifact**. The current application reads the same three frozen JSON columns and fingerprint and resolves every original planned resource incarnation before the original assertions proceed. Four ordinary-stage calls in those cases now explicitly continue with `stageFrozen` and the original fingerprint. This validates frozen cross-artifact continuation; it does not claim the current application can recreate retired legacy codes.

The three other cases remain current-only: `neverRecreatesLegacyWhenModeStateIsMissingAndManagedFactsSurvive`, `inventoriesPlatformSourcesWithoutRelabelingAnEnterpriseNamedPlatform`, and `persistsAnIdempotentDryRunWithoutChangingEngineMode`. Their method bodies are unchanged and run against the current application. The MVC-only fixture omits the same complete realtime subsystem as before; the runtime class installs it in both artifacts. The fixture lives outside the application component-scan package, so it cannot silently affect unrelated Spring tests.

The three normal test helpers live under `summer-sample/src/test/java/acceptance/perm12/`. `OldBridgeCaptureMain` and `HarnessJUnitMain` live under `scripts/acceptance/permission-migration/java/acceptance/perm12/` and are compiled only by this runner. The normal test source set does not import the JUnit platform launcher, which may be a runtime-only Gradle dependency. These helpers add no application components, permission annotations, grants or production migration behavior. `MigrationArtifactHarness` supplies the controlled lifecycle; the old Main runs solely on the old application classpath. Target compilation/launch never uses old application classes. Supplemental classpath entries are restricted to eight hashed JUnit/AssertJ support JARs and cannot contain application classes.

## Build and schedule

Build the candidate current executable JAR and resolve test dependencies first, using the normal project build and Java 21. Stop all owned Gradle daemons and test workers before this protocol phase. Do not select these three opted-in classes inside a Gradle Test JVM while a Gradle daemon occupies the other heavy-process slot. The aggregate native orchestrator must explicitly route the three classes to this runner and select only the remaining native classes for its Gradle phase. Run the phases sequentially, keeping their raw XML and source/artifact bindings separate. Merge a final readout only after each selected class has actually completed once; never count excluded or skipped Gradle classes as passing.

Compilation here uses two sequential `javac -J-Xmx512m` invocations, each exiting before the next phase. Execution uses twelve sequential one-method JUnit workers, each at `-Xmx512m`, plus at most one old bridge child at `-Xmx512m`. Each worker selects one original `class#method` and uses its own fixed case resources; no worker is reused across methods. Each old child fully exits before the current Spring context starts in the same method. The runner verifies the worker process group and observed case ports are gone before the next method starts. No Gradle JVM runs concurrently. This is a two-heavy-JVM heap budget, not a claim about total RSS. Other owned Java application processes must also have been released by the resource owner.

Supply immutable, approved old/current executable artifacts and their exact SHA256 values. Obtain a **new independent** resource lease; historical native grants do not authorize this phase. The external owner must provision twelve independent MySQL schema/account pairs, one per original method, and exclusively owned Redis DB1 case namespaces. Each schema and its same-name account must be `summer_perm12_harness_` plus ten lowercase hex digits: 32 characters, with no appended method suffix. All twelve names must differ; each account receives privileges only on its exact schema, including the DDL privileges required by the original RENAME test. The owner's outer `try/finally` must release exactly all schema/account/keys it acquired in its ledger, verify ports and PIDs are gone, and preserve the cleanup evidence even when this command fails. Old init, fixture SQL, old capture and current continuation within one method share that method's schema; no schema or namespace is shared across methods. This runner creates no schema/account/Redis lease and does not delete unknown resources or choose engine mode.

The owner supplies credentials only through the secret environment variable `PERM12_HARNESS_CASE_PROPERTIES`: a JSON object mapping each exact original `class#method` to its full flat Spring properties. Do not put credentials in command-line flags, the public lease record or saved configuration. The batch runner requires this twelve-entry map; a top-level `SPRING_APPLICATION_JSON` alone does not supply case properties. For each worker it sets one fixed `SPRING_APPLICATION_JSON` from that case entry and removes the batch map before any Java process starts. The old child inherits only the same case environment, and the current context and fixture JDBC use that same fixed case configuration. Required settings in every case entry include:

* Local MySQL URL for that case's exact leased schema, its same-name username and explicit password, and `spring.flyway.enabled=true`.
* Redis host `127.0.0.1`, port from the owned local service, database `1`, explicit password, and both `spring.session.data.redis.namespace` and `summer.sample.session-index.namespace` starting with that case's exact `summer:perm12-harness:<leaseId>:case-NN:` prefix (`NN` is `01` through `12` in runner order). The two settings must remain within the case prefix, rather than merely the batch root prefix.
* Explicit `summer.sample.authorization.legacy-scope-compatibility=false`, empty `summer.sample.authorization.managed-bootstrap-domains`, and the actual security master key.
* `server.address=127.0.0.1`, `server.port=0`, both sample event scheduling flags false, and `spring.quartz.auto-startup=false`.
* Existing normal application settings required by the complete artifacts. Use `logging.level.root=ERROR` and `spring.main.banner-mode=off` to avoid incidental credential output.

Do not substitute H2 for the historical MySQL chain, edit a historical migration, synthesize an old anchor/permission code, or relax resource/blocker assertions to make a fixture pass. If the old artifact cannot start against the evolved schema or an original resource cannot resolve on the target, retain the failing report and fix the approved fixture/upgrade contract separately.

## Frozen inputs

The `--source-inputs` JSON is a map from these nine repository-relative paths to their SHA256: this Python runner, the five helper Java files, and the three original Java test files. Create it after the candidate is finalized and before the lease is approved. A reproducible source-freeze command from the repository root is:

```sh
python3 - "$PROTOCOL_SOURCE_INPUTS" <<'PY'
from pathlib import Path
import hashlib, json, sys
root = Path.cwd()
helper = root / 'summer-sample/src/test/java/acceptance/perm12'
standalone = root / 'scripts/acceptance/permission-migration/java/acceptance/perm12'
tests = root / 'summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/migration'
paths = [root / 'scripts/acceptance/permission-migration/run_native_protocol.py']
paths += [helper / name for name in ['RunSettings.java', 'MvcOnlyFixture.java', 'MigrationArtifactHarness.java']]
paths += [standalone / name for name in ['OldBridgeCaptureMain.java', 'HarnessJUnitMain.java']]
paths += [tests / (name + '.java') for name in ['AuthorizationMigrationMySqlTest',
    'AuthorizationDataMigrationMySqlTest', 'AuthorizationMigrationRuntimeMySqlTest']]
with Path(sys.argv[1]).open('x') as output:
    json.dump({str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in paths}, output, indent=2)
    output.write('\n')
PY
```

The `--test-dependencies` JSON is an array of `{ "path": "/absolute/resolved.jar", "sha256": "..." }` entries for exactly `junit-jupiter-api`, `junit-jupiter-engine`, `junit-platform-launcher`, `junit-platform-commons`, `junit-platform-engine`, `opentest4j`, `apiguardian-api` and `assertj-core`. Use the versions resolved by the candidate build, hash their actual cached files, and freeze that manifest. No dependency download or Gradle process is started by this runner. A changed dependency or source manifest requires a newly bound lease.

The non-secret `--lease-record` must contain `resourceContract=canonical12-method-schema/1`, the actual owner's `owner`, new `leaseId`, `databaseAndRedisOwned=true`, `redisDatabase=1`, batch-root `redisNamespacePrefix=summer:perm12-harness:<leaseId>:`, absolute fresh `runDirectory`, `existingOwnedJavaPids=[]`, and `maximumOwnedJavaProcesses=2`. It must bind `oldArtifactSha256`, `currentArtifactSha256`, `sourceInputsSha256` and `testDependenciesSha256` to the exact approved files. A single top-level schema binding is insufficient; single-schema leases are rejected.

The lease must also have an ordered `cases` array with exactly the twelve original `class#method` IDs in the runner's `CASE_IDS` order. Every case object contains exactly these five public fields: `caseId`, `schema`, `account`, `runDirectory` and `redisNamespacePrefix`. Its directory must be `<runDirectory>/case-01` through `case-12`, and its namespace must be the matching batch-root prefix plus `case-01:` through `case-12:`. Its schema/account and namespace must match its entry in `PERM12_HARNESS_CASE_PROPERTIES`. No credentials or extra fields belong in a case object. This record describes resources actually acquired by the owner; editing a placeholder record is not acquisition or approval. Completed batches do not authorize this fresh lease.

```sh
## Exact artifact bytes used by the 2026-11-10 preparation, not a runtime grant:
PROTOCOL_OLD_SHA256='c2b6eb557da97e5a058241640cbbdfad19ff64f460462a7cc19805ab2245a5ff'
PROTOCOL_CURRENT_SHA256='30fa2fee82e5b6c709848d3319ef42092b3a766a52a55452811aed5cea80ec01'
python3 scripts/acceptance/permission-migration/run_native_protocol.py \
  --execute \
  --java "$PROTOCOL_JAVA21" --javac "$PROTOCOL_JAVAC21" \
  --old-artifact "$PROTOCOL_OLD_BRIDGE_JAR" --old-sha256 "$PROTOCOL_OLD_SHA256" \
  --current-artifact "$PROTOCOL_CURRENT_JAR" --current-sha256 "$PROTOCOL_CURRENT_SHA256" \
  --source-inputs "$PROTOCOL_SOURCE_INPUTS" \
  --test-dependencies "$PROTOCOL_TEST_DEPENDENCIES" \
  --lease-record "$PROTOCOL_NEW_LEASE_RECORD" --run-dir "$PROTOCOL_NEW_RUN_DIR"
```

The run root contains exact application classpaths, stage argv/PIDs/exits with case IDs/schema bindings and heap budget, sanitized stage logs, three aggregated original-class XML files and `junit-result.json` for all twelve cases. Each `case-01` through `case-12` subdirectory contains its applicable original-producer reports with actual ports, old/target context open/close records, target snapshot/incarnation bindings, one-method XML and `case-result.json`. Owner wrappers must read context/process/port receipts recursively and release all twelve exact case resource bindings in `finally`. The old child reports success only after context close and evidence writes succeed; the parent validates init/capture status, phase, closed flag, PID, artifact, lease and owner. Its transported report map reads integer counters as Long (`USE_LONG_FOR_INTS`), preserving the producer's long resource counters and the original `2L` assertion without changing production numbers.

Startup failure remains nonzero even if no case XML could be written. Every TERM/KILL requires a fresh process-group check against the actual registered leader birth/argv and attributable old child birth/argv/parent or matching start record. Unknown members are preserved and fail release. After KILL the runner waits a bounded interval and requires actual group absence; all observed loopback ports must also be absent. It never kills an unknown listener that rebound a historical port. Original test failure remains recorded if cleanup also fails, and successful cleanup cannot turn a failed test into success. End-of-run artifact/source/dependency/manifest drift also fails the batch. A Spring context closing is never reported as its worker JVM exiting. External resource cleanup is explicitly still required after the runner returns.

These are internal service transaction/checkpoint tests. They do not replace the separately approved operator command, writer-fence/provenance, encrypted backup/restore, HTTP comparison or controlled cutover acceptance lanes. The original synthetic DATA verification contract still asserts `cutoverAuthorized=false`. A passing new twelve-case batch does not erase a historical failed batch or establish full-suite credit.
