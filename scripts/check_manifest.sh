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

# ---------------------------------------------------------------------------
# Step 1: produce the merged manifest for the debug variant.
# Debug is the variant we install and run, so it is the one that must be clean.
# ---------------------------------------------------------------------------
echo "==> :app:processDebugMainManifest"
"$GRADLE" --quiet :app:processDebugMainManifest

MERGED="$(find app/build/intermediates -type f -name 'AndroidManifest.xml' \
            -path '*merged_manifest*' 2>/dev/null | sort | tail -n 1)"

if [ -z "${MERGED}" ] || [ ! -f "${MERGED}" ]; then
  echo "FAIL: could not find the merged manifest under app/build/intermediates." >&2
  echo "      The build step above must have failed, or AGP changed its output path." >&2
  exit 1
fi

echo "    merged manifest: $MERGED"

fail=0

# ---------------------------------------------------------------------------
# Step 2: verify the merged manifest still declares the permissions we DO need.
# Without this, a green result below would be meaningless — an empty or truncated
# file would also "contain no INTERNET".
# ---------------------------------------------------------------------------
for required in RECORD_AUDIO CAMERA; do
  if grep -q "android.permission.$required" "$MERGED"; then
    echo "    ok: $required present"
  else
    echo "FAIL: required permission $required is missing from the merged manifest." >&2
    fail=1
  fi
done

# ---------------------------------------------------------------------------
# Step 3: the actual assertion. INTERNET and ACCESS_NETWORK_STATE must be gone.
# ---------------------------------------------------------------------------
for forbidden in INTERNET ACCESS_NETWORK_STATE; do
  hits="$(grep -c "android.permission.$forbidden" "$MERGED" || true)"
  if [ "${hits}" != "0" ]; then
    echo "FAIL: android.permission.$forbidden appears ${hits}x in the merged manifest." >&2
    echo "      Expected it to be removed by tools:node=\"remove\" in" >&2
    echo "      app/src/main/AndroidManifest.xml. Do not delete those two lines," >&2
    echo "      and do not add a library that re-adds the permission." >&2
    grep -n "android.permission.$forbidden" "$MERGED" >&2 || true
    fail=1
  else
    echo "    ok: $forbidden absent from merged manifest"
  fi
done

# ---------------------------------------------------------------------------
# Step 4: check the built APK too, if there is one.
# This inspects the artifact that would actually be installed rather than an
# intermediate, so it catches a merge configuration that only applies to one path.
# The APK's AndroidManifest.xml is binary, but permission names live in its string
# pool as plain text, so a byte-wise grep is a valid check.
# ---------------------------------------------------------------------------
APK="$(find app/build/outputs/apk/debug -type f -name '*.apk' 2>/dev/null | head -n 1)"
if [ -n "${APK}" ] && [ -f "${APK}" ]; then
  echo "    apk: $APK"
  TMP_MANIFEST="$(mktemp)"
  if unzip -p "${APK}" AndroidManifest.xml > "${TMP_MANIFEST}" 2>/dev/null; then
    for forbidden in INTERNET ACCESS_NETWORK_STATE; do
      if grep -aq "android.permission.$forbidden" "${TMP_MANIFEST}"; then
        echo "FAIL: android.permission.$forbidden is present in the APK's manifest." >&2
        fail=1
      else
        echo "    ok: $forbidden absent from APK"
      fi
    done
  else
    echo "    note: could not extract AndroidManifest.xml from the APK; merged-manifest check stands."
  fi
  rm -f "${TMP_MANIFEST}"
else
  echo "    note: no debug APK found yet; checked the merged manifest only."
fi

# ---------------------------------------------------------------------------
# Step 5: the microphone service, and the two attributes that matter (§6.7).
#
# Until P5 this step asserted the opposite — that SessionService was NOT yet
# declared, which was the correct check while the class did not exist. The class
# exists now, so the check is inverted: a session that cannot start its
# foreground service is a session that silently loses the microphone the moment
# the screen turns off.
#
# The two attributes are not cosmetic:
#   foregroundServiceType="microphone" — without it, startForeground() with
#     FOREGROUND_SERVICE_TYPE_MICROPHONE throws on API 34+, so the session dies
#     on the first tap rather than at some later, quieter moment.
#   exported="false" — nothing outside this app may open the microphone. On
#     targetSdk 36 an unspecified android:exported on a service with no intent
#     filter defaults to false, but "the default happens to be safe" is not the
#     standard this file holds things to; it is written down and checked.
# ---------------------------------------------------------------------------
if grep -q 'app.vaakku.session.SessionService' "$MERGED"; then
  echo "    ok: SessionService declared"

  # The service element spans several lines in the merged output, so pull the
  # element itself out before looking at its attributes — a bare grep over the
  # whole file would happily match an attribute belonging to the activity.
  SERVICE_EL="$(tr '\n' ' ' < "$MERGED" \
                  | grep -o '<service[^>]*app\.vaakku\.session\.SessionService[^>]*>' || true)"

  if [ -z "${SERVICE_EL}" ]; then
    echo "FAIL: found SessionService by name but could not isolate its <service> element." >&2
    echo "      Check app/src/main/AndroidManifest.xml by hand." >&2
    fail=1
  else
    if printf '%s' "${SERVICE_EL}" | grep -q 'android:foregroundServiceType="microphone"'; then
      echo "    ok: SessionService foregroundServiceType=microphone"
    else
      echo "FAIL: SessionService is missing android:foregroundServiceType=\"microphone\"." >&2
      echo "      startForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE) throws without it." >&2
      fail=1
    fi

    if printf '%s' "${SERVICE_EL}" | grep -q 'android:exported="false"'; then
      echo "    ok: SessionService not exported"
    else
      echo "FAIL: SessionService must declare android:exported=\"false\" explicitly." >&2
      echo "      Nothing outside this app may start the microphone." >&2
      fail=1
    fi
  fi
else
  echo "FAIL: SessionService is not in the merged manifest." >&2
  echo "      The session's microphone runs as a foreground service (§6.7); without" >&2
  echo "      the declaration, startForegroundService() throws and no session can start." >&2
  fail=1
fi

# The microphone service needs both permissions in the merged output, not just
# the class declaration.
for required in FOREGROUND_SERVICE FOREGROUND_SERVICE_MICROPHONE; do
  if grep -q "android.permission.$required" "$MERGED"; then
    echo "    ok: $required present"
  else
    echo "FAIL: required permission $required is missing from the merged manifest." >&2
    fail=1
  fi
done

echo
if [ "${fail}" -ne 0 ]; then
  echo "RESULT: FAIL — the app is not provably offline."
  exit 1
fi

echo "RESULT: PASS — no INTERNET, no ACCESS_NETWORK_STATE, in both the merged manifest and (if present) the APK."
