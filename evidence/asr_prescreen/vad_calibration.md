# Silero VAD calibration — VAAKKU

Settings under test (build plan §6.3): threshold 0.5, min speech 0.25 s, max speech 8.0 s, min silence 0.5 s, window 512 samples, 16000 Hz.

Audio is fed in 512-sample chunks, the same way `MicAudioSource` will feed it,
so the segmentation below is the segmentation the app gets — not the different
one you get by handing the VAD a whole file at once.

## Per file

| file | audio s | segments | speech s | speech % | segment durations s | segment RMS dBFS |
|---|---|---|---|---|---|---|
| R01_demo_pitch | 22.07 | 5 | 15.28 | 69% | 2.18, 1.89, 3.65, 4.29, 3.26 | -13.3, -13.8, -11.8, -11.9, -12.3 |
| T01_guarantee_fd | 5.34 | 2 | 4.08 | 76% | 2.18, 1.89 | -13.3, -13.8 |
| T02_lockin_liquidity | 5.39 | 1 | 4.13 | 77% | 4.13 | -12.3 |
| T03_bundling | 5.32 | 1 | 5.18 | 97% | 5.18 | -12.7 |
| T04_charges | 4.23 | 1 | 3.49 | 83% | 3.49 | -12.6 |
| T05_honest_rate | 7.43 | 1 | 7.26 | 98% | 7.26 | -12.5 |
| T06_honest_lockin | 5.09 | 1 | 4.89 | 96% | 4.89 | -13.1 |
| T07_selfcorrect | 4.25 | 1 | 4.09 | 96% | 4.09 | -12.0 |
| T08_hedge | 3.95 | 1 | 2.95 | 75% | 2.95 | -13.3 |
| T09_conditional | 5.69 | 1 | 5.53 | 97% | 5.53 | -13.7 |
| T10_english | 6.48 | 1 | 5.70 | 88% | 5.70 | -14.4 |
| T11_numbers | 5.78 | 2 | 4.30 | 74% | 2.89, 1.41 | -12.6, -13.6 |
| T12_formal_tamil | 4.57 | 1 | 3.27 | 71% | 3.27 | -13.2 |
| T13_noisy_T01 | 9.43 | 3 | 5.04 | 53% | 1.25, 1.93, 1.86 | -22.5, -13.9, -14.9 |
| T14_noisy_T02 | 12.82 | 2 | 2.64 | 21% | 0.49, 2.15 | -18.8, -13.2 |

## Distribution over every segment

- segments: **24**
- RMS dBFS min / p10 / median / p90 / max: **-22.5 / -14.9 / -13.3 / -12.0 / -11.8**
- duration s min / median / max: **0.49 / 3.10 / 7.26**

**How to read the dBFS column:** the app's energy factor must reach 1.0 at or
below the p10 above, or ordinary speech from these very recordings would be
scored as poor. `SpokenExtractor` multiplies `segmentQuality` into every
confidence, and `Thresholds.spokenStrong = 0.80` with `EXACT_QUALITY = 0.95`
means a segment scoring under 0.842 can never produce a DIFFERS card.
