# TASK-011 — Query Understanding

## Dependency
TASK-010

## Objective
Parse normalized queries into intent, category, keywords, and optional location signals.

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
feat(query-understanding): complete TASK-011 query understanding
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: SearchQuery, TelegramClientManager and SearchQueryTest. The TDLib search path now normalizes bounded 96-codepoint queries. The parser recognizes curated electrical categories, five major cities, learning/market/discovery intent, and up to 16 unique immutable keywords. Phrase boundaries avoid false city matches.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 125 tests, no failures/errors/skips; lint and APK build passed.
- Commit SHA: `cb9e35f` (`feat(query-understanding): analyze bounded discovery queries`).
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: taxonomy is deliberately curated and finite; unknown categories/cities are left unclassified. Live TDLib search remains unperformed.

## Next
TASK-012
