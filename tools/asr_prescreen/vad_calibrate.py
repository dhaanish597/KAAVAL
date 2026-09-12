"""
vad_calibrate.py — measure what the Silero VAD actually does to OUR recordings,
so the app's VAD parameters and `segmentQuality` energy anchors are set from
evidence rather than from a plausible-sounding guess (build plan §11.3).

Why this exists
---------------
`segmentQuality` (build plan §6.3) is "mean VAD speech probability x a clipped
energy factor", and it is not cosmetic: `SpokenExtractor` sets
`confidence = matchQuality * segmentQuality`, and the reconciler compares that
against `Thresholds.spokenStrong = 0.80` before a card may ever read DIFFERS.
With `FuzzyMatcher.EXACT_QUALITY = 0.95`, a segment needs
`segmentQuality >= 0.842` for a single exact mention to be allowed to differ at
all. So an energy factor that is pessimistic by 0.2 does not "slightly lower
confidence" — it silences the product.

The two numbers this script produces:
  1. Segment counts and durations under the §6.3 VAD settings (min 0.25 s,
     max ~8 s), against the real recordings. If a 22 s rehearsal clip comes back
     as one 22 s blob or as 40 fragments, the settings are wrong.
  2. The RMS distribution, in dBFS, of the speech the VAD actually emits. The
     energy anchors in the app's SegmentQuality must sit BELOW this, or normal
     speech gets marked poor.

What it cannot measure
----------------------
The Python binding exposes only `VadModel.is_speech(samples) -> bool`; the raw
per-window probability is Kotlin-only (`Vad.compute(FloatArray): Float`). For a
segment the VAD itself emitted, `is_speech` is true by construction, so the
"mean speech probability" half of segmentQuality cannot be calibrated here. It
is measured on the phone by the bake-off screen instead.

Level caveat: the human recorded these on a phone recorder, which very likely
applied its own gain control. `MicAudioSource` uses AudioRecord with
VOICE_RECOGNITION. The levels can differ, so treat the anchors chosen here as a
starting point to re-measure on-device, not as final.

Usage:  python tools/asr_prescreen/vad_calibrate.py
Writes: evidence/asr_prescreen/vad_calibration.md
"""

from __future__ import annotations

import math
import statistics
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx

REPO_ROOT = Path(__file__).resolve().parents[2]
AUDIO_DIR = REPO_ROOT / "testdata" / "testaudio"
VAD_MODEL = REPO_ROOT / "models" / "silero_vad.onnx"
OUT_FILE = REPO_ROOT / "evidence" / "asr_prescreen" / "vad_calibration.md"

# Build plan §6.3: "speech segments (min 0.25 s, max ~8 s)".
MIN_SPEECH_S = 0.25
MAX_SPEECH_S = 8.0
# Silero's own defaults for the other two. min_silence 0.5 s is what decides
# whether "எட்டு பர்சன்ட் ... ரிட்டர்ன்" stays one segment or splits across the
# pause in the middle; splitting it would put the number and its anchor word in
# different segments, and SpokenExtractor only looks within one segment.
THRESHOLD = 0.5
MIN_SILENCE_S = 0.5
WINDOW_SIZE = 512
SAMPLE_RATE = 16_000


def read_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path), "rb") as w:
        rate = w.getframerate()
        frames = w.readframes(w.getnframes())
        channels = w.getnchannels()
    audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    if channels > 1:
        audio = audio.reshape(-1, channels).mean(axis=1)
    return audio, rate


def dbfs(samples: np.ndarray) -> float:
    """RMS in dBFS. Silence returns -inf, which formats as -inf, not as 0."""
    if samples.size == 0:
        return float("-inf")
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    if rms <= 0.0:
        return float("-inf")
    return 20.0 * math.log10(rms)


def main() -> None:
    if not VAD_MODEL.exists():
        raise SystemExit(f"VAD model not found: {VAD_MODEL}")

    config = sherpa_onnx.VadModelConfig(
        silero_vad=sherpa_onnx.SileroVadModelConfig(
            model=str(VAD_MODEL),
            threshold=THRESHOLD,
            min_silence_duration=MIN_SILENCE_S,
            min_speech_duration=MIN_SPEECH_S,
            window_size=WINDOW_SIZE,
            max_speech_duration=MAX_SPEECH_S,
        ),
        sample_rate=SAMPLE_RATE,
        num_threads=1,
        provider="cpu",
    )

    wavs = sorted(AUDIO_DIR.glob("*.wav"))
    if not wavs:
        raise SystemExit(f"No .wav files in {AUDIO_DIR}")

    per_file: list[dict] = []
    all_seg_dbfs: list[float] = []
    all_seg_durations: list[float] = []

    for wav_path in wavs:
        audio, rate = read_wav(wav_path)
        if rate != SAMPLE_RATE:
            raise SystemExit(
                f"{wav_path.name} is {rate} Hz. The VAD is configured for {SAMPLE_RATE} Hz "
                f"and does NOT resample. Run prepare_testaudio.py first."
            )

        # A fresh detector per file: state must not leak between recordings.
        vad = sherpa_onnx.VoiceActivityDetector(config, buffer_size_in_seconds=60)

        segments: list[tuple[float, float, float]] = []  # start_s, duration_s, dbfs

        def drain() -> None:
            while not vad.empty():
                seg = vad.front
                samples = np.asarray(seg.samples, dtype=np.float32)
                segments.append(
                    (seg.start / SAMPLE_RATE, samples.size / SAMPLE_RATE, dbfs(samples))
                )
                vad.pop()

        # Feed in mic-sized chunks so the segmentation matches what the app will
        # see. Handing the VAD the whole file at once is not the same code path.
        for i in range(0, audio.size, WINDOW_SIZE):
            vad.accept_waveform(audio[i : i + WINDOW_SIZE])
            drain()
        vad.flush()
        drain()

        speech_s = sum(d for _, d, _ in segments)
        per_file.append(
            {
                "file": wav_path.stem,
                "audio_s": audio.size / SAMPLE_RATE,
                "segments": segments,
                "speech_s": speech_s,
                "whole_file_dbfs": dbfs(audio),
            }
        )
        all_seg_dbfs.extend(d for _, _, d in segments if math.isfinite(d))
        all_seg_durations.extend(d for _, d, _ in segments)

    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with OUT_FILE.open("w", encoding="utf-8") as out:
        w = out.write
        w("# Silero VAD calibration — VAAKKU\n\n")
        w(
            f"Settings under test (build plan §6.3): threshold {THRESHOLD}, "
            f"min speech {MIN_SPEECH_S} s, max speech {MAX_SPEECH_S} s, "
            f"min silence {MIN_SILENCE_S} s, window {WINDOW_SIZE} samples, "
            f"{SAMPLE_RATE} Hz.\n\n"
        )
        w(
            "Audio is fed in %d-sample chunks, the same way `MicAudioSource` will feed it,\n"
            "so the segmentation below is the segmentation the app gets — not the different\n"
            "one you get by handing the VAD a whole file at once.\n\n" % WINDOW_SIZE
        )

        w("## Per file\n\n")
        w("| file | audio s | segments | speech s | speech % | segment durations s | segment RMS dBFS |\n")
        w("|---|---|---|---|---|---|---|\n")
        for row in per_file:
            durs = ", ".join(f"{d:.2f}" for _, d, _ in row["segments"]) or "—"
            dbs = ", ".join(f"{d:.1f}" for _, _, d in row["segments"]) or "—"
            pct = 100.0 * row["speech_s"] / row["audio_s"] if row["audio_s"] else 0.0
            w(
                f"| {row['file']} | {row['audio_s']:.2f} | {len(row['segments'])} | "
                f"{row['speech_s']:.2f} | {pct:.0f}% | {durs} | {dbs} |\n"
            )
        w("\n")

        w("## Distribution over every segment\n\n")
        if all_seg_dbfs:
            srt = sorted(all_seg_dbfs)
            w(f"- segments: **{len(all_seg_dbfs)}**\n")
            w(f"- RMS dBFS min / p10 / median / p90 / max: "
              f"**{srt[0]:.1f} / {srt[len(srt)//10]:.1f} / {statistics.median(srt):.1f} / "
              f"{srt[(9*len(srt))//10]:.1f} / {srt[-1]:.1f}**\n")
        if all_seg_durations:
            srtd = sorted(all_seg_durations)
            w(f"- duration s min / median / max: "
              f"**{srtd[0]:.2f} / {statistics.median(srtd):.2f} / {srtd[-1]:.2f}**\n")
        w("\n")
        w(
            "**How to read the dBFS column:** the app's energy factor must reach 1.0 at or\n"
            "below the p10 above, or ordinary speech from these very recordings would be\n"
            "scored as poor. `SpokenExtractor` multiplies `segmentQuality` into every\n"
            "confidence, and `Thresholds.spokenStrong = 0.80` with `EXACT_QUALITY = 0.95`\n"
            "means a segment scoring under 0.842 can never produce a DIFFERS card.\n"
        )

    print(f"vad_calibrate: wrote {OUT_FILE}")
    if all_seg_dbfs:
        srt = sorted(all_seg_dbfs)
        print(f"  {len(all_seg_dbfs)} segments, RMS dBFS p10={srt[len(srt)//10]:.1f} "
              f"median={statistics.median(srt):.1f} min={srt[0]:.1f}")


if __name__ == "__main__":
    main()
