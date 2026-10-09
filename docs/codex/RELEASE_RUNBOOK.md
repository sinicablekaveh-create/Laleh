# Local discovery release runbook

The current release candidate preserves application ID `com.sinicable.telegramelectric`, schema-v2 public metadata, and the existing TDLib client/session. This document is build preparation, not a production-release claim.

## Build and verify

Requires full JDK 17 (including jlink), Gradle 8.9, Android SDK platform 35, build tools 34/35, and Node 24/npm. No Gradle wrapper exists: use an installed, verified Gradle distribution. Respect the environment's proxy and CA trust.

```sh
gradle --no-daemon --max-workers=4 testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug assembleRelease
cd web
npm ci
mkdir -p reports
npm run test:ci
npm run typecheck
NEXT_TELEMETRY_DISABLED=1 npm run build
npm run test:smoke
npm run benchmark:discovery
npm run measure:build
npm audit --audit-level=high
```

Local checkout currently tracks an older APK and a logo. Back up the tracked APK outside the checkout before packaging and restore its exact bytes afterwards; do not overwrite user changes. Use `-x generateAppLogo` only when preserving the already generated tracked logo, as done in the local validation. CI runs the normal logo task. Keep generated reports, toolchains, keystores and APKs out of source commits.

Artifacts:

- `app/build/outputs/apk/debug/app-debug.apk`: debug signed, installable for testing.
- `app/build/outputs/apk/release/app-release-unsigned.apk`: release variant, not installable before signing.
- `web/.next/`: production web build; `npm start` runs it.
- Android JUnit/report directories and web `reports/web-tests.xml`: CI test evidence.

## Release gates

1. Both Android variants' unit tests, lint, APK assembly; web tests/typecheck/build/HTTP smoke; public schema parity; dependency audit.
2. Device validation: login, 2FA, reconnect, foreground/background transitions, discovery, favorites/history controls, export, and sending only with actual chat permissions. Never attach private sessions/messages or login values to reports.
3. Browser validation: mobile RTL, keyboard navigation, theme, storage-denied behavior, history opt-out, favorites and current group availability. A local Chromium interaction check passed mobile RTL, history opt-in/out, theme, favorite removal, clearing and storage-denied feedback; device-specific keyboard/visual review remains outstanding. HTTP smoke is separate from browser interaction testing.
4. Configure the established release keystore through secure file/secret bindings. Sign using the existing application's signing identity; verify signer, application ID, ABI, version code, and installation/update before publishing. Never substitute the debug key or silently generate a replacement production identity.
5. Choose the release version and monotonically increasing versionCode before distribution. This local feature work has not changed 1.13.0/15, so it is a development candidate, not a distinct published update.
6. Approve release notes, supported ABI (arm64-v8a), Android minimum (26), known issues, and the distribution destination. There is no store/publishing workflow enabled.

## Web and sync deployment

The bundled approved catalog remains empty. `/api/health` reports `backend: disabled`; `/api/discovery` extends the v1 public response with engineVersion 3/searchVersion 6, while existing routes remain compatible. Catalog entries must be explicitly approved public records, never automatically copied from account messages, contacts or session storage.

A real service requires an approved public-metadata endpoint, authentication/authorization method, source provenance, revision/conflict contract, request cancellation, removal/retention policy and operation monitoring. The supplied Telegram documentation URL is not this endpoint. Implement production transports through `CancellableTransport`; legacy `Transport` is retained for local/mock compatibility and cannot guarantee physical network cancellation. Close the runner when its owning lifecycle ends. Neither adapter is wired to an invented service.

Host Next.js behind HTTPS, preserve security headers and search `no-store`, disable access-log query-string capture, and set resource limits. The static GitHub Pages workflows do not deploy the dynamic Next.js API; use an appropriate Next.js runtime when a host is selected.

## Rollback and recovery

- Tag/store the tested source commit and its signed artifact outside Git before publication.
- Android store updates require a forward versionCode: do not promise an in-place downgrade. Keep the existing signing key and backup/recovery policy. Do not delete TDLib/session databases to roll back search UI changes.
- Web rollback uses the last tested application build plus its approved catalog revision. Do not migrate private preferences to public metadata.
- Sync stays disabled by default. Disable it on a service incident: queued public metadata is removed, consent generation changes, and old callbacks cannot mutate a new queue. Close/cancel any active transport as well.
- Browser preferences can be cleared locally; history is off by default. Android discovery preferences are separate from auth and existing word-search attempt history, which is unchanged.
