# Cockpit replay

An Android dashboard for replaying a 15-second trace of simulated battery telemetry. The screen shows the playhead, charge, timestamp of the most recent sample, age of that sample, and whether the signal is fresh. Drag the timeline, play or pause, rewind, or jump directly to a checkpoint.

## Reproduce a stale reading

| Playhead | Battery | Last sample | Status |
| --- | --- | --- | --- |
| 0 s | 80% | 0 s | Fresh |
| 5 s | 80% | 0 s | Fresh |
| 7 s | 80% | 0 s | Stale |
| 10 s | 77% | 10 s | Fresh |

Tap the 7 s checkpoint, then the 10 s checkpoint. Tap **Rewind** and **Play** to reproduce the same transition. Staleness starts when the sample age exceeds 5,000 ms. Seeking directly to a time uses the same lookup as continuous playback.

## Import a battery trace

Save this example as a plain ASCII CSV (UTF-8 without a byte-order mark also works for these characters). Open the trace through the dashboard's file picker.

```csv
time_ms,battery_percent
0,80
3000,79
12000,77
```

Start at 0 milliseconds. Each subsequent timestamp must increase, and each battery value must be a whole number from 0 through 100. Files can contain up to 10,000 samples and 256 KiB. For the example above, the 3 s sample goes stale just after 8 s and the 12 s sample restores freshness. Imported traces run for 5,001 ms after the last sample so you can inspect its first stale moment.

## Build and run

Use a JDK 17 or 21 installation and the Android SDK with platform 35. Point Android Studio or the `ANDROID_HOME` environment variable at the SDK. The Gradle wrapper downloads the pinned build tools on first use. Open the repository in Android Studio and run the `app` configuration on an Android 8.0 (API 26) or newer emulator or device.

From PowerShell in the repository root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
adb shell am start -n com.krishang.cockpit/.MainActivity
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The install and launch commands require a connected device or running emulator, with Android platform tools on your PATH. On macOS or Linux use `./gradlew` in place of `.\gradlew.bat`.

## Run the core regression checks

Run `.\gradlew.bat :app:testDebugUnitTest` (macOS or Linux: `./gradlew :app:testDebugUnitTest`) to execute all four suites with JUnit.

The trace, CSV parser, and playback clock are plain Java. With `JAVA_HOME` pointing to a JDK, run the same checks directly from PowerShell:

```powershell
$classes = 'build/replay-checks'
New-Item -ItemType Directory -Force $classes | Out-Null
$sourceDir = "app/src/main/java/com/krishang/cockpit"
$testDir = "app/src/test/java/com/krishang/cockpit"
$sources = (Get-ChildItem "$sourceDir/Replay*.java","$sourceDir/TraceCsv.java","$testDir/*Test.java").FullName
& "$env:JAVA_HOME/bin/javac" --release 17 -d $classes $sources
& "$env:JAVA_HOME/bin/java" -cp $classes com.krishang.cockpit.ReplayTraceTest
& "$env:JAVA_HOME/bin/java" -cp $classes com.krishang.cockpit.ReplayClockTest
& "$env:JAVA_HOME/bin/java" -cp $classes com.krishang.cockpit.TraceCsvTest
& "$env:JAVA_HOME/bin/java" -cp $classes com.krishang.cockpit.ReplayAcceptanceTest
```

The acceptance test compares direct seeks with continuous playback through the stale interval and recovery, including a long pause and a rewind. The component tests exercise sample selection, CSV import, invalid traces, seeking during playback, and end-of-trace behavior.
