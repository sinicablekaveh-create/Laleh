# TASK-012 — Smart Suggestions

## Dependency
TASK-011

## Objective
Generate bounded, deduplicated, discovery-focused search suggestions from query context and curated keyword data.

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
feat(smart-suggestions): complete TASK-012 smart suggestions
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: existing GroupKeywordBank/SmartKeywordQueue, WordBank, MainActivity and DiscoverySuggestionsTest. Curated related topics preserve query city, normalize/deduplicate, and cap expansion at eight. Bank UI offers explicit add buttons; suggestions never mutate the bank automatically, and existing terms are excluded.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 128 tests; no failures/errors/skips; lint and APK build passed.
- Commit SHA: see `feat(smart-suggestions): offer bounded city-aware alternatives` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: curated suggestions cover electrical/cable/solar topics only; visual device testing unperformed.

## Next
TASK-013
