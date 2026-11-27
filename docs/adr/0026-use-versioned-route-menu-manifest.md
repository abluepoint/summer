# ADR 0026: Use a versioned route menu manifest for server-side visibility

2026-11-23 更新：菜单清单和布局统一由平台维护。所属客户端引用平台字典 `menu-client` 中的稳定编码，分配时使用已发布且启用的选项。V195 移除租户专用覆盖表及租户布局记录，平台清单和平台布局仍为可见菜单提供元数据。可见菜单继续按当前用户和授权域计算。


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
