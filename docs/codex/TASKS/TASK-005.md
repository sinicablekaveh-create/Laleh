# TASK-005 — Group Filtering

## Dependency
TASK-004

## Objective
Ensure only valid Telegram basic groups and non-channel supergroups are retained, with deterministic duplicate removal.

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
feat(group-filtering): complete TASK-005 group filtering
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: TelegramClientManager and TelegramPhoneSearchTest. Group filtering checks the pinned TDLib type directly and rejects ID zero. Regression covers basic groups, non-channel supergroups, channels, private/secret chats, unknown types and duplicated IDs in response order.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 111 tests, no failures/errors/skips; lint and APK build passed.
- Commit SHA: see `fix(group-filtering): reject invalid group identities` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: device/live Telegram validation unperformed.

## Next
TASK-006
