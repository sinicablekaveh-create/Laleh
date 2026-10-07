# TASK-016 — Data Models

## Dependency
TASK-015

## Objective
Define stable data models for discovery/search/sync with backward-compatible serialization boundaries.

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
feat(data-models): complete TASK-016 data models
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: DiscoveryMetadata, Android model tests, shared/discovery.schema.json and web metadata parser/tests. Schema v2 uses decimal strings for signed-64-bit group IDs and revisions, preserving JavaScript precision. Android still reads v1; web migrates v1 only for safely representable integer identities. The web parser rejects unknown fields/schema/ranges and returns immutable records.
- Android test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 133 tests; lint and APK build passed.
- Web test/build commands: `npm test` — 4 tests PASS; `npm run typecheck` and `NEXT_TELEMETRY_DISABLED=1 npm run build` — PASS.
- Commit SHA: see `feat(data-models): preserve long identities across Android and web` in branch history.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: no real public catalog or remote service configured. JavaScript v1 numbers outside the safe-integer range are rejected rather than silently approximated.

## Next
TASK-017
