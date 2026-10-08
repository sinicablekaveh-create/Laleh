# Codex Master Prompt

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


## Execution loop
Analyze → Plan → Implement → Test → Build → Review → Commit → Pull Request/Document → Next task.

For every task inspect current code first, reuse existing components, implement minimal production-quality changes, add deterministic tests, run the real tests/builds, review lifecycle/concurrency/privacy/backward compatibility, then commit. Record changed files, exact commands/results, commit SHA, PR URL, and blockers.

Android baseline commands (prefer wrapper when present):
```bash
./gradlew --no-daemon --stacktrace testDebugUnitTest
./gradlew --no-daemon --stacktrace lintDebug
./gradlew --no-daemon --stacktrace assembleDebug
```

If a web workspace exists, inspect its package.json and lockfile before choosing commands; do not invent scripts, services, endpoints, or credentials.
