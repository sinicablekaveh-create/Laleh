# TASK-024 — API Layer

## Dependency
TASK-023

## Objective
Define stable API contracts/interfaces for search, groups, related results, categories, and health/status without inventing unavailable backends.

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
feat(api-layer): complete TASK-024 api layer
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Evidence recorded after validation of implementation commit `68b331c270d619752ac3a17e9f7abc3c84b19b85`:
- Changed files: `web/lib/api-contracts.ts`; read-only Next.js routes for search, group, related, categories, health and status; `web/tests/api-contracts.test.ts`; `web/README.md`; scoped CI branch trigger. Android/TDLib source was not modified.
- Test commands/results: GitHub Actions run `37769162382` — web `npm test` PASS (23 passed, 0 failed/skipped), `npm run typecheck` PASS; Android `gradle --no-daemon --stacktrace testDebugUnitTest` PASS.
- Build commands/results: `NEXT_TELEMETRY_DISABLED=1 npm run build` PASS and emitted all six API routes; `gradle --no-daemon --stacktrace lintDebug` PASS; `gradle --no-daemon --stacktrace assembleDebug` PASS; APK upload PASS.
- Commit SHA: `68b331c270d619752ac3a17e9f7abc3c84b19b85`.
- Pull Request: Draft PR #35 — https://github.com/sinicablekaveh-create/Laleh/pull/35
- APK artifact: `telegram-electric-debug`, artifact `11545873961`, run `37769162382`.
- Remaining blockers: the approved public catalog is intentionally empty; no backend or remote synchronization endpoint exists or was invented. Browser visual, on-device TDLib/authentication and live Telegram-server validation remain unperformed. Existing stacked PR integration conflicts remain separate work.

## Next
TASK-025
