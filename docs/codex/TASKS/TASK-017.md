# TASK-017 — Index Architecture

## Dependency
TASK-016

## Objective
Design and implement efficient local/remote-ready indexing abstractions for public discovery metadata.

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
feat(index-architecture): complete TASK-017 index architecture
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07 (public index abstraction).
- Changed files: web/lib/discovery-index.ts and deterministic tests. DiscoveryIndex separates consumers from authoritative storage/transport. MemoryDiscoveryIndex validates public records, caps rows at 10,000, indexes normalized tokens, supports staged prefixes, intersects query candidates, removes stale postings on higher revisions and returns immutable pages capped at 50 hits. Relevance/id ties are deterministic.
- Test commands/results: `npm test` — 7 tests PASS; `npm run typecheck` — PASS.
- Build commands/results: `NEXT_TELEMETRY_DISABLED=1 npm run build` — PASS.
- Commit SHA: `0bc807c` (`feat(index-architecture): add bounded public inverted index`).
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: catalog source/remote adapter and web search wiring belong to subsequent tasks. Tests use synthetic public-shaped records; no live public index exists yet.

## Next
TASK-018
