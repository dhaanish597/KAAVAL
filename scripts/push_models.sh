#!/usr/bin/env bash
#
# push_models.sh — copy ASR models and the VAD to the phone's app-external storage.
#
# Destination (build plan §6.3):
#   /sdcard/Android/data/app.vaakku/files/models/<model-dir>/
# which the app reads as getExternalFilesDir("models"). The app loads models from
# ABSOLUTE paths there rather than from assets, because a 350 MB model cannot
# usefully live in the APK.
#
# Two cautions that shape this script:
#   * Uninstalling the app DELETES these folders. Always install with `-r`
#     (scripts/install.sh does). CLAUDE.md lists `adb uninstall` under "Never do".
#   * A full re-push of the model set is several hundred megabytes. If that is the
#     norm, we lose minutes per iteration, so files already present with a matching
#     size are skipped. Size, not hash: hashing 350 MB over adb costs about as much
#     as the push it would avoid.
#
# Usage:
#   scripts/push_models.sh [source_dir]
#
# Default source_dir is ./models. The archives downloaded during preparation can
# be unpacked there, or passed explicitly (for example handoff/models).

set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
source scripts/lib.sh

PKG="app.vaakku"
DEST_ROOT="/sdcard/Android/data/${PKG}/files/models"

SOURCE_DIR="${1:-models}"

if [ ! -d "${SOURCE_DIR}" ]; then
  echo "push_models.sh: no such directory: ${SOURCE_DIR}" >&2
  exit 2
fi

# Does the source dir actually hold model content? An empty ./models is the
# expected state on a fresh checkout, and failing with a pointer is friendlier
# than reporting "0 files pushed".
model_file_count="$(find "${SOURCE_DIR}" -type f \( -name '*.onnx' -o -name '*.txt' \) 2>/dev/null | wc -l | tr -d ' ')"
if [ "${model_file_count}" -eq 0 ]; then
  echo "push_models.sh: no .onnx or .txt files under ${SOURCE_DIR}." >&2
  echo "  Nothing to push." >&2
  if [ -d handoff/models ]; then
    echo "  The prepared model set is in handoff/models — try:" >&2
    echo "      scripts/push_models.sh handoff/models" >&2
  fi
  exit 2
fi

ADB="$(resolve_adb)"

echo "==> push_models.sh"
echo "    source: ${SOURCE_DIR}"
echo "    dest:   ${DEST_ROOT}"

require_device "${ADB}"

# Every adb call below reads its stdin from /dev/null. This is not decoration.
#
# `adb` forwards its own stdin to the device, and the push loops are `while read`
# loops fed by process substitution — so an adb invocation inside the loop body
# consumes the file list the loop is still reading from. The first version of this
# script pushed exactly TWO of 1.1 GB of model files and exited 0, because adb
# drained the `find` output after the first iteration. It reported
# "pushed 2, skipped 0, failed 0" and looked like a success.
#
# That failure mode is worth spelling out: it is silent, it is green, and the
# engines would then fail on the phone with "missing model.int8.onnx" pointing the
# blame at the app. Keep the redirects.
"${ADB}" shell mkdir -p "${DEST_ROOT}" </dev/null

pushed=0
skipped=0
failed=0

# Remote size of a path, or empty if it does not exist. `stat -c %s` is available
# in toybox; `wc -c` is the fallback for the rare ROM without it.
remote_size() {
  local path="$1"
  local out
  out="$("${ADB}" shell stat -c %s "'${path}'" 2>/dev/null </dev/null | tr -d '\r\n' || true)"
  case "${out}" in
    ''|*[!0-9]*) echo "" ;;
    *) echo "${out}" ;;
  esac
}

push_one() {
  local local_path="$1"
  local remote_path="$2"
  local local_size
  local_size="$(wc -c < "${local_path}" | tr -d ' ')"

  local existing
  existing="$(remote_size "${remote_path}")"

  if [ -n "${existing}" ] && [ "${existing}" = "${local_size}" ]; then
    echo "    skip   (${local_size} B) ${remote_path}"
    skipped=$((skipped + 1))
    return 0
  fi

  echo "    push   (${local_size} B) ${remote_path}"
  if "${ADB}" push "${local_path}" "${remote_path}" >/dev/null </dev/null; then
    pushed=$((pushed + 1))
  else
    echo "    FAILED ${local_path}" >&2
    failed=$((failed + 1))
  fi
}

# --- Model directories: push the tree, mirroring the folder name. ------------
# Process substitution rather than a pipe: a `while read` fed by a pipe runs in a
# subshell, and the pushed/skipped/failed counters would reset to zero before the
# summary below could print them.
while read -r dir; do
  name="$(basename "${dir}")"
  echo "  ${name}"
  "${ADB}" shell mkdir -p "${DEST_ROOT}/${name}" </dev/null
  while read -r f; do
    rel="${f#${dir}/}"
    # adb push does not create intermediate directories, so make them first.
    rel_dir="$(dirname "${rel}")"
    if [ "${rel_dir}" != "." ]; then
      "${ADB}" shell mkdir -p "${DEST_ROOT}/${name}/${rel_dir}" </dev/null
    fi
    push_one "${f}" "${DEST_ROOT}/${name}/${rel}"
  done < <(find "${dir}" -type f | sort)

  # Make the directory traversable by the app. This is not optional.
  #
  # `adb shell mkdir` creates directories owned by `shell`, mode 2770 (group
  # ext_data_rw, NO world execute). The app runs as its own uid, is not shell and
  # is not in that group, so it can stat the directory but cannot traverse INTO
  # it. The observed symptom is precise and misleading: every engine reports
  # "missing: model.int8.onnx, tokens.txt" while `adb shell ls` shows both files
  # present at the right size. `File.isDirectory()` succeeds (it only needs the
  # parent, which the app owns) and `File.isFile()` on each child fails.
  #
  # DEST_ROOT itself is created by the app via getExternalFilesDir() and is owned
  # by the app, so chmod on it fails with "Operation not permitted" — expected,
  # and not a problem, because the app already owns it.
  "${ADB}" shell chmod -R 777 "${DEST_ROOT}/${name}" </dev/null 2>/dev/null || true
done < <(find "${SOURCE_DIR}" -mindepth 1 -maxdepth 1 -type d | sort)

# --- Loose files at the top level (silero_vad.onnx). -------------------------
# The VAD is not inside a model directory; the app expects it at the root of
# files/models/.
while read -r f; do
  push_one "${f}" "${DEST_ROOT}/$(basename "${f}")"
  "${ADB}" shell chmod 666 "${DEST_ROOT}/$(basename "${f}")" </dev/null 2>/dev/null || true
done < <(find "${SOURCE_DIR}" -mindepth 1 -maxdepth 1 -type f | sort)

echo
echo "==> pushed ${pushed}, skipped ${skipped} (already present, same size), failed ${failed}"

# --- Completeness check ------------------------------------------------------
# Every local file must have been either pushed or skipped-as-identical. Without
# this, the stdin bug described at the top of this script exits 0 after moving two
# of several hundred files, and the first sign of trouble is an engine failing to
# load on the phone twenty minutes later. Counting is cheap; being wrong here is
# not (CLAUDE.md #7).
local_total="$(find "${SOURCE_DIR}" -type f | wc -l | tr -d ' ')"
accounted=$((pushed + skipped))

if [ "${failed}" -ne 0 ]; then
  echo "push_models.sh: ${failed} file(s) FAILED to push." >&2
  exit 1
fi

if [ "${accounted}" -ne "${local_total}" ]; then
  echo "push_models.sh: accounted for ${accounted} file(s) but ${SOURCE_DIR} holds ${local_total}." >&2
  echo "  Nothing was reported as failed, so files were silently skipped by the loop," >&2
  echo "  not by the device. Do not treat this run as a successful push." >&2
  exit 1
fi

echo "==> all ${local_total} file(s) under ${SOURCE_DIR} are accounted for."

echo
echo "==> done. Files on the phone:"
"${ADB}" shell "du -sh ${DEST_ROOT}/* 2>/dev/null || ls -la ${DEST_ROOT}" </dev/null || true

echo
echo "Reminder: uninstalling ${PKG} deletes ${DEST_ROOT}. Always install with -r."
