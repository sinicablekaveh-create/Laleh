# TASK-021 — Web Search Platform

## Dependency
TASK-020

## Objective
Implement the web group-search experience against the approved discovery/search interfaces.

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
feat(web-search-platform): complete TASK-021 web search platform
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Do not mark complete until real evidence exists:
- Changed files: web catalog service/local catalog, page, styling, catalog tests and web README.
- Test commands/results: `cd web && npm test` — 12 passed; `npm run typecheck` — passed. Production HTTP GET with Persian query verified lang=fa, dir=rtl, search form, query and truthful empty-catalog message.
- Build commands/results: `cd web && NEXT_TELEMETRY_DISABLED=1 npm run build` — passed (Next.js production webpack build).
- Commit SHA: `be9bd48`.
- Pull Request: Not created; earlier GitHub GraphQL authorization denial remains unresolved.
- Remaining blockers: No approved catalog rows or dedicated remote service; network delivery remains disabled. Device/live Telegram and browser visual tests not performed. No Android code changed by this task.

## Next
TASK-022
