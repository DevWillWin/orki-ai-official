#!/usr/bin/env bash
set -euo pipefail

# Measures time to first visible app frame on a connected Android device/emulator.
# Usage: scripts/measure-startup.sh [iterations]
ITERATIONS="${1:-5}"
PACKAGE="com.devdgames.orkiai"
ACTIVITY="com.example.MainActivity"
COMPONENT="${PACKAGE}/${ACTIVITY}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 1
fi
if ! adb get-state >/dev/null 2>&1; then
  echo "No Android device/emulator is connected" >&2
  exit 1
fi

echo "Cold process startup measurements for ${COMPONENT}"
echo "The first result is retained (it represents first launch after install)."
printf "run,total_ms,wait_ms,logged_first_frame_ms\n"

for run in $(seq 1 "${ITERATIONS}"); do
  adb shell am force-stop "${PACKAGE}"
  adb logcat -c

  output="$(adb shell am start -S -W -n "${COMPONENT}")"
  total="$(printf '%s\n' "${output}" | awk -F': ' '/^TotalTime:/ {gsub(/\r/, "", $2); print $2}')"
  wait="$(printf '%s\n' "${output}" | awk -F': ' '/^WaitTime:/ {gsub(/\r/, "", $2); print $2}')"

  # Let the posted first-draw log line flush. This is not part of the measured launch.
  sleep 0.25
  first_frame="$(adb logcat -d -s OrkiStartup:I '*:S' | sed -n 's/.*first_frame_drawn_ms=\([0-9][0-9]*\).*/\1/p' | tail -1)"
  printf "%s,%s,%s,%s\n" "${run}" "${total:-n/a}" "${wait:-n/a}" "${first_frame:-n/a}"
done
