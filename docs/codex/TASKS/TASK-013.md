# TASK-013 — Discovery Improvements

## Dependency
TASK-012

## Objective
Improve discovery orchestration and related-group discovery while keeping the existing Telegram client lifecycle.

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
feat(discovery-improvements): complete TASK-013 discovery improvements
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: TelegramClientManager and TelegramPhoneSearchTest. Concurrent equivalent normalized public queries share one request for the current client; completion/error fans out once per subscriber. Finished operations are removed, another search can start, and an exception in one subscriber cannot starve others. At most 16 public operations and 32 additional subscribers per operation are retained.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 130 tests, no failures/errors/skips; lint and APK build passed. Lock ordering was reviewed to avoid nesting the operation lock beneath the public-search map lock.
- Commit SHA: `af6ab47` (`perf(discovery-improvements): coalesce concurrent public searches`).
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: related discovery uses TASK-012 user-selected curated alternatives; no automatic traversal of live Telegram groups is introduced. Device/live validation unperformed.

## Next
TASK-014
