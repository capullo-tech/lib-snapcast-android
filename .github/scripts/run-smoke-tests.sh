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

# `am instrument` exits 0 even when tests fail, so judge by JUnit's summary line.
if grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$out_dir/instrumentation.txt"; then
  echo "Smoke tests passed."
else
  echo "::error::Smoke tests failed on API $(adb shell getprop ro.build.version.sdk | tr -d '\r'). See instrumentation.txt above."
  exit 1
fi
