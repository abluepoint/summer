# ADR 0027: Publish immutable dictionary snapshots

## Decision

Summer Sample keeps dictionary editing in the existing type and item tables, and records every publish as an immutable JSON snapshot in `summer_dictionary_version`. The dictionary type stores the currently selected published version. Public reads resolve that pointer and filter disabled items from the snapshot, so draft edits cannot leak into anonymous reads.

Publish, disable and rollback require an expected published version. Mutations write a dictionary outbox row and audit event in the same transaction, then invalidate the tenant-scoped versioned cache. An idempotency key on a mutation replays the committed result without creating another version or event.

## Consequences

- Rollback selects a prior snapshot and keeps its original version number; the version history remains append-only.
- The existing legacy path where a dictionary was created as published and has no snapshot remains readable from current rows for compatibility with the T06 contract.
- Live notifications are outside this decision and remain T31 scope; UI refresh reads the public endpoint again and retains a still-valid local form value.
