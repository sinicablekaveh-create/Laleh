# TASK-009 — Sync Foundation

## Dependency
TASK-008

## Objective
Define safe discovery-metadata synchronization interfaces and local queue behavior without syncing private Telegram content or session secrets.

## Mandatory execution
1. Inspect the current repository implementation relevant to this task.
2. Reuse/extend existing code; do not create duplicate Telegram clients, sessions, lifecycle managers, or competing storage systems.
3. Implement the smallest coherent production-quality change set satisfying the objective.
4. Add/update deterministic tests for changed behavior.
5. Run relevant tests and builds.
6. Review concurrency, lifecycle, errors, privacy, compatibility, and resource bounds.
7. Commit only validated work and record evidence.

## Validation gate
- Relevant tests PASS with exact command/results recorded.
- Relevant build PASS with exact command/results recorded.
- No regression to Telegram auth/single-client lifecycle.
- No sensitive/session/private data added to discovery sync/logging.
- Changed files and behavioral impact documented.

## Suggested commit
```text
feat(sync-foundation): complete TASK-009 sync foundation
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07 (local synchronization foundation).
- Changed files: DiscoveryMetadata, DiscoverySyncQueue, DiscoverySyncQueueTest. The outbox shares existing `telegram_discovery` preferences, defaults disabled, limits pending public records to 100, deduplicates by group/revision, preserves newer updates against stale acknowledgments, and clears work on opt-out. Only a negative group ID, bounded public title, valid public username, category/location, revision and schema version are serialized.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 122 tests, no failures/errors/skips; lint and APK build passed.
- Commit SHA: see `feat(sync-foundation): add opt-in bounded public outbox` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: no remote transport or endpoint configured. User-facing opt-in wiring and background delivery belong to subsequent integration/sync tasks; this step performs no network synchronization.

## Next
TASK-010
