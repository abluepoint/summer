# Permission management HTTP regression

Run from the repository root with Python 3.10+ (standard library only). This entry exercises real HTTP against two current application nodes and their shared real MySQL database. It does not create containers, services, schemas, accounts, grants, or tenants, and has no dependency on scratch files.

Supply two distinct application origins backed by an explicitly owned, disposable test database with migrations applied, and an existing owned domain. The authenticated principal must already have active persistent management membership/role grants and the matching OAuth management scopes (enterprise.roles.read/write, or platform.capabilities.read/write for an isolated platform domain). OAuth scopes cap client delegation and do not create persistent management grants. Creating these grants is intentionally outside this runner.

Supply a current Bearer credential through `SUMMER_PERMISSION_ACCEPTANCE_TOKEN` in the process environment; never put its value in the command, report, or repository. Example invocation, after supplying that environment variable securely:

```sh
python3 scripts/acceptance/permission-management/run.py \
  --origin http://127.0.0.1:38101 \
  --secondary-origin http://127.0.0.1:38102 \
  --owned-domain /api/v1/enterprises/owned-test-domain \
  --report .scratch/permission-management/reusable-result.json
```

The runner covers three-level classification, default/explicit access modes, immutable identity/type, incomplete DATA rejection, pagination, null clearing, 400/409/412/428 responses, durable original-response replay across nodes, concurrent identical receipts, concurrent cyclic group moves, concurrent permission moves with one version winner, and permanent code reservation after deletion. API redirects are not followed: the original 3xx status fails its expected-status check. Successful POST identities are registered before assertions, including concurrent requests, so an unexpected success status still participates in cleanup. It exits nonzero on a failed check, transport/setup error, or incomplete cleanup. Reports contain named outcomes, counts and the runner source digest; raw responses and authentication material are not logged.

Cleanup uses only IDs created by this invocation, through versioned DELETE requests. Permission identity reservations, mutation receipts, audit and outbox history deliberately remain because they are durable business facts. Dispose the owned test database/account using your environment's established cleanup procedure after collecting the report. Never point this runner at a shared production or user domain.

This runner does not replace native-browser checks, direct MySQL FK/fault-injection transaction checks, or the final cross-ticket authorization suite. Those are separate acceptance evidence. It provides a persistent regression entry for the management contract without requiring privileged database access.
