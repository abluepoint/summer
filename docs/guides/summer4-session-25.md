# T25 shared login sessions

The sample application keeps the browser's servlet-session cookie and projects its authenticated context into Redis when `summer.sample.session-index.enabled` and `summer.security.storage.session=redis` are enabled. Redis records use a SHA-256 session reference, a principal index, an expiry, the client identifier, and the serialized claims/authorities needed to rebuild the security context. Raw servlet session IDs and bearer tokens are never returned by the session APIs or stored in the index.

`GET /api/v1/sessions` lists the caller's sessions when `platform.capabilities.read` is present. Administrators can pass `principal` to inspect another principal. `DELETE /api/v1/sessions/{sessionRef}` requires `platform.capabilities.write` (or `ROLE_ADMIN`) and marks the server session `REVOKED`; the response explicitly reports `jwtStatus: UNCHANGED`, because an already issued JWT remains valid until its own expiry. `DELETE /api/v1/session` is idempotent and revokes the current server session before invalidating the servlet session.

The Redis client is bounded by timeout, retry, and circuit-open properties. Management operations fail with `503 SUMMER_SESSION_STORE_UNAVAILABLE` and preserve `X-Request-Id` when the index cannot be reached. Session records expire with the servlet session timeout, and stale principal-index members are removed while listing.
