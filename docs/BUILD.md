# Building Laleh+T

## Requirements

- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle Wrapper 8.9 (included)

The current TDLib dependency packages native binaries, so this workspace does not require the Android NDK for its debug build.

Validate the environment with:

```bash
./scripts/bootstrap-android.sh
```

Run the required checks from the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
