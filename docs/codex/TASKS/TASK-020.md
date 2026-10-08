# TASK-020 — Data Validation

## Dependency
TASK-019

## Objective
Validate group identifiers, public metadata, categories, scores, versions, and duplicate records before persistence/sync.

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
feat(data-validation): complete TASK-020 data validation
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Local validation increment completed 2026-10-07; live TASK-019 dependency is still blocked.
- Changed files: Android metadata/outbox/cache and web metadata/index with regression tests. Unknown/private fields, fractional/overflowing IDs, invalid schemas/usernames and control/spoofing characters in public titles are rejected. Public cache rejects conflicting same-revision content. Web index rejects duplicate username ownership across group IDs and releases old aliases on a higher revision. Outbox JSON reads are size-bounded, and permanent-rejection revisions remain paused.
- Android test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 139 tests, no failures/errors/skips; lint passed with 35 warnings, APK built.
- Web test/build commands: `npm test` — 10 tests PASS; `npm run typecheck` and `NEXT_TELEMETRY_DISABLED=1 npm run build` — PASS. `npm audit --audit-level=high` reported 0 vulnerabilities at the time of this run.
- Commit SHA: `cd98a16`.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: real sync/data-source configuration from TASK-019 is missing. This independent validation increment does not claim end-to-end delivery, browser/device validation or completion of TASK-021 through TASK-055.

## Next
TASK-021
