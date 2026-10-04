# Use the authenticated session boundary for Redis diagnostics

## Decision

The Redis runtime diagnostic endpoint is exposed at
`/internal/v1/diagnostics/redis`. It requires the existing
`platform.capabilities.read` authority and returns the same request-correlated
API envelope used by the backend. It is a read-only operational endpoint; the
server remains the authority for access decisions.

Summer UI calls the endpoint with a same-origin relative URL. Vite development
proxy and the production Caddy route forward `/internal` to the Spring service,
so the browser sends the authenticated session cookie without exposing the
internal backend listener as a separate browser origin. The route is therefore
outside the public Customer OAuth scope list; adding a broad Customer scope
would grant an unrelated business permission.

The response contains only a fixed status, safe reason and message, latency,
timestamp, and an allowlist of numeric Redis runtime metrics. Redis addresses,
credentials, connection strings, and raw driver exception text never cross the
API boundary.

## Consequences

The UI hides the menu item unless its local permission model grants the
diagnostic read permission, while the backend check remains mandatory. A
deployment that uses a different authenticated session must map that session to
`platform.capabilities.read` before enabling the page.
