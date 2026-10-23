# Music Player (Android)

Local-library music player: Kotlin, Jetpack Compose, Material 3 Expressive, Media3, Room, Koin.
Package `com.mhrsdev.musicplayer`, minSdk 26, compile/target SDK 36. Project protocol: see `CLAUDE.md` and `docs/`.

## Prerequisites
- JDK 17+ (built and tested with OpenJDK 21; bytecode target is 17).
- Android SDK with `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`.
- Gradle is provided by the wrapper (`./gradlew`, Gradle 8.14.3).

### Installing the SDK (Linux, command line only)
```bash
mkdir -p /opt/android-sdk/cmdline-tools && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"
export ANDROID_HOME=/opt/android-sdk          # or put sdk.dir=/opt/android-sdk in local.properties (gitignored)
```
Behind the Claude Code agent proxy this works unchanged (`dl.google.com`, `maven.google.com`, Maven Central and
`services.gradle.org` are reachable; the proxy CA is already in the JVM trust store).

## Build / verify
```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew lint                   # app/build/reports/lint-results-debug.html
./gradlew test                   # JVM unit tests (domain + fakes)
./gradlew assembleDebug lint test   # what CI runs
```
Install on a device/emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Layout
See `docs/ARCHITECTURE.md`. In short: `domain/` (pure models + interfaces), `fake/` (in-memory implementations with
sample data, bound in `di/AppModule.kt`), `data/` + `playback/` (real implementations, later tasks), `ui/` (Compose).

## Version notes
Material 3 Expressive APIs (`MaterialExpressiveTheme`, `MaterialShapes`, `LinearWavyProgressIndicator`,
`LoadingIndicator`, floating toolbars) are not in stable `material3` 1.4.0; the project pins `material3`
`1.5.0-beta02` on top of Compose BOM `2026.09.00` (see `gradle/libs.versions.toml`).
