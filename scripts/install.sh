#!/usr/bin/env bash
#
# install.sh — build the debug APK, install it over the existing one, and launch.
#
# Three deliberate choices:
#
#  1. `adb install -r` (replace). NEVER uninstall: the ASR models live in
#     /sdcard/Android/data/app.vaakku/files/, which an uninstall deletes, and
#     re-pushing several hundred megabytes mid-event is not affordable.
#     CLAUDE.md lists `adb uninstall` under "Never do" for this reason.
#
#  2. The manifest guard runs first. A build that quietly gained INTERNET would
#     invalidate the product's central claim, and the install is the last moment
#     it can be caught cheaply.
#
#  3. After N1 (NPU integration) the install path becomes
#     scripts/install_bundle.sh (bundletool, local testing). This script remains
#     the plain-APK path until then.
#
# Usage:
#   scripts/install.sh [--no-manifest-check]

set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
source scripts/lib.sh

PKG="app.vaakku"
ACTIVITY="${PKG}/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

RUN_MANIFEST_CHECK=1
for arg in "$@"; do
  case "${arg}" in
    --no-manifest-check) RUN_MANIFEST_CHECK=0 ;;
    *) echo "install.sh: unknown argument: ${arg}" >&2; exit 2 ;;
  esac
done

GRADLE="$(resolve_gradle)"
ADB="$(resolve_adb)"

echo "==> install.sh"

if [ "${RUN_MANIFEST_CHECK}" -eq 1 ]; then
  echo "==> manifest guard (scripts/check_manifest.sh)"
  ./scripts/check_manifest.sh
else
  echo "==> manifest guard SKIPPED (--no-manifest-check). Do not do this before a gate."
fi

echo "==> :app:assembleDebug"
"${GRADLE}" :app:assembleDebug

if [ ! -f "${APK}" ]; then
  echo "FAIL: ${APK} was not produced." >&2
  exit 1
fi

require_device "${ADB}"

echo "==> adb install -r ${APK}"
"${ADB}" install -r "${APK}"

echo "==> launching ${ACTIVITY}"
# -W waits for the launch to complete, so a crash on startup surfaces here rather
# than as a silent no-op.
"${ADB}" shell am start -W -n "${ACTIVITY}"

echo
echo "==> installed and launched. For a screenshot:"
echo "    adb exec-out screencap -p > evidence/<name>.png"
