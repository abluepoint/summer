# Use versioned Caffeine L1 and Redis L2 for parameters and dictionaries

## Decision

Summer Sample parameter and public dictionary reads use a tenant-scoped Caffeine L1 plus Redis L2 cache. Redis version keys are part of the effective cache key. A successful parameter outbox delivery or dictionary mutation increments the resource version, so old entries cannot be read after invalidation.

Each application node consumes parameter invalidation events with its own Redis Stream consumer group. Shared receipt idempotency does not replace node-local eviction. A node that reconnects reads the current version before using either tier.

Cache misses are protected by a per-entry node-local lock. If Redis cannot validate a version, reads bypass both tiers and load from MySQL; the failure is counted and logged without driver details or cached values.

## Consequences

The cache is eventually consistent across nodes and bounded by the configured TTL and event delivery delay. Every cache key includes the tenant boundary. Summer UI remains unchanged because the cache is behind the existing HTTP contracts.
