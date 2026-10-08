#!/usr/bin/env bash
# Runs the instrumented tests on the connected emulator and collects screenshots,
# the test output and logcat into device-test-output/<name>/.
# Usage: device-tests.sh phone|tablet
set -u

OUT="device-test-output/${1:-device}"
mkdir -p "$OUT"

./gradlew :app:installDebug :app:installDebugAndroidTest --stacktrace || exit 1

adb logcat -c
# Run the tests directly (not via connectedAndroidTest) so the app stays installed
# and its screenshots can be pulled afterwards.
adb shell am instrument -w com.hamza.todo.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrumentation.txt"
adb logcat -d > "$OUT/logcat.txt"
adb exec-out screencap -p > "$OUT/final-screen.png" || true
adb pull /sdcard/Android/data/com.hamza.todo/files/screenshots "$OUT/" || true

if grep -q "FAILURES!!!" "$OUT/instrumentation.txt" || ! grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "Device tests failed"
  exit 1
fi
