# TASK-019 — Sync Pipeline

## Dependency
TASK-018

## Objective
Implement queued, validated, retry-safe synchronization for approved public discovery metadata.

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
feat(sync-pipeline): complete TASK-019 sync pipeline
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Local pipeline implemented/validated 2026-10-07; real remote synchronization remains blocked.
- Changed files: DiscoverySyncQueue, DiscoverySyncRunner and tests. Injected transport delivers one pending record at a time with a 30-second deadline and exactly-once completion. Retry attempts/backoff persist in existing preferences; permanent rejection pauses the revision, newer revisions reset retry state, and stale acknowledgments cannot delete new work. Accepted metadata updates the bounded public cache.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 138 tests, no failures/errors/skips; lint and APK build passed. Tests use controlled transport, timeout and clock; no live synchronization was executed.
- Commit SHA: `11ef62b` (`feat(sync-pipeline): add retry-safe public delivery adapter`).
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: no approved remote endpoint, authentication policy or public catalog source is configured in the repository/environment. The transport contract is concrete and testable; an external adapter must be configured before real delivery can be claimed.

## Next
TASK-020
