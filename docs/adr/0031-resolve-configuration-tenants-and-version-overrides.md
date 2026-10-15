# Resolve configuration tenants and version overrides independently

Status: accepted for the Summer Sample configuration domain. Partially supersedes the fixed-tenant and override-history boundaries in ADR 0028; its expression grammar, default denial and override precedence remain in force.

## Decision

Configuration routes accept an explicit enterprise path. Existing unqualified routes resolve agreeing path/header/query selectors, then the authenticated active-enterprise claim or exactly one active membership. Missing, ambiguous, conflicting or inactive contexts fail closed. There is no ent-acme fallback. Anonymous public reads require an explicit tenant and still pass public-state and persisted data-policy checks. The existing ent-acme policy seed is sample initialization, not a default policy for other tenants.

The validated tenant follows configuration reads, writes, uniqueness checks, history, idempotency, events, audit, cache keys, fallback proofs and plugin facades. Parameter keys and dictionary codes/item values are unique within a tenant. V32 preserves old data and changes dictionary item references to composite tenant/code keys. Organization targets and current organization context must belong to active metadata in the selected enterprise.

V33 records immutable override revisions independently of base parameter/dictionary history. Drafts do not change runtime values. Publish, disable and rollback each require their own action and an expected aggregate version; replacing a draft also checks the version. Initial draft creation requires create plus the draft endpoint's update action. Existing immediate PUT requires create/update plus publication or disable as applicable. History requires read. Legacy write scopes retain write-action compatibility, not implicit history read.

Every successful override write appends a revision; publishing selects a draft, rollback selects a non-draft revision and revalidates its values against today's base constraints and visible dictionary items. Optimistic aggregate version and immutable revision are separate numbers. A draft-only override does not participate in precedence. A private, disabled or scope-hidden published override still blocks lower-priority values with NOT_FOUND. Audit/outbox records omit configuration values.

The SQL list projection normalizes MySQL DATETIME/LocalDateTime using the application's configured UTC JDBC/Hibernate zone. It does not infer the workstation time zone. Dictionary notifications recheck current read authorization and data scope before delivery; heartbeat catch-up reads a bounded latest persisted window, allowing another process's committed notifications to be observed.

## Validation boundary

HTTP tests, seeded V31-to-current MySQL upgrade, tenant-specific fallback failure injection and real Redis cache/outbox tests are recorded in the T18/T19 acceptance guide. Two cache objects over real Redis are not evidence of two independently deployed application processes. Real browser login, context switching and complete application-node failover remain integration acceptance owned by the coordinating task.
