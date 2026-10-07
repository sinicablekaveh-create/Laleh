# TASK-014 — Search Analytics

## Dependency
TASK-013

## Objective
Add privacy-conscious aggregate search metrics for latency, cache use, result counts, and failures; never log sensitive content or credentials.

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
feat(search-analytics): complete TASK-014 search analytics
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: SearchMetrics, TelegramClientManager and tests. Lifetime-local synchronized numeric snapshots report request/completion/failure/timeout counts, cache hits, coalesced requests, discovery result counts and total/max response latency. No query, group identifier/title, contact, credential or session data is retained in metrics.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 131 tests, no failures/errors/skips; lint and APK build passed. Integration checks one physical request and one completion despite two subscribers and duplicated callback.
- Commit SHA: see `feat(search-analytics): add content-free local metrics` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: metrics are local to the manager lifetime, not persisted or sent to a monitoring service; protocol-level success counts do not assert semantic validity of every response.

## Next
TASK-015
