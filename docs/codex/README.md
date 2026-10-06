# Laleh Codex Execution Package

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


## Contents
- CODEX_MASTER_PROMPT.md
- CODEX_TASK_CHAIN.md
- EXECUTION_RULES.md
- ARCHITECTURE_RULES.md
- TEST_PLAN.md
- BUILD_PLAN.md
- TASKS/TASK-001.md through TASKS/TASK-055.md
