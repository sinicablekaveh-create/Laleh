# Laleh Complete Test Gate

Target branch: `codex/laleh-execution-chain`

This document defines the real validation gate for the Laleh Codex execution package. It is not proof of success by itself.

## Required CI commands

```bash
gradle --no-daemon --stacktrace testDebugUnitTest
gradle --no-daemon --stacktrace assembleDebug
```

## Expected artifacts

- regression-test-reports
- telegram-electric-debug

## PASS criteria

- regression tests succeed
- debug APK build succeeds
- test reports are uploaded
- APK artifact is uploaded
- no regression to the existing TDLib authentication/single-client lifecycle
- no second Telegram client/session is introduced

## Evidence record

Fill from actual GitHub Actions output only:

- Workflow run:
- Commit SHA:
- Test result:
- Build result:
- Test artifact:
- APK artifact:
- Failed jobs/logs:
- Final verdict:

## Codex / Waggle handoff

Use with `docs/codex/CODEX_MASTER_PROMPT.md` and TASK-001 → TASK-055. Waggle Installer only installs Waggle for Codex; it does not itself execute this test suite.
