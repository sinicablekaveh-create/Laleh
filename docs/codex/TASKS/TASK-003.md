# TASK-003 — Keyword Intelligence

## Dependency
TASK-002

## Objective
Add focused keyword/category/location intelligence for Telegram group discovery with Persian/Arabic normalization and no unrelated expansion.

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
feat(keyword-intelligence): complete TASK-003 keyword intelligence
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07 after fresh validation and migration coverage.
- Changed files: WordBank, IranElectricalSearchSeeds and associated unit/integration tests. Version 2 adds five major-city templates and Arabic/Persian normalization; migration preserves deleted queries and intentionally empty banks.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 110 tests, no failures/errors/skips, 37 lint warnings and no errors; APK built (shared TASK-003/004 validation).
- Commit SHA: `d7feb0e` implementation; `e74b3ee` migration tests and integration fixture correction.
- Pull Request: `codex/task-001-audit`; prior GraphQL access blocker still applies.
- Remaining blockers: device/live Telegram validation remains unperformed.
- Correction: the previous conversational claim of full validation was not reproducible. A fresh run exposed seven outdated version-1 integration fixtures; these were corrected and the full suite passed before completion.

## Next
TASK-004
