#!/usr/bin/env bash
set -euo pipefail

diagnostics() {
  adb logcat -d > device-logcat.txt || true
  adb shell dumpsys window > device-window.txt || true
  adb shell screencap -p /sdcard/test-screen.png || true
  adb pull /sdcard/test-screen.png test-screen.png || true
  adb pull /sdcard/Android/data/com.strengthtimer.app/files/screenshots screenshots || true
}
trap diagnostics EXIT

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install apks/debug/app-debug.apk
adb install apks/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.strengthtimer.app android.permission.POST_NOTIFICATIONS

# Cold boot can leave System UI's startup ANR above every app.
# Restart only these emulator components before testing the application.
adb root
adb wait-for-device
adb shell settings put system screen_off_timeout 2147483647
sleep 20
adb shell pkill -f '^com\.android\.systemui$' || true
adb shell am force-stop com.android.launcher3
sleep 10
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am start -W -n com.strengthtimer.app/.MainActivity

app_focus_ready=false
for attempt in $(seq 1 15); do
  adb shell dumpsys window > preflight-window.txt
  if grep -q 'mCurrentFocus=.*com\.strengthtimer\.app/' preflight-window.txt; then
    app_focus_ready=true
    break
  fi
  sleep 2
done
if [ "$app_focus_ready" != true ]; then
  cat preflight-window.txt
  exit 1
fi

adb shell am instrument -w com.strengthtimer.app.test/androidx.test.runner.AndroidJUnitRunner | tee instrument-results.txt
grep -q 'OK (14 tests)' instrument-results.txt
