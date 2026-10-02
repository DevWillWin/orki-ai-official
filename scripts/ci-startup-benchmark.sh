#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.devdgames.orkiai"
COMPONENT="${PACKAGE}/com.example.MainActivity"
RESULTS_FILE="${RESULTS_FILE:-startup-results.csv}"
ITERATIONS="${ITERATIONS:-5}"

echo "version,run,total_ms,wait_ms,first_frame_log_ms" > "${RESULTS_FILE}"

measure_apk() {
  local version="$1"
  local apk="$2"

  echo "::group::${version}: ${apk}"
  adb uninstall "${PACKAGE}" >/dev/null 2>&1 || true
  adb install "${apk}"
  adb shell pm clear "${PACKAGE}" >/dev/null

  for run in $(seq 1 "${ITERATIONS}"); do
    adb shell am force-stop "${PACKAGE}"
    adb logcat -c
    local output total wait first_frame
    output="$(adb shell am start -S -W -n "${COMPONENT}")"
    echo "${output}"
    total="$(printf '%s\n' "${output}" | awk -F': ' '/^TotalTime:/ {gsub(/\r/, "", $2); print $2}')"
    wait="$(printf '%s\n' "${output}" | awk -F': ' '/^WaitTime:/ {gsub(/\r/, "", $2); print $2}')"
    sleep 0.25
    first_frame="$(adb logcat -d -s OrkiStartup:I '*:S' | sed -n 's/.*first_frame_drawn_ms=\([0-9][0-9]*\).*/\1/p' | tail -1)"
    echo "${version},${run},${total:-0},${wait:-0},${first_frame:-0}" | tee -a "${RESULTS_FILE}"
  done
  echo "::endgroup::"
}

if [[ "$#" -eq 0 || $(( $# % 2 )) -ne 0 ]]; then
  echo "Usage: $0 <version-label> <apk-path> [<version-label> <apk-path> ...]" >&2
  exit 2
fi

while [[ "$#" -gt 0 ]]; do
  measure_apk "$1" "$2"
  shift 2
done

echo
cat "${RESULTS_FILE}"
echo
for version in $(tail -n +2 "${RESULTS_FILE}" | cut -d, -f1 | uniq); do
  median="$(awk -F, -v version="${version}" '$1 == version { print $3 }' "${RESULTS_FILE}" | sort -n | awk '{ values[NR]=$1 } END { if (NR % 2) print values[(NR+1)/2]; else print (values[NR/2]+values[NR/2+1])/2 }')"
  echo "${version} median TotalTime: ${median} ms"
done
