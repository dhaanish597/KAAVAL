#!/usr/bin/env python3
"""Bring testdata/testaudio into the shape the build plan specifies.

Build plan §11.1 says the test audio is "16 kHz, mono, 16-bit PCM", that Gradle
copies it into `app/src/main/assets/testaudio/`, and that the Python pre-screen
reads it directly. §11.2's last row says `R01_demo_pitch` is "T01 + T02 + T03
(+ T04) concatenated (create with a script)". This is that script.

What the human actually recorded is **44.1 kHz** mono 16-bit. Two jobs follow:

1. **Downsample to 16 kHz.** This does not change the pre-screen result —
   sherpa-onnx resamples internally when you hand `accept_waveform()` the true
   sample rate, and a decode of the native 44.1 kHz file is byte-identical to a
   decode of an explicitly resampled 16 kHz one (measured, see STATUS.md). It
   matters for the **app**: `MicAudioSource` is a 16 kHz pipeline (§6.3), and
   `WavAssetAudioSource` feeds the same pipeline, so a 44.1 kHz asset played
   through it would run 2.76x slow unless every call site remembered to pass the
   real rate. Converting once here removes that whole class of bug.

   The originals are never destroyed: they move to `original_44k/`, which the
   pre-screen's non-recursive glob does not see.

2. **Build `R01_demo_pitch.wav`**, the rehearsal pitch, by concatenating
   T01-T04 with a short silence between them so the Silero VAD (§6.3) splits it
   back into four segments rather than one 21-second block.

Idempotent: running it twice is a no-op.

Usage: python tools/asr_prescreen/prepare_testaudio.py
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

REPO_ROOT = Path(__file__).resolve().parents[2]
TESTAUDIO_DIR = REPO_ROOT / "testdata" / "testaudio"
ORIGINALS_DIR = TESTAUDIO_DIR / "original_44k"

TARGET_RATE = 16_000

# §11.2: R01 is T01 + T02 + T03 (+ T04). labels.json's R01 "script" field is the
# concatenation of exactly these four, in this order.
REHEARSAL_NAME = "R01_demo_pitch"
REHEARSAL_PARTS = [
    "T01_guarantee_fd",
    "T02_lockin_liquidity",
    "T03_bundling",
    "T04_charges",
]
# Long enough for the VAD to call end-of-speech, short enough that the rehearsal
# does not drag on stage.
GAP_SECONDS = 0.6

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def read_mono(path: Path) -> tuple[np.ndarray, int]:
    audio, rate = sf.read(str(path), dtype="float32", always_2d=True)
    return audio[:, 0], rate


def to_16k(audio: np.ndarray, rate: int) -> np.ndarray:
    if rate == TARGET_RATE:
        return audio
    g = np.gcd(rate, TARGET_RATE)
    return resample_poly(audio, TARGET_RATE // g, rate // g).astype(np.float32)


def downsample_recordings() -> int:
    """Rewrite every recording at 16 kHz, preserving the original under original_44k/."""
    converted = 0
    for wav_path in sorted(TESTAUDIO_DIR.glob("*.wav")):
        if wav_path.stem == REHEARSAL_NAME:
            continue  # built from the parts below, not an original recording
        audio, rate = read_mono(wav_path)
        if rate == TARGET_RATE:
            continue

        ORIGINALS_DIR.mkdir(parents=True, exist_ok=True)
        original_copy = ORIGINALS_DIR / wav_path.name
        if not original_copy.exists():
            shutil.copy2(wav_path, original_copy)

        sf.write(str(wav_path), to_16k(audio, rate), TARGET_RATE, subtype="PCM_16")
        print(f"  {wav_path.name}: {rate} Hz -> {TARGET_RATE} Hz "
              f"(original kept at {original_copy.relative_to(REPO_ROOT)})")
        converted += 1
    return converted


def build_rehearsal() -> Path:
    """Concatenate T01-T04 into R01_demo_pitch.wav with a VAD-splittable gap."""
    gap = np.zeros(int(GAP_SECONDS * TARGET_RATE), dtype=np.float32)
    pieces: list[np.ndarray] = []

    for i, stem in enumerate(REHEARSAL_PARTS):
        part_path = TESTAUDIO_DIR / f"{stem}.wav"
        if not part_path.exists():
            raise SystemExit(f"Missing rehearsal part: {part_path}")
        audio, rate = read_mono(part_path)
        if i:
            pieces.append(gap)
        pieces.append(to_16k(audio, rate))

    out_path = TESTAUDIO_DIR / f"{REHEARSAL_NAME}.wav"
    joined = np.concatenate(pieces)
    sf.write(str(out_path), joined, TARGET_RATE, subtype="PCM_16")
    print(f"  {out_path.name}: {len(REHEARSAL_PARTS)} parts + "
          f"{len(REHEARSAL_PARTS) - 1} x {GAP_SECONDS}s gap "
          f"= {len(joined) / TARGET_RATE:.2f}s")
    return out_path


def main() -> None:
    if not TESTAUDIO_DIR.is_dir():
        raise SystemExit(f"No such directory: {TESTAUDIO_DIR}")

    print(f"Downsampling recordings to {TARGET_RATE} Hz (build plan §11.1):")
    converted = downsample_recordings()
    if not converted:
        print("  (already 16 kHz — nothing to do)")

    print(f"\nBuilding {REHEARSAL_NAME}.wav (build plan §11.2):")
    build_rehearsal()

    print("\nFinal contents of testdata/testaudio/:")
    for wav_path in sorted(TESTAUDIO_DIR.glob("*.wav")):
        info = sf.info(str(wav_path))
        print(f"  {wav_path.name:26s} {info.samplerate} Hz  ch={info.channels}  "
              f"{info.duration:6.2f}s  {info.subtype}")


if __name__ == "__main__":
    main()
