# Execution Rules

Repository: `sinicablekaveh-create/Laleh`

Execution range: **TASK-001 → TASK-055**

This is an execution specification, not proof of completion. Codex must inspect and modify the real repository, run tests/builds, and record actual commit/PR evidence before marking work complete.

### Global invariants
- Preserve `TelegramClientManager`.
- Preserve the existing single TDLib client/session and authentication lifecycle.
- Preserve existing features, database/cache compatibility, and backward compatibility.
- Do not replace TDLib or rewrite the application.
- Do not access Telegram application's private/internal database.
- Never claim tests/builds passed unless they actually ran and passed.
- Never sync/log private messages, credentials, auth codes, encryption keys, session files, or private contact data.


## Completion gate
A task is complete only when required real changes exist, relevant tests/builds ran successfully (or a verified external blocker is documented), the diff was reviewed, an atomic commit exists, and docs reflect reality.

On failure: capture exact error → diagnose → fix → rerun. Never bypass a failing gate by changing status text.

Use scoped codex/* or feature/* branches and accurate Conventional Commit prefixes (feat/fix/perf/test/docs/refactor/ci/chore).
