# TASK-018 — Cache Layers

## Dependency
TASK-017

## Objective
Implement bounded memory/persistent cache layers with TTL, invalidation, defensive copying, and stale-entry cleanup.

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
feat(cache-layers): complete TASK-018 cache layers
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07 (cache primitives).
- Changed files: PublicDiscoveryCache and tests; web ResultCache and tests. Public Android metadata is persisted in existing discovery preferences, capped at 200 entries and 24-hour maximum configurable TTL, with revision protection, LRU eviction, expiry cleanup, clock-rollback invalidation and explicit clear/invalidate. Web result cache caps entries at 200, each page at 50 hits and key length at 512, copies ingress/egress and supports TTL/LRU/clear.
- Android test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 135 tests; lint and APK build passed.
- Web test/build commands: `npm test` — 9 tests PASS; `npm run typecheck` and `NEXT_TELEMETRY_DISABLED=1 npm run build` — PASS.
- Commit SHA: see `feat(cache-layers): add bounded public caches with TTL` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: integration with delivery/search providers belongs to following tasks; these caches do not access TDLib sessions or initiate network operations. Persistent LRU order reflects the last write, while memory order updates on reads.

## Next
TASK-019
