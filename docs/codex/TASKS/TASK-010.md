# TASK-010 — Performance Base

## Dependency
TASK-009

## Objective
Establish measurable performance baselines and optimize obvious request, cache, threading, and rendering hot paths.

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
feat(performance-base): complete TASK-010 performance base
```
Use fix/perf/test/docs/refactor/ci when more accurate.

## Completion record
Completed 2026-10-07.
- Changed files: WordBank reuses three immutable regex Patterns; tools/LalehNormalizationBenchmark.java provides a synthetic repeatable baseline.
- Measurement: after activating the toolchain, run `java -cp "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:$ANDROID_HOME/platforms/android-35/android.jar" tools/LalehNormalizationBenchmark.java`. Three rounds of 20,000 normalizations: before 63.99/41.47/44.72 ms; after 41.85/23.09/31.04 ms; checksum identical (931680). These host observations do not establish Android-device latency or a statistical benchmark.
- Test/build command: `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` — PASS, 122 tests; lint and APK build passed. Existing normalization and migration tests cover unchanged semantics.
- Commit SHA: `b405814`.
- Pull Request: `codex/task-001-audit`; recorded GitHub GraphQL blocker applies.
- Remaining blockers: device/thread/memory profiling and browser rendering measurements remain unperformed.

## Next
TASK-011
