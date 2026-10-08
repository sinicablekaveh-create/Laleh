# TASK-004 — TDLib Search Pipeline

## Dependency
TASK-003

## Objective
Optimize the existing TDLib public-chat search pipeline, request scheduling, callbacks, deduplication, timeout handling, and result processing.

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
feat(tdlib-search-pipeline): complete TASK-004 tdlib search pipeline
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: TelegramClientManager and TelegramPhoneSearchTest. Missing-chat recovery checks the latest TDLib cache before requesting the next chat. A GetChat response with an unexpected ID fails without adding unrelated metadata.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 110 tests, no failures/errors/skips, 37 lint warnings and no errors; APK built.
- Commit SHA: `a77f588`.
- Pull Request: `codex/task-001-audit`; prior GraphQL access blocker still applies.
- Remaining blockers: device/live Telegram validation remains unperformed.

## Next
TASK-005
