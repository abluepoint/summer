# Summer 4 字典版本发布与更新

## 实现

- `POST /api/dictionaries/{code}/publish` creates the next immutable published snapshot. `expectedVersion` is required (`0` for the first publish); `version` and `revision` are accepted aliases.
- `POST /api/dictionaries/{code}/disable` (also `deactivate`) removes the selected snapshot from public reads while retaining its version pointer.
- `POST /api/dictionaries/{code}/rollback` selects a previous `targetVersion` and requires the current `expectedVersion`.
- Every transition writes `summer_dictionary_version`, `summer_dictionary_outbox` and `summer_audit_event`, then invalidates the versioned tenant cache. `idempotencyKey` makes retries safe.
- `GET /public/dictionaries/{code}` returns the selected snapshot and its version. Draft rows and disabled items are excluded after a snapshot is published.

## UI

`/dictionary/form` keeps its real same-origin public API call. Refresh and route re-entry fetch the current published version, show the version number, and retain a selected value when that value still exists in the new option set.

## 验收证据

- `DictionaryVersionHttpTest`: publish, draft isolation, disable, rollback, stale version, invalid target, permission rejection, audit/outbox counts and idempotent replay; 3/3 passed.
- `DictionaryHttpTest`: existing dictionary CRUD/public filtering contract passed when run in isolation; one run was unable to write its generated XML result because another parallel Gradle run held the same output file.
- `summer-ui`: `npm test` (all checks, 19 Node tests), `npm run typecheck` and `npm run build` passed.
- Full project tests remain the parent session's responsibility because parallel worktree tests include unrelated in-progress sources.
