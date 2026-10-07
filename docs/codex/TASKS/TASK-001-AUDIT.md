# TASK-001 — Repository Audit Record

Audit date: 2026-10-07 (Asia/Tehran)  
Repository snapshot: `22c9a0881401ec421363cff9163bf92a2406bc8f`

## Scope and outcome

This audit examined the Android application architecture, Gradle configuration, TDLib lifecycle, search pipeline, local storage, background service, tests, and CI before feature changes in TASK-002 and later work. No product behavior changed in TASK-001.

## Architecture evidence

- `MainActivity` obtains the existing `BackgroundRuntime` snapshot when present; otherwise it constructs one `WordBank` and one `TelegramClientManager` for the activity.
- `TelegramClientManager` is the only production class that calls `Client.create`. Its `start` method uses a serialized executor and closes its prior TDLib client before creating a replacement.
- `CentralCore` owns message scheduling and the resumable word-search loop. `SmartSearchQueue` generates and persists the shortened query stages, while `WordBank` normalizes and persists the local electrical vocabulary.
- Public group discovery, phone lookup, contacts, proxy setup, target-group refresh, and TDLib authorization updates are handled by `TelegramClientManager`; the UI and `CentralCorePanel` consume that manager instead of creating competing clients.
- `BackgroundRuntime` shares the existing manager, word bank, and core with `BackgroundCoreService`. If a process starts the service without an in-memory snapshot, the service constructs the same set of components as a recovery path. Future changes must preserve the single active TDLib session across this handoff.

## Storage and privacy evidence

- `electrical_word_bank`, `smart_search_queue`, `central_core`, `telegram_discovery`, and `telegram_auto_login` are private application preferences.
- TDLib data is kept under the application-private `filesDir/tdlib` path. The app uses no Telegram private or internal application database.
- Phone numbers, contact data, API credentials, session data, authentication codes, proxy credentials, exports, and logs were not added to repository sources, tests, or audit evidence.
- `AndroidManifest.xml` sets `allowBackup=false`.

## Build and CI evidence

- Gradle wrapper: absent. The validated environment uses Temurin JDK 17, Gradle 8.9, Android SDK Platform 35, and Build Tools 35.0.0.
- `app/build.gradle.kts` sets `compileSdk` and `targetSdk` 35, `minSdk` 26, version `1.13.0`/code `15`, and `arm64-v8a` packaging.
- `.github/workflows/build-apk.yml` validates `testDebugUnitTest`, `lintDebug`, and `assembleDebug` for relevant pushes and pull requests to `main`.

## Fresh validation

Command run from the repository root:

```bash
source /workspace/toolchains/activate.sh
gradle --no-daemon --max-workers=4 --rerun-tasks -x generateAppLogo \
  testDebugUnitTest lintDebug assembleDebug
```

Results:

- Build succeeded in 23 seconds; 49 tasks executed.
- Unit tests: 103 test cases, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors and 37 warnings.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

`generateAppLogo` was excluded because it rewrites a tracked image with different bytes. The older tracked APK was backed up outside the checkout and restored after validation.

## Follow-up constraints

- TASK-002 and later work must extend the existing `TelegramClientManager`, `CentralCore`, `SmartSearchQueue`, and `WordBank` rather than duplicating lifecycle or storage systems.
- Changes around `BackgroundRuntime` and `BackgroundCoreService` need explicit lifecycle tests because they coordinate activity and foreground-service ownership of the TDLib session.
- Device installation, real Telegram authentication, and production-server behavior remain outside this deterministic local audit.
