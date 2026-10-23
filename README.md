# Music Player (Android)

Local-library music player: **Java only**, XML + custom Views, MVVM (ViewModel + LiveData), framework SQLite
(no Room), Media3. Package `com.mhrsdev.musicplayer`, minSdk 26, compile/target SDK 37.
Project protocol: see `CLAUDE.md` and `docs/`.

## Toolchain
| Piece | Version |
|---|---|
| Gradle (wrapper) | **9.0.0** |
| Android Gradle Plugin | **8.13.2** (the newest AGP that runs on Gradle 9.0.0; AGP 9.x needs Gradle >= 9.1.0) |
| JDK | 17+ (built with OpenJDK 21; bytecode/source level 17) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

AGP 8.13 officially tests up to compileSdk 36, so `gradle.properties` has `android.suppressUnsupportedCompileSdk=37.0`
and `app/build.gradle` uses `compileSdkVersion 'android-37.0'`. Versions are pinned in `gradle/libs.versions.toml`;
AndroidX libraries that require AGP >= 9.1 (e.g. `androidx.core` 1.18+) are held back until the project can move
to Gradle >= 9.1.

## Prerequisites: Android SDK (Linux, command line only)
```bash
mkdir -p /opt/android-sdk/cmdline-tools && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platform-tools" "platforms;android-37.0" "build-tools;35.0.0"
export ANDROID_HOME=/opt/android-sdk          # or put sdk.dir=/opt/android-sdk in local.properties (gitignored)
```
Behind the Claude Code agent proxy this works unchanged (`dl.google.com`, Maven Central and `services.gradle.org`
are reachable; the proxy CA is already in the JVM trust store).

## Build / verify
```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug test lint   # what CI runs
./gradlew :app:assembleRelease           # R8 + resource shrinking (unsigned APK)
```
Outputs: `app/build/outputs/apk/debug/app-debug.apk`, lint report `app/build/reports/lint-results-debug.html`.
Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Rules (owner)
No Kotlin (`git ls-files | grep -E '\.kts?$'` must print nothing), MVVM, no Room, tidy SQL in query classes,
clean packages. Details and the final contracts: `docs/ARCHITECTURE.md`.
