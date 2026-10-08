# TASK-008 — Web Foundation

## Dependency
TASK-007

## Objective
Create or stabilize the Laleh web foundation with Next.js/React/TypeScript while keeping it separate from Telegram authentication/session internals.

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
feat(web-foundation): complete TASK-008 web foundation
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: `web/` Next.js 16.4.0 / React 19.3.0 / TypeScript 7.0.2 workspace, npm lockfile, Persian RTL responsive page, bounded normalized GET search, empty approved catalog state, build/test instructions; `.gitignore` excludes generated assets and environment files.
- Test commands/results: `npm test` — 2 tests PASS; `npm run typecheck` — PASS. Production HTTP smoke with curl and assertions — PASS for Persian RTL, normalized query, search form and empty catalog.
- Build commands/results: `NEXT_TELEMETRY_DISABLED=1 npm run build` — PASS; output in `web/.next/`, `/` rendered dynamically and not-found statically.
- Commit SHA: `86c3834`.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: catalog starts empty; no remote metadata service or deployment configured. Browser visual/accessibility checks remain unperformed. No Telegram authentication/session integration was introduced.

## Next
TASK-009
