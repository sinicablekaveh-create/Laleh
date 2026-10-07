# TASK-002 — Smart Group Search Core

## Dependency
TASK-001

## Objective
Implement the smart public-group search foundation incrementally around the existing search path without replacing TelegramClientManager or TDLib.

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
feat(smart-group-search-core): complete TASK-002 smart group search core
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed on 2026-10-07.

- Changed files: `TelegramClientManager` now retains discovery IDs in insertion order while
  rejecting a repeated group ID before it can be re-captured or re-counted; the focused
  discovery test verifies the callback receives one ID and one new item.
- Test commands/results: `gradle --no-daemon --max-workers=4 testDebugUnitTest --tests com.sinicable.telegramelectric.TelegramPhoneSearchTest` — PASS (20 tests).
- Build commands/results: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS (104 unit tests, 0 failures/errors/skips; lint completed with 38 existing warnings and no errors; debug APK assembled).
- Commit SHA: `400eaad4ea31431d1ec69f37d2316c5ff63adde0` (`feat(smart-group-search-core): deduplicate discovery output`).
- Pull Request: branch is pushed after the documentation commit; automatic PR creation remains blocked by the GitHub GraphQL `Forbidden` response recorded in TASK-001.
- Remaining blockers: none for this task. No Telegram client/session/storage was added, and no private discovery data is logged.

## Next
TASK-003
