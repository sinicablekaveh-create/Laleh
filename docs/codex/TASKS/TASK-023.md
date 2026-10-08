# TASK-023 — User Search Experience

## Dependency
TASK-022

## Objective
Improve shared UX patterns such as recent searches, filters, sorting, accessibility, and mobile behavior.

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
feat(user-search-experience): complete TASK-023 user search experience
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Do not mark complete until real evidence exists:
- Changed files: web catalog/index search options, URL parsing/link preservation, category filtering, search form, accessible controls/mobile styles, deterministic regression tests and web CI job. See Git diff for exact paths.
- Test commands/results: `cd web && npm test` — 18 passed, no failures/skips; two new behavioral checks failed before implementation. Production HTTP smoke verified Persian RTL, labels, selected filters/sort, skip link, page links, empty catalog, categories 200 and missing-group 404.
- Build commands/results: `npm run typecheck` and `NEXT_TELEMETRY_DISABLED=1 npm run build` passed. Java 17 / Gradle 8.9 / SDK 35: `gradle --no-daemon --stacktrace --max-workers=4 testDebugUnitTest`, `lintDebug`, `assembleDebug` passed. The initial unit execution ran all 139 tests; the subsequent standard invocation reused valid outputs. Lint: 0 errors, 36 warnings. APK: `app/build/outputs/apk/debug/app-debug.apk`, 24,193,153 bytes; manifest/dex/arm64 TDLib inspected.
- Commit SHA: implementation commit recorded in Git history; publication evidence is appended after commit/CI.
- Pull Request: draft publication follows local validation; no merge authorized.
- Remaining blockers: approved catalog remains empty; no remote sync endpoint, browser visual, on-device or live-Telegram validation. This slice provides filters, sorting, accessibility and mobile controls; persistent recent-search history is not introduced. TASK-024–055 remain pending.

## Next
TASK-024
