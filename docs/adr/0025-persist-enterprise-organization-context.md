# Persist enterprise, organization membership, and current context

## Status

Accepted for Summer Sample T13.

## Decision

Summer Sample treats `enterpriseId` as the tenant boundary. `EnterpriseEntity`
owns the tenant registry; organization units carry that boundary, and
`OrganizationMembershipEntity` connects an authenticated member to one or more
organization units with at most one primary organization per assignment batch.
The selected organization context is stored by authenticated subject so a
multi-organization user has an explicit, auditable current context.

The existing Spring Security principal remains the authentication identity. A
Customer is not reused as a tenant or organization member. Every organization
HTTP route receives the enterprise context in the path and checks both active
enterprise membership and the enterprise-scoped capability before reading or
mutating data.

## Consequences

Flyway migrations create durable enterprise, organization unit, membership and
context tables. The sample keeps its existing `ent-acme` fixture for compatible
contract tests while newly created enterprises start with an empty organization
tree. Organization mutations continue to emit request-correlated audit events;
the persisted context records the selected boundary for subsequent flows.
