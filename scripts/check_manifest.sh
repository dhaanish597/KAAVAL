#!/usr/bin/env bash
#
# check_manifest.sh — proves the app cannot reach the network.
#
# The product is offline by definition (CLAUDE.md #3, build plan §6.2). Declaring
# no networking libraries is not enough: libraries we DO depend on — ML Kit's
# transport and logging artifacts, in particular — contribute INTERNET and
# ACCESS_NETWORK_STATE through manifest merging. The app manifest removes both
# with tools:node="remove", and this script is what proves the removal actually
# took effect on the merged output.
#
# Two independent checks, because they can disagree and that disagreement is
# itself the bug:
#   1. the merged manifest that AGP assembles (intermediates)
#   2. the AndroidManifest.xml actually inside the built APK, when one exists
#
# BOTH VARIANTS ARE CHECKED. Debug is what we install during the build; release
# is what P7 installs through the final install path (§13). They are produced by
# different tasks and can differ — a variant-specific manifest, a debug-only
# dependency, a manifest placeholder — so checking one and installing the other
# proves nothing about the artifact that ships.
#
# Exits non-zero on any finding. Run before every install (CLAUDE.md, "Always do").

set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

if [ -x "./gradlew" ]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "check_manifest.sh: no ./gradlew and no gradle on PATH." >&2
  echo "Run 'gradle wrapper' once to create the wrapper, then re-run." >&2
  exit 2
fi

echo "==> check_manifest.sh"
echo "    repo: $REPO_ROOT"

fail=0

# ---------------------------------------------------------------------------
# Reading a binary AndroidManifest.xml.
#
# The APK's manifest is binary XML (AXML) and its string pool is **UTF-16LE**,
# so every character is followed by a NUL byte. A plain `grep` for
# "android.permission.INTERNET" therefore matches nothing — not because the
# permission is absent, but because the bytes on disk are
# a\0n\0d\0r\0o\0i\0d\0... That is exactly how this script used to report
# "ok: INTERNET absent from APK" on every run without ever having looked.
#
# Two ways to read it properly, in order of preference:
#   aapt2 dump permissions — parses the AXML and prints uses-permission lines.
#   tr -d '\000' | grep    — strips the interleaved NULs so the text greps.
# The fallback is a real check, not a placebo: it finds RECORD_AUDIO in this
# APK, which is the positive control that proves the read worked.
# ---------------------------------------------------------------------------
find_aapt2() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if command -v aapt2 >/dev/null 2>&1; then command -v aapt2; return; fi
  [ -n "$sdk" ] || return 0
  find "$sdk/build-tools" -maxdepth 2 -name 'aapt2*' -type f 2>/dev/null | sort | tail -n 1
}
AAPT2="$(find_aapt2 || true)"

# Prints the APK's permission names, one per line, or returns 1 if it cannot
# read the APK at all. Never returns 0 with empty output on a valid APK —
# see the positive control in check_apk.
apk_permissions() {
  local apk="$1"
  if [ -n "${AAPT2}" ] && [ -x "${AAPT2}" ]; then
    "${AAPT2}" dump permissions "$apk" 2>/dev/null \
      | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p"
  else
    unzip -p "$apk" AndroidManifest.xml 2>/dev/null \
      | tr -d '\000' \
      | grep -ao 'android\.permission\.[A-Z_]*' \
      | sort -u
  fi
}

# ---------------------------------------------------------------------------
# check_merged <variant> <merge-task> <merged-manifest-path-fragment>
#
# The path is derived from the variant, NOT from `find | sort | tail -1`. That
# old approach picked whichever merged manifest sorted last across the whole
# intermediates tree — which, once a release build existed, silently became the
# release manifest while the script still printed the debug task name. A check
# that names one variant and inspects another is worse than no check.
# ---------------------------------------------------------------------------
check_merged() {
  local variant="$1" task="$2"

  echo
  echo "==> [$variant] :app:$task"
  "$GRADLE" --quiet ":app:$task"

  local merged="app/build/intermediates/merged_manifest/$variant/$task/AndroidManifest.xml"
  if [ ! -f "${merged}" ]; then
    echo "FAIL: [$variant] merged manifest not found at ${merged}" >&2
    echo "      The build step above must have failed, or AGP changed its output path." >&2
    fail=1
    return
  fi
  echo "    merged manifest: $merged"

  # Verify the manifest still declares the permissions we DO need. Without this,
  # a green result below would be meaningless — an empty or truncated file would
  # also "contain no INTERNET". This is the positive control.
  local required
  for required in RECORD_AUDIO CAMERA FOREGROUND_SERVICE FOREGROUND_SERVICE_MICROPHONE; do
    if grep -q "android.permission.$required" "$merged"; then
      echo "    ok: $required present"
    else
      echo "FAIL: [$variant] required permission $required is missing from the merged manifest." >&2
      fail=1
    fi
  done

  # The actual assertion.
  local forbidden hits
  for forbidden in INTERNET ACCESS_NETWORK_STATE; do
    hits="$(grep -c "android.permission.$forbidden" "$merged" || true)"
    if [ "${hits}" != "0" ]; then
      echo "FAIL: [$variant] android.permission.$forbidden appears ${hits}x in the merged manifest." >&2
      echo "      Expected it to be removed by tools:node=\"remove\" in" >&2
      echo "      app/src/main/AndroidManifest.xml. Do not delete those two lines," >&2
      echo "      and do not add a library that re-adds the permission." >&2
      grep -n "android.permission.$forbidden" "$merged" >&2 || true
      fail=1
    else
      echo "    ok: $forbidden absent from merged manifest"
    fi
  done

  check_service "$variant" "$merged"
}

# ---------------------------------------------------------------------------
# The microphone service, and the two attributes that matter (§6.7).
#
#   foregroundServiceType="microphone" — without it, startForeground() with
#     FOREGROUND_SERVICE_TYPE_MICROPHONE throws on API 34+, so the session dies
#     on the first tap rather than at some later, quieter moment.
#   exported="false" — nothing outside this app may open the microphone. On
#     targetSdk 36 an unspecified android:exported on a service with no intent
#     filter defaults to false, but "the default happens to be safe" is not the
#     standard this file holds things to; it is written down and checked.
# ---------------------------------------------------------------------------
check_service() {
  local variant="$1" merged="$2"

  if ! grep -q 'app.vaakku.session.SessionService' "$merged"; then
    echo "FAIL: [$variant] SessionService is not in the merged manifest." >&2
    echo "      The session's microphone runs as a foreground service (§6.7); without" >&2
    echo "      the declaration, startForegroundService() throws and no session can start." >&2
    fail=1
    return
  fi
  echo "    ok: SessionService declared"

  # The service element spans several lines in the merged output, so pull the
  # element itself out before looking at its attributes — a bare grep over the
  # whole file would happily match an attribute belonging to the activity.
  local service_el
  service_el="$(tr '\n' ' ' < "$merged" \
                  | grep -o '<service[^>]*app\.vaakku\.session\.SessionService[^>]*>' || true)"

  if [ -z "${service_el}" ]; then
    echo "FAIL: [$variant] found SessionService by name but could not isolate its <service> element." >&2
    echo "      Check app/src/main/AndroidManifest.xml by hand." >&2
    fail=1
    return
  fi

  if printf '%s' "${service_el}" | grep -q 'android:foregroundServiceType="microphone"'; then
    echo "    ok: SessionService foregroundServiceType=microphone"
  else
    echo "FAIL: [$variant] SessionService is missing android:foregroundServiceType=\"microphone\"." >&2
    echo "      startForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE) throws without it." >&2
    fail=1
  fi

  if printf '%s' "${service_el}" | grep -q 'android:exported="false"'; then
    echo "    ok: SessionService not exported"
  else
    echo "FAIL: [$variant] SessionService must declare android:exported=\"false\" explicitly." >&2
    echo "      Nothing outside this app may start the microphone." >&2
    fail=1
  fi
}

# ---------------------------------------------------------------------------
# check_apk <variant>
#
# Inspects the artifact that would actually be installed rather than an
# intermediate, so it catches a merge configuration that only applies to one
# path. Absent an APK this is skipped — but a *present* APK that yields no
# permissions at all is a FAILURE, not a pass: that is the exact shape of the
# UTF-16 bug this check used to have.
# ---------------------------------------------------------------------------
check_apk() {
  local variant="$1"
  local apk
  apk="$(find "app/build/outputs/apk/$variant" -type f -name '*.apk' 2>/dev/null | head -n 1)"

  if [ -z "${apk}" ] || [ ! -f "${apk}" ]; then
    echo "    note: no $variant APK built yet; checked the merged manifest only."
    return
  fi
  echo "    apk: $apk"

  local perms
  perms="$(apk_permissions "$apk" || true)"

  # Positive control. RECORD_AUDIO is in every variant of this app, so if the
  # read produced nothing — or produced something without it — the reader is
  # broken and every "absent" below would be a lie.
  if ! printf '%s\n' "${perms}" | grep -q 'android.permission.RECORD_AUDIO'; then
    echo "FAIL: [$variant] could not read permissions out of the APK." >&2
    echo "      RECORD_AUDIO is declared in every variant and was not found, so this" >&2
    echo "      check cannot vouch for INTERNET being absent either. The APK manifest" >&2
    echo "      is binary AXML with a UTF-16LE string pool: a plain grep silently" >&2
    echo "      matches nothing. Install build-tools (aapt2) or fix apk_permissions." >&2
    fail=1
    return
  fi
  echo "    ok: APK manifest readable (RECORD_AUDIO found)"

  local forbidden
  for forbidden in INTERNET ACCESS_NETWORK_STATE; do
    if printf '%s\n' "${perms}" | grep -q "android.permission.$forbidden"; then
      echo "FAIL: [$variant] android.permission.$forbidden is present in the APK's manifest." >&2
      fail=1
    else
      echo "    ok: $forbidden absent from APK"
    fi
  done
}

if [ -n "${AAPT2}" ] && [ -x "${AAPT2}" ]; then
  echo "    aapt2: ${AAPT2}"
else
  echo "    aapt2: not found — falling back to NUL-stripped grep (still checked)"
fi

check_merged debug processDebugMainManifest
check_apk debug

check_merged release processReleaseMainManifest
check_apk release

echo
if [ "${fail}" -ne 0 ]; then
  echo "RESULT: FAIL — the app is not provably offline."
  exit 1
fi

echo "RESULT: PASS — no INTERNET, no ACCESS_NETWORK_STATE, in the merged manifest and the built APK, for BOTH debug and release."
