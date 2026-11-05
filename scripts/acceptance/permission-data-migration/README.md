# DATA migration acceptance support

These files are acceptance sources. Normal Gradle source sets do not compile the Java fixture into the product. An explicit isolated runtime init script may add this directory and `../permission-migration` while preserving `<module>/build` output paths.

`PermissionDataMigrationFixture` imports the existing controlled MVC/menu fixture and removes the whole optional realtime subsystem. On the first initialization of a newly owned schema it seeds explicit LEGACY domains, active identity/membership and persistent legacy role/DATA facts. It never repairs a missing domain on restart. The local file command channel calls the actual migration application service, supports holding a DATA transaction for an owned-process interruption test, and seeds owned business owner facts that public constructors cannot produce. It has no HTTP administrative bypass and no mode-toggle command. It must never be included in a production deployment.

`compare_http.generate(plan_report, scenarios, transports, runtime_inputs, output_directory)` generates the runtime receipt. Each `(old|new, subject)` transport must perform actual authenticated HTTP, keeping its token in memory, and return status/body/headers. Every PROVED subject/default/resource/action cell must have exactly the required operations. A scenario provides relative business API probes, methods, bodies, a logical business identity, and either an item/id path or a real record result. An optional countProbe always executes, including when the list denies. The complete ID set must match both returned pagination total and the independent count result. Unsupported statuses and inconclusive list/count combinations fail closed.

The generator compares each probe outcome and ID set before creating compatible aggregate receipt fields. 403 and 404 are controlled denials; success versus denial is different even for empty results. Scenario headers use a small noncredential allowlist, and credential fields/query parameters are rejected before transport or artifact serialization. Actual build/source hashes, mode/restore metadata, observations and the scenario matrix bind runtimeInputsFingerprint. The runner must additionally assert expected legacy fixture ALLOW sets and read the owned persistent row/items/history/outbox before and after writes; HTTP 403 alone does not prove absence of a side effect.

Use an isolated old source/proof schema that remains LEGACY. Complete staged identity/DATA steps there and prove that old reads remain unchanged. Restore an immutable protected backup into a separate target while it has no running application node, then set the target fixture to MANAGED with a revision advance and marker before cold startup. Compare actual operations with tokens issued before staging, and import the generated receipt into the still-LEGACY source/proof schema. A later cold source-node move can establish two final MANAGED nodes. This is comparison evidence; production enrollment/fleet cutover/contract remains the integration task's gate.

The project-local run driver and results live in `.scratch/permission-management/execution/13`. Infrastructure use is serialized by the controller, with the `summer_perm13_` schema/account prefix, verified empty exclusive Redis DB2, and recorded own PIDs/ports. No script here authorizes a lease or changes shared MySQL/Redis service state. SQL backups containing test OAuth state must be encrypted and kept out of ordinary logs.

Fast generator regressions use controlled transports and are not HTTP acceptance:

```sh
python3 -m unittest discover -s scripts/acceptance/permission-data-migration -p test_compare_http.py -v
```

The local command guide is `docs/guides/permission-212-data-migration.md`. Actual acceptance status comes from the corresponding run result and raw observations, not from this README or a synthetic receipt.
