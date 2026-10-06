# Test Plan

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


Cover query normalization, suggestions/deduplication, group-vs-channel filtering, deterministic ranking, request deduplication/timeouts/cancellation/stale generations, bounded cache TTL/invalidation, sync queue/validation/version behavior, Android UI states where practical, and regression-sensitive TDLib lifecycle/auth boundaries. Avoid live Telegram dependencies in unit tests; use deterministic fakes/adapters.
