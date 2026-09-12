#!/usr/bin/env bash
#
# lib.sh — shared helpers for the VAAKKU scripts. Sourced, not executed.
#
# Kept deliberately small: resolve adb, and stop with a readable message when the
# phone or the wrapper is missing. Every script here is run by a human under time
# pressure during a two-day event, so failures should say what to do next.

set -euo pipefail

# ---------------------------------------------------------------------------
# Locate adb. CLAUDE.md says it should be on PATH; if it is not, fall back to the
# platform-tools that ships with the SDK recorded in local.properties.
# ---------------------------------------------------------------------------
resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    echo "adb"
    return 0
  fi

  local sdk_dir=""
  if [ -f "local.properties" ]; then
    # sdk.dir=C\:\\Users\\... — unescape the Java-properties escaping.
    sdk_dir="$(grep -E '^sdk\.dir=' local.properties | head -n 1 | cut -d= -f2- \
               | sed -e 's/\\\\/\\/g' -e 's/\\:/:/g' -e 's/\\ / /g')"
  fi

  for candidate in \
      "${sdk_dir}/platform-tools/adb.exe" \
      "${sdk_dir}/platform-tools/adb" \
      "${ANDROID_HOME:-}/platform-tools/adb.exe" \
      "${ANDROID_HOME:-}/platform-tools/adb" \
      "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ]; then
      echo "${candidate}"
      return 0
    fi
  done

  echo "Could not find adb. Add platform-tools to PATH, or set sdk.dir in local.properties." >&2
  return 2
}

# ---------------------------------------------------------------------------
# Resolve the Gradle launcher, preferring the wrapper the repo pins.
# ---------------------------------------------------------------------------
resolve_gradle() {
  if [ -x "./gradlew" ]; then
    echo "./gradlew"
    return 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    echo "gradle"
    return 0
  fi
  echo "Neither ./gradlew nor a gradle on PATH is available. Run 'gradle wrapper' once." >&2
  return 2
}

# ---------------------------------------------------------------------------
# Fail early and clearly if no device is attached. Installing to no device is the
# most common way to waste a Red Light window.
# ---------------------------------------------------------------------------
require_device() {
  local adb_bin="$1"
  local count
  count="$("${adb_bin}" devices | grep -cE '\bdevice$' || true)"
  if [ "${count}" -eq 0 ]; then
    echo "No device attached. Check USB, accept the USB-debugging prompt on the phone, then retry." >&2
    "${adb_bin}" devices >&2 || true
    return 1
  fi
  echo "    device count: ${count}"
}
