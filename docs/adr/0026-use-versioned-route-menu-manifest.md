# ADR 0026: Use a versioned route menu manifest for server-side visibility

## Status

Accepted for Summer UI and Summer Sample.

## Decision

Summer UI owns the route and component mapping. It publishes a versioned menu manifest containing only menu identity, parent identity, path, title and icon translation keys, ordering, and a Capability Code. The server stores this metadata and enterprise overrides, but never stores or evaluates a frontend component import or display expression.

The platform endpoint accepts an idempotent manifest sync. Enterprise administrators can override enabled state, parent, ordering, and Capability binding with optimistic version checks. The visible-manifest endpoint verifies active enterprise membership and evaluates each item's Capability Code for the authenticated principal. Its result includes action names so the UI can hide menus, routes, and buttons consistently. API authorization remains authoritative when a hidden control is invoked directly.

## Consequences

- Route declarations remain the single source for local component resolution.
- Menu metadata can be persisted and changed without coupling the server to React or Vite internals.
- Manifest changes require an increased manifest version; retries with the same content are safe.
- Capability filtering is tenant-bound and preserves explicit user denies from Summer Security.
