# TASK-006 — Ranking Engine

## Dependency
TASK-005

## Objective
Implement deterministic relevance scoring that prioritizes exact/title/keyword/category/location relevance over popularity.

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
feat(ranking-engine): complete TASK-006 ranking engine
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: existing GroupRanker, WordBank normalization API, TelegramClientManager callback ranking, GroupRankerTest and TelegramPhoneSearchTest. Exact/title/username/query-token relevance determines order. Topic/city tokens are scored from the query. Equal scores preserve discovery order; member count is excluded.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 115 tests; no failures/errors/skips; lint and APK build passed.
- Commit SHA: see `feat(ranking-engine): rank discovery by normalized relevance` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: ranking weights are deterministic heuristics, not tuned on a live relevance dataset. Device/live Telegram validation unperformed.

## Next
TASK-007
