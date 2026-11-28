#!/usr/bin/env bash
# Installs the prebuilt app + test APKs on the connected device and runs SnapcastSmokeTest.
# Usage: run-smoke-tests.sh <app.apk> <androidTest.apk> <output dir>
set -euo pipefail

app_apk=$1
test_apk=$2
out_dir=$3

test_class=tech.capullo.snapcastandroidexample.SnapcastSmokeTest
runner=tech.capullo.snapcastandroidexample.test/androidx.test.runner.AndroidJUnitRunner

mkdir -p "$out_dir"
adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
adb logcat -c || true

set +e
adb shell am instrument -w -e class "$test_class" "$runner" | tr -d '\r' | tee "$out_dir/instrumentation.txt"
set -e

adb logcat -d > "$out_dir/logcat.txt" 2>&1 || true
api_level=$(adb shell getprop ro.build.version.sdk | tr -d '\r')

# android-emulator-runner's own "adb emu kill" can block forever (seen on API 36 after all tests
# had passed), holding the job until its timeout. Stop the emulator here, bounded; the action's
# later kill then fails fast with "device not found", which it only logs.
stop_emulator() {
  timeout 30 adb emu kill > /dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    pgrep -f qemu-system > /dev/null || return 0
    sleep 1
  done
  echo "Emulator did not exit after 'adb emu kill'; killing qemu."
  pkill -9 -f qemu-system || true
}

# `am instrument` exits 0 even when tests fail, so judge by JUnit's summary line.
if grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$out_dir/instrumentation.txt"; then
  echo "Smoke tests passed on API $api_level."
  stop_emulator
else
  echo "::error::Smoke tests failed on API $api_level. See instrumentation.txt above."
  stop_emulator
  exit 1
fi
