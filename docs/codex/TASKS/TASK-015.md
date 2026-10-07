# TASK-015 — Ranking Optimization

## Dependency
TASK-014

## Objective
Refine ranking weights and tie-breaking using deterministic tests and measured relevance.

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
feat(ranking-optimization): complete TASK-015 ranking optimization
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: GroupRanker, TelegramClientManager and tests. Phrase-boundary matching removes title substring false positives, punctuation is normalized, curated category/location get explicit weights, and equal scores sort by numeric group ID independent of recovery timing. Exact/title relevance still dominates and popularity remains excluded.
- Relevance evidence: deterministic synthetic tests confirm exact > full phrase > partial matches; category-specific results beat generic city matches; Arabic normalization and Turkish locale remain supported; `ابرقدرت` has zero score for `برق`; punctuation variants have equal scores. No claim of live dataset accuracy is made.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 132 tests, no failures/errors/skips; lint and APK build passed.
- Commit SHA: see `feat(ranking-optimization): score phrase boundaries and context` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: measured live relevance/user evaluation unavailable; this increment uses curated synthetic cases.

## Next
TASK-016
