# Build Plan

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


## Android
```bash
./gradlew --no-daemon --stacktrace testDebugUnitTest
./gradlew --no-daemon --stacktrace assembleDebug
```
If wrapper execution is unavailable, document why before using installed Gradle.

## Web
Only when a web workspace actually exists: inspect package.json/lockfile, use the matching package manager, and run defined test/lint/build scripts. Record actual artifact paths; never invent them.
