#!/usr/bin/env python3
"""Laptop ASR pre-screen — build plan §6.3, §11.1-§11.3, phase P2 step 1.

Decodes every WAV in testdata/testaudio/ with every candidate sherpa-onnx
engine, entirely on the laptop CPU (no phone needed for this step). Writes:

  evidence/asr_prescreen/<engine>/<file>.txt   -- the raw transcript text
  evidence/asr_prescreen/prescreen_results.csv -- engine,file,duration_s,
                                                  decode_s,rtf,text

`:domain:evalTranscripts` then reads the per-file transcripts against
testdata/testaudio/labels.json for slot accuracy; this script's own CSV is the
laptop-RTF half of the §13 P2 decision rule. **Laptop RTF is indicative only** —
the gate's "RTF <= 0.5" is a *phone* number and comes from the on-device
bake-off screen (§6.3), not from here.

Model config is read from `models/*/` directly (see models/MANIFEST.md for what
is what) using the exact sherpa_onnx.OfflineRecognizer factory method for each
model family — read from the installed sherpa_onnx package's own source
(offline_recognizer.py), not guessed:
  - Dolphin CTC (small, base)        -> OfflineRecognizer.from_dolphin_ctc
  - Omnilingual ASR CTC (300M)       -> OfflineRecognizer.from_omnilingual_asr_ctc
  - Whisper (small, Tamil fine-tune) -> OfflineRecognizer.from_whisper(language="ta")

Note on sample rate: `accept_waveform()` is always handed the WAV's **true**
rate. sherpa-onnx resamples internally to the model's 16 kHz when they differ
("Creating a resampler: in 44100 out 16000"), and the result is byte-identical
to an explicitly pre-resampled decode. Passing a rate the audio does not have
silently corrupts the decode, so never hard-code 16000 here.

Usage: python tools/asr_prescreen/prescreen.py [engine ...]
       (no arguments = all engines)
"""
from __future__ import annotations

import csv
import io
import sys
import time
import traceback
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import sherpa_onnx
import soundfile as sf

REPO_ROOT = Path(__file__).resolve().parents[2]
MODELS_DIR = REPO_ROOT / "models"
TESTAUDIO_DIR = REPO_ROOT / "testdata" / "testaudio"
OUT_DIR = REPO_ROOT / "evidence" / "asr_prescreen"
CSV_PATH = OUT_DIR / "prescreen_results.csv"
CSV_FIELDS = ["engine", "file", "duration_s", "decode_s", "rtf", "text"]

# §6.3 sets numThreads default 4 on the phone. Match it here so the laptop RTF
# is at least measured under the same threading policy.
NUM_THREADS = 4

# Console on Windows defaults to cp1252, which cannot print Tamil script.
# Every actual transcript goes to a UTF-8 file, never to stdout, but wrap
# stdout defensively anyway so a stray print() never crashes the run.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


@dataclass
class Engine:
    name: str
    build: Callable[[], "sherpa_onnx.OfflineRecognizer"]


def dolphin_engine(name: str, folder: str) -> Engine:
    d = MODELS_DIR / folder
    return Engine(
        name=name,
        build=lambda: sherpa_onnx.OfflineRecognizer.from_dolphin_ctc(
            model=str(d / "model.int8.onnx"),
            tokens=str(d / "tokens.txt"),
            num_threads=NUM_THREADS,
        ),
    )


def omnilingual_engine() -> Engine:
    d = MODELS_DIR / "sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12"
    return Engine(
        name="omnilingual_300m",
        # from_omnilingual_asr_ctc exposes no language argument in sherpa-onnx
        # 1.13.8 (checked against the installed offline_recognizer.py), so the
        # model picks the output script itself. That is a real limitation of
        # this engine for our audio, not a missing parameter here.
        build=lambda: sherpa_onnx.OfflineRecognizer.from_omnilingual_asr_ctc(
            model=str(d / "model.int8.onnx"),
            tokens=str(d / "tokens.txt"),
            num_threads=NUM_THREADS,
        ),
    )


def whisper_small_ta_engine() -> Engine:
    d = MODELS_DIR / "whisper-small-ta"
    return Engine(
        name="whisper_small_ta",
        build=lambda: sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=str(d / "encoder.int8.onnx"),
            decoder=str(d / "decoder.int8.onnx"),
            tokens=str(d / "tokens.txt"),
            language="ta",
            task="transcribe",
            num_threads=NUM_THREADS,
        ),
    )


ENGINES = [
    whisper_small_ta_engine(),
    dolphin_engine("dolphin_small", "sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02"),
    dolphin_engine("dolphin_base", "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02"),
    omnilingual_engine(),
]


def decode_one(recognizer, wav_path: Path) -> tuple[str, float, float]:
    """Returns (text, duration_seconds, decode_seconds)."""
    audio, sample_rate = sf.read(str(wav_path), dtype="float32", always_2d=True)
    audio = audio[:, 0]  # mono
    duration = len(audio) / sample_rate

    t0 = time.time()
    stream = recognizer.create_stream()
    stream.accept_waveform(sample_rate, audio)
    recognizer.decode_stream(stream)
    decode_seconds = time.time() - t0

    return stream.result.text.strip(), duration, decode_seconds


def main() -> None:
    wanted = set(sys.argv[1:])
    engines = [e for e in ENGINES if not wanted or e.name in wanted]
    if wanted and not engines:
        raise SystemExit(f"No engine matches {sorted(wanted)}; "
                         f"known: {[e.name for e in ENGINES]}")

    wav_files = sorted(TESTAUDIO_DIR.glob("*.wav"))
    if not wav_files:
        print(f"No WAV files found in {TESTAUDIO_DIR} — nothing to do.")
        return

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # The CSV is opened once and flushed after every row. An earlier run of this
    # script died partway through the last engine and took every timing with it,
    # because the CSV was only written at the very end. Losing an hour of decode
    # to a crash in the final file is avoidable; this is how.
    csv_file = io.open(CSV_PATH, "w", newline="", encoding="utf-8")
    writer = csv.DictWriter(csv_file, fieldnames=CSV_FIELDS)
    writer.writeheader()
    csv_file.flush()

    total = 0
    failures: list[str] = []

    try:
        for engine_spec in engines:
            print(f"=== {engine_spec.name} ===")
            engine_out_dir = OUT_DIR / engine_spec.name
            engine_out_dir.mkdir(parents=True, exist_ok=True)

            t_load0 = time.time()
            try:
                recognizer = engine_spec.build()
            except Exception:
                failures.append(f"{engine_spec.name}: failed to load")
                traceback.print_exc()
                continue
            print(f"  loaded in {time.time() - t_load0:.1f}s")

            for wav_path in wav_files:
                file_id = wav_path.stem
                try:
                    text, duration, decode_seconds = decode_one(recognizer, wav_path)
                except Exception:
                    failures.append(f"{engine_spec.name}/{file_id}: decode failed")
                    traceback.print_exc()
                    continue

                rtf = decode_seconds / duration if duration > 0 else float("nan")
                (engine_out_dir / f"{file_id}.txt").write_text(text + "\n", encoding="utf-8")
                writer.writerow(
                    {
                        "engine": engine_spec.name,
                        "file": file_id,
                        "duration_s": f"{duration:.3f}",
                        "decode_s": f"{decode_seconds:.3f}",
                        "rtf": f"{rtf:.4f}",
                        "text": text,
                    }
                )
                csv_file.flush()
                total += 1
                # The transcript itself is deliberately not printed: it is Tamil,
                # and the file is the evidence. Print only the numbers.
                print(f"  {file_id}: {duration:.2f}s audio, "
                      f"{decode_seconds:.3f}s decode, rtf={rtf:.3f}, {len(text)} chars")

            del recognizer  # free model memory before loading the next engine
    finally:
        csv_file.close()

    print(f"\nWrote {total} transcripts across {len(engines)} engine(s).")
    print(f"Per-file text: {OUT_DIR}/<engine>/<file>.txt")
    print(f"CSV: {CSV_PATH}")
    if failures:
        print(f"\n{len(failures)} FAILURE(S):")
        for f in failures:
            print(f"  {f}")
        sys.exit(1)


if __name__ == "__main__":
    main()
