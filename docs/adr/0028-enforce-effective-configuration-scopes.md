# Enforce persisted effective scopes for configuration reads

Status: accepted for the Summer Sample T18/T19 implementation; browser acceptance remains separate.

## Decision

One validated DataScopeExpression compiles to row predicates, JPA Specifications and bound SQL template predicates. TENANT remains an unconditional boundary under AND/OR and DENY. Only supported fields, comparators and structured identifiers are accepted; policy values never become SQL syntax. Organization-tree expansion reads tenant-scoped active organization metadata.

The resource policy is the maximum range. When ROLE bindings exist for a resource, active roles assigned through summer_authorization_user_binding contribute a union of allowed ranges within that maximum. An unassigned subject then receives no role range. USER bindings select rows to deny and are subtracted last. Both bindings and role assignments are read from persistence for every decision. Preview accepts a target subject and sample row attributes; roleAllows, userDenies and organizationIds cannot supply hypothetical authority. V29 persists versioned, checksummed bindings, with audit and authorization outbox events.

Public configuration reads require the public marker, current publication/activation state, applicable managed interface authorization and the current data-scope predicate. Anonymous callers use the same resource policy with no subject or organization; a TENANT-only policy can therefore intentionally allow anonymous public data, while SELF or organization policies cannot manufacture a principal. Persisted interface denials also apply to authenticated public callers.

Dictionary types and items are selected with database scope predicates before projecting immutable snapshot values. Cache/fallback access occurs after visibility checks and is keyed by published version, effective scope and visible item identities. This supplements ADR 0027 (dictionary snapshots); the legacy published-without-snapshot path remains available, with publish authorization on changes that directly affect that public representation.

## Configuration actions and overrides

Stable action codes are system.parameter.{read,create,update,publish,disable,rollback} and platform.dictionary.{read,create,update,publish,disable,rollback}. The historical *.write scope remains an explicit all-write-actions scope for compatibility. Managed role permissions and explicit denials still evaluate the individual action. PATCH status/public flags cannot bypass publish/disable authorization; direct parameter value updates also need publish authorization because they change runtime state. Creation that is immediately public requires publish authorization.

V30 stores typed parameter or dictionary-option overrides independently of base definitions. Current organization override has precedence over the tenant override (_tenant), then the base value. An existing but private, disabled or inaccessible higher-priority override yields NOT_FOUND; it never falls through. Base public visibility and data scope remain mandatory. Dictionary override options must refer to currently visible dictionary items. Override publication requires create/update plus publish (or disable as applicable), expectedVersion for replacement and an audit event without values. Overrides are read fresh, so they cannot reuse another subject's cached value.

## Boundaries

Summer Sample's parameter/dictionary application remains pinned to ent-acme. Explicit foreign/inactive membership is rejected; these changes do not introduce arbitrary enterprise provisioning or global cross-tenant defaults. Organization IDs must exist in the sample tenant. Existing no-membership legacy scoped JWTs retain the sample-tenant convention.

Binding and override APIs currently replace one binding/override; deletion and independent override revision rollback are outside this implementation. Existing base parameter/dictionary history, publish, disable and rollback remain separately authorized. The new API inputs use typed request models and explicit domain validation; public parameter keys reuse the Summer MVC Parameter contract. T32 fallback failure tests now verify current authorization and resource state in a separate transaction (see the T32/T35 acceptance guide). Full browser login and real multi-node authorization delivery remain separate acceptance work. The fixed ent-acme scope does not satisfy arbitrary-tenant configuration/preview from #126/#127; the acceptance guide records this product gap. Independent override rollback is not an implemented capability, and must not be inferred from base configuration rollback.
