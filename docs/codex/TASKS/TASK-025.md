# TASK-025 — Web Optimization

## Dependency
TASK-024

## Objective
Optimize web rendering, caching, bundle behavior, loading states, and navigation using measured results.

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
feat(web-optimization): complete TASK-025 web optimization
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Evidence recorded after exact implementation commit `838d9a4568a58acbc3d34ba2c2a93760d0c5791f` passed GitHub Actions run `37772949785`.

- Changed files: public API response caching/ETag handling and route policies; bounded list prefetch helper; group/category navigation; accessible `app/loading.tsx`; reduced-motion loading CSS; build-metrics script; package script; deterministic optimization tests; scoped Next.js CI cache. Android/TDLib source was not modified.
- Test commands/results: web `npm test` PASS — 26 passed / 0 failed; `npm run typecheck` PASS. Android `gradle --no-daemon --stacktrace testDebugUnitTest` PASS.
- Build commands/results: `NEXT_TELEMETRY_DISABLED=1 npm run build` PASS; compile 3.6s vs prior TASK-024 baseline 4.9s; static generation 183ms vs prior 217ms. `npm run measure:build` reported 27 JS chunks, 932301 raw bytes, 288559 gzip bytes, largest chunk 242379 bytes. Android `lintDebug` PASS and `assembleDebug` PASS.
- Cache/privacy behavior: successful approved public group/category metadata responses use bounded shared cache headers plus SHA-256 ETag/304; search terms, operational health, and error responses remain `no-store`.
- CI cache: first TASK-025 run had an expected Next.js cache miss and saved the cache for subsequent runs.
- Commit SHA: `838d9a4568a58acbc3d34ba2c2a93760d0c5791f`.
- Pull Request: Draft PR #36 — https://github.com/sinicablekaveh-create/Laleh/pull/36
- APK artifact: `telegram-electric-debug`, artifact `11548880142`, run `37772949785`.
- Remaining blockers: approved public catalog remains intentionally empty and no remote synchronization backend exists. Browser visual, on-device TDLib/authentication, and live Telegram-server validation remain outside this web-only slice.

## Next
TASK-026
