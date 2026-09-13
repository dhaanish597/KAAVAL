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
#  3. THIS IS THE FINAL INSTALL PATH — there is no scripts/install_bundle.sh and
#     there will not be one. Build plan line 351 says the path changes after N1
#     to an AAB plus `bundletool build-apks --local-testing`, but it adds: "Keep
#     installDebug only if you verify NPU still loads that way." It does. The G4
#     logcat that proved Hexagon dispatch (BackendType : Htp(2), 175/175 ops,
#     live session 08:19:11) reports
#     libDir=/data/app/~~...==/app.vaakku-...==/lib/arm64 — the plain-APK native
#     library layout, not a --local-testing split install. So the QNN libraries
#     load and the DSP takes the whole graph from an ordinary `adb install -r`,
#     and bundletool would add a step that can fail at an event in exchange for
#     nothing. See STATUS.md decision 91.
#
#     If NPU load ever breaks from a plain APK, that decision is void and line
#     351's main clause applies again. The tell is in logcat: the libDir line
#     above, plus `selected 175 ops`.
#
# Usage:
#   scripts/install.sh [--release] [--no-manifest-check]
#
# --release installs app-release.apk instead of app-debug.apk. P7 (§13) requires
# the release build to go through this path, and debug and release share the
# package name `app.vaakku` with no suffix, so they replace each other in place
# and the pushed models survive either way. Debug remains the default because it
# is what every other step of the build uses.
#
# WHAT YOU LOSE ON RELEASE, so it is not a surprise mid-test: the Dev menu
# (app/src/debug only) and the three-finger-hold debug overlay (gated on
# BuildConfig.DEBUG at both the gesture and the render) are both gone. The scan
# sheet's mask latency label is NOT debug-gated and still shows. Do any dev-menu
# or overlay work BEFORE installing release.

set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
source scripts/lib.sh

PKG="app.vaakku"
ACTIVITY="${PKG}/.MainActivity"

RUN_MANIFEST_CHECK=1
VARIANT="debug"
for arg in "$@"; do
  case "${arg}" in
    --no-manifest-check) RUN_MANIFEST_CHECK=0 ;;
    --release) VARIANT="release" ;;
    --debug) VARIANT="debug" ;;
    *) echo "install.sh: unknown argument: ${arg}" >&2; exit 2 ;;
  esac
done

# Derived from the variant rather than hard-coded, and the Gradle task with it,
# so the script cannot build one variant and install another. check_manifest.sh
# had exactly that bug — it announced debug and inspected release — and it went
# unnoticed for the whole project because both were clean (STATUS.md open issue
# 30, decision 89).
case "${VARIANT}" in
  debug)   APK="app/build/outputs/apk/debug/app-debug.apk";     TASK=":app:assembleDebug" ;;
  release) APK="app/build/outputs/apk/release/app-release.apk"; TASK=":app:assembleRelease" ;;
esac

GRADLE="$(resolve_gradle)"
ADB="$(resolve_adb)"

echo "==> install.sh (variant: ${VARIANT})"

if [ "${RUN_MANIFEST_CHECK}" -eq 1 ]; then
  echo "==> manifest guard (scripts/check_manifest.sh)"
  ./scripts/check_manifest.sh
else
  echo "==> manifest guard SKIPPED (--no-manifest-check). Do not do this before a gate."
fi

echo "==> ${TASK}"
"${GRADLE}" "${TASK}"

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
