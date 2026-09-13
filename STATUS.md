# STATUS — VAAKKU

Current light: GREEN · Current phase: **P4 (NPU) has run on the phone and is proven** ·
Hour: H1–H6

**What is real as of 2026-09-13:** the microphone genuinely opens on the phone (logcat
proves `silero_vad.onnx` loads and `AudioRecord` starts), every P5 screen renders in Tamil,
the session service shuts down cleanly leaving no `ServiceRecord`, and **a receipt written
by the phone now verifies in the independent Node implementation** — a real session was
ended, saved and re-saved on the iQOO 15, and `tools/packet-cli` read back both the folder
and the zip as `INTEGRITY: PASSED` / `SIGNATURE: verified` / exit 0. StrongBox is real here
(`"strongBox": true`), the head on screen equals the head in the file, and re-signing
changes exactly one field while the head stays byte-identical. **G7 is PASS.**
**The Hexagon NPU is now proven too** — `libQnnHtp.so` loaded, `BackendType : Htp(2)`,
VTCM acquired, **175 of 175 ops** placed on the DSP with no CPU fallback, on the
**ordinary scan path of a live session** at 08:19:11, not on a debug screen
(`evidence/G4_npu_logcat.txt`, full capture in `evidence/G4_npu_logcat_full.txt`). Two
benchmark runs are in `evidence/G4_mask_bench*.csv`: NPU **2.60 ms** vs CPU 35.3 ms at
inference — but only ~1.4× end to end, because ~114 ms of CPU-side scale/normalise/paint
runs whichever rung is chosen. **Never quote the 12× without the 1.4×.**
**What is still unproven:** **G3 is part-measured** — two recorded sessions have put
camera-read clauses into the ledger (06:07 IST: RETURN_RATE {4,8} ILLUSTRATIVE at 0.861,
GUARANTEE false at 0.909; 08:17 IST: six ledger rows, `evidence/G3_session_081722_receipt.json`),
so the camera→OCR→ledger path is not in doubt. The gate wants ≥4 of 5 sessions and five
clause types; LOCK_IN, LIQUIDITY and CHARGES have still never been read from a photograph —
and the 08:17 pages show why: **those clauses were never in front of the camera**, which
is a different problem from the capture-resolution cap we assumed (see G3 below). **Whether
the mask lands on a person is still untested** — it paints, and on page 5 it painted a
cable and a dark desk with no person in frame (open issue 22). P6's *failure* path — a
session that starts with a model missing — has never run (open issue 21).

Red Light ruling: **unknown** — no organizer statement recorded yet.
Name ruling: **unknown** — displayed name is VAAKKU, changed by editing the single
string `app_name` in `app/src/main/res/values/strings.xml`. If organizers require the
registered name, change that one value to KAAVAL; nothing else in the app changes.
Originality ruling: **unknown** — the working assumption is the FINAL LOCKED SPEC's
"app logic written inside the event window"; assets and models prepared beforehand.
This assumption has not been confirmed by an organizer.

## Gates

| Gate | Status | Evidence | Time |
|---|---|---|---|
| G0 Bootstrap | **PASS** | see below | H0–H1 |
| G1 Domain | **PASS** | see below | H1–H5 |
| G2 ASR decision | **DECISION TAKEN — 2 of 3 evidence items** | `evidence/asr_prescreen/`, `evidence/G2_asr_bakeoff.csv`; live-mic scorecard still needs a human | H5– |
| G3 OCR | **PART-MEASURED — 2 sessions, 2 of 5 clause types** | `evidence/G3_partial_session_060746_receipt.json`, `evidence/G3_session_081722_receipt.json` + `_pages/` — camera→OCR→ledger proven twice. Needs 2 more sessions and the other three clause types, which have not yet been photographed. | H6– |
| G4 NPU | **PROVEN ON THE PHONE — 2 of 3 evidence items** | `evidence/G4_npu_logcat.txt` (Htp(2), VTCM, 175/175 ops, live session 08:19:11) + `evidence/G4_npu_logcat_full.txt`; `evidence/G4_mask_bench.csv` + `_firstrun.csv` (NPU 2.60 ms vs CPU 35.3 ms inference). Missing: screenshot of the latency label. | H6– |
| G5 End-to-end | **BOTH HALVES HAVE NOW RUN ON THE PHONE** | `evidence/P5_*.png`, `evidence/P6_*.png`; Setup→mic→scan→end→receipt→save. The camera half reached `page_1.jpg` (2448 × 3264) and ML Kit read it offline. Still unproven: a *clause* extracted from a real prop page (that is G3). | H6– |
| G6 Go/No-Go | NOT STARTED | — | — |
| G7 Receipt + Office Kit | **PASS — verified from a real device export** | `evidence/P6_packet_verify.txt` — a session saved on the phone, read back by `tools/packet-cli` as `INTEGRITY: PASSED` / `SIGNATURE: verified` / StrongBox **yes**, from both the folder and the zip; head on screen == head in file | H6– |
| G8 Freeze | NOT STARTED | — | — |

### G0 evidence checklist (§13)

| Item | Status | Path |
|---|---|---|
| `:app:assembleDebug` log tail | **done** | `evidence/G0_build.txt` |
| `scripts/check_manifest.sh` — no INTERNET | **done** | `evidence/G0_manifest_check.txt` |
| Install success | **done** | `adb install -r` → `Success`; md5 of the installed `base.apk` matches the local APK |
| Setup screen screenshot | **done** | `evidence/G0_setup.png` |
| Rung-0 probe export | **done** | `evidence/G0_rung0_probe.txt`, `evidence/G0_rung0_probe.png` |

Build was green on a clean `:app:assembleDebug` (40 tasks) with Gradle 9.3.1 / AGP
8.13.1 / Kotlin 2.3.0 on a JDK 21 daemon at Java 11 target. `checkBannedWords` is
clean over 20 files, with zero Kotlin warnings. The manifest guard passes against both
the merged manifest and the APK, and `evidence/G0_manifest_check.txt` records the
manifest-merger REJECTED lines proving the two `tools:node="remove"` declarations are
load-bearing rather than passing vacuously. Debug APK is 51 MB.

### G1 evidence (§5, §13 P1)

`:domain` built test-first, one commit per deliverable (`git log` P1.1–P1.11), touching
only `domain/**`, `testdata/testaudio/labels.json`, and this file, per the P1 prompt's
scope. A clean `./gradlew clean :domain:test :domain:fixtureReport :domain:evalTranscripts
checkBannedWords` is green.

| Item | Status | Path / number |
|---|---|---|
| `:domain:test` | **PASS** | **277 tests, 0 failures**, 13 test classes |
| Schema guard (§5.9) | **PASS** | `SchemaGuardTest` — 18 tests; scans every main class actually compiled into `app.vaakku.domain` (>= 20 found, not a vacuous pass) |
| Fixtures (§5.10) | **PASS** | 26 fixtures (10 adversarial A01–A10, 6 demo-path D01–D06, 4 honest-agent H01–H04, 6 normalization N01–N06) — `domain/src/test/resources/fixtures/` |
| `fixtureReport` | **PASS** | `evidence/fixture_report.md` — **26 fixtures, 45 assertions, DIFFERS precision = 1.00 (TP=12, FP=0), DIFFERS recall = 1.00 (TP=12, FN=0), 0 mismatches** |
| `evalTranscripts` | **wired, honestly empty** | `evidence/asr_slot_accuracy.md` — no `evidence/asr_prescreen/` transcripts exist yet (P2 produces them); the real computation path is exercised today by `LabelsTest` against `testdata/testaudio/labels.json` and was separately smoke-tested with synthetic transcripts (2/2 and 0/1 slot accuracy, as expected) then removed so no invented evidence is committed |
| `checkBannedWords` | **PASS** | 45 files scanned, 0 findings (after rewording two of my own KDoc comments that quoted the banned-word list while explaining the rule — same pattern STATUS.md decision "checkBannedWords scans comments too" already found in P0) |
| `labels.json` (§11.2) | **PASS** | `testdata/testaudio/labels.json`, 15 rows; every script's expected claims verified against the real `SpokenExtractor` by `LabelsTest` |

**Spec ambiguities resolved in P1** (each also documented at its declaration site in code):

1. `Observation` gained one field beyond §5.1's suggested shape: `ambiguous: ReasonCode?`
   (nullable, default null), so `SpokenExtractor` can flag `NEGATION_AMBIGUOUS` /
   `NORMALIZATION_AMBIGUOUS` at extraction time for the reconciler's decision-table
   step 3 to read directly, per §5.1's own "adjust names but keep semantics."
2. §5.3 names one `hedge` lexicon group; split into `hedge_up_to` / `hedge_illustrative`
   / `hedge_generic` so `SpokenExtractor` can assign the right `RateQualifier` instead
   of one undifferentiated bucket.
3. The "-அரை" half-compound suffix (எட்டரை → 8.5) and Indian-numbering compound
   thousands (இருபதாயிரம் → 20000) have no safe general sandhi algorithm — listing
   them as explicit lexicon data (`half_compounds_ta`, `compound_numbers_ta`) beats
   guessing, per CLAUDE.md #2.
4. Tokenizer splits on hyphens (does not glue "lock-in" into one token) — the
   labels.json T09 script contains "Guaranteed-ஆ" (English loanword + Tamil
   interrogative-particle suffix), and gluing the hyphen would push that word 2 edits
   from "guaranteed," over the §5.3 distance-1 fuzzy budget, silently dropping the
   GUARANTEE anchor. Multi-word lexicon idioms ("lock in") are matched by joining
   adjacent tokens with a space at query time instead.
5. `Normalizer.parsePercent` / `parseDurationMonths` always resolve the FIRST marker
   in whatever token window they're given — correct for their own single-value
   contract, but wrong when `SpokenExtractor` needs a SPECIFIC, already-known anchor
   (two "percent"s or two "வருஷம்"s in one segment). `Normalizer.numberNear` is public
   so the extractor resolves each anchor at its own position directly, and
   `Normalizer.classifyDurationKind` splits the lock-in/liquidity/term lexical check
   out from number-parsing for the same reason (§5.10 fixtures A02/T02/T05 exercise
   this).
6. Within one segment, the LAST occurrence of a claim's anchor wins (self-correction,
   A2/T09), with hedge/negation/conditional read from a window LOCAL to that anchor —
   not the whole segment — so an earlier mention's modifier never bleeds onto a later,
   corrected one.
7. Same-segment anaphora: a negated "surrender" with no duration of its own inherits
   the segment's own LOCK_IN months (T06: "Lock-in அஞ்சு வருஷம். அதுக்கு முன்னாடி
   surrender value கிடையாது."). Scoped to same-segment, same-mention-count-1 cases —
   documented as a narrow rule, not a general guess.
8. §5.7's LIQUIDITY compare rule names "surrender value OR lock-in months" as the
   written-side `n`; implemented as: use LIQUIDITY's own written `surrenderNilBeforeMonths`
   first, fall back to a written LOCK_IN's months only if LIQUIDITY has none — but this
   fallback is only reached after LIQUIDITY's own NOT_IN_DOCUMENT check (rule 6) has
   already passed, so a document truly silent on LIQUIDITY still reads NOT_IN_DOCUMENT,
   never a LOCK_IN-borrowed comparison.
9. §8.3's value-phrase table is not fully orthogonal to the six claim types: added a
   plain `v_guaranteed` key (same family as `v_guaranteed_pct`) for GUARANTEE=true with
   no same-utterance rate, and reused `v_illustrative_pcts` for every `RateQualifier`
   since a qualifier word is never itself shown on a card.
10. `CopyBuilder` returns `null` for every `DeltaState` except DIFFERS/NOT_IN_DOCUMENT,
    so "MATCHES/PENDING/UNCERTAIN are silent" (CLAUDE.md #2) is enforced at the one
    place a card gets built, not left to a UI layer to remember later.

**One real bug the fixtures caught, not assumed away:** `extractRate` fires on any
percent marker regardless of surrounding claim context. An honest-agent fixture's
CHARGES sentence originally stated the charge amount ("ஐந்து percent"), which
spuriously produced a SECOND, LATER `RETURN_RATE` observation that overwrote the
correct one via latest-wins, turning an expected MATCHES into a false DIFFERS. Fixed
in the fixture (CHARGES comparison is presence-only per §5.7, so the percent was
never needed there) rather than patched over in the pipeline — recorded here because
it is a real, narrow scoping gap in `extractRate` worth knowing about in P2/P5, not a
fixture-authoring slip to quietly forget.

### G2 evidence — part 1 of 3: laptop ASR pre-screen (§11.3 item 1, §13 P2)

The human delivered the 14 recordings (open issue 10 is closed). `tools/asr_prescreen/`
now holds two scripts, and both have been run:

| Item | Status | Path / number |
|---|---|---|
| Test audio in repo | **done** | `testdata/testaudio/*.wav` — 14 human recordings + `R01_demo_pitch.wav` built from T01–T04 |
| Pre-screen transcripts | **done** | `evidence/asr_prescreen/<engine>/<file>.txt` — **60 files** (4 engines × 15 clips), UTF-8 |
| Pre-screen timings | **done** | `evidence/asr_prescreen/prescreen_results.csv` — engine, file, duration_s, decode_s, rtf, text |
| `:domain:evalTranscripts` | **PASS, with real data** | `evidence/asr_slot_accuracy.md` — ranked summary + per-slot "expected → actual" |
| sherpa-onnx API read, not guessed | **done** | `evidence/sherpa_api_1.13.8.txt` — `javap` over the AAR's `classes.jar` |
| `:domain:test` | **PASS** | **280 tests, 0 failures** (277 + 3 new `SlotCheckerTest` cases) |
| `fixtureReport` | **PASS** | unchanged: 26 fixtures, DIFFERS precision 1.00, recall 1.00 |
| `checkBannedWords` | **PASS** | 0 findings |

**Slot accuracy — the number the §13 P2 decision rule is written against.**
Each transcript goes through the real `SpokenExtractor`; a slot counts only if the
extracted VALUE equals the `labels.json` value exactly.

| engine | slots correct | slot accuracy | laptop RTF (aggregate / median / worst) |
|---|---|---|---|
| `whisper_small_ta` | 11 / 28 | **39%** | 0.535 / 0.537 / 0.748 |
| `omnilingual_300m` | 5 / 28 | 18% | 0.131 / 0.126 / 0.147 |
| `dolphin_base` | 3 / 28 | 11% | 0.024 / 0.022 / 0.028 |
| `dolphin_small` | 0 / 28 | 0% | 0.053 / 0.051 / 0.064 |

Laptop RTF is **indicative only** — the §13 gate's "phone RTF ≤ 0.5" is a phone number
and is still unmeasured. It is recorded because the ordering (whisper ~20× slower than
dolphin_base) will survive the move to the phone even if the absolute values do not.

**The headline: no engine reaches the 70% threshold §13 P2 sets.** That is not a
failure to work around; §13 P2 states the consequence itself — *"If no engine gets ≥ 70%
slot accuracy on clean clips, the demo uses the rehearsal WAV as primary and live mic as
a 'try it' moment."* See decision 29.

**The safety property held under real ASR noise.** Of the 17 slots `whisper_small_ta`
missed, 16 report `(nothing extracted)` and exactly one produced a wrong value (T07,
LOCK_IN: expected 60, got 36 — the clip is a deliberate self-correction and Whisper
truncated the recording *before* the correction, so the extractor never saw it). Across
all four engines there are three wrong values in 112 scored slots; everything else is
silence. **The pipeline degrades to silence, not to false claims** (CLAUDE.md #2) — this
is the first time that has been observed against real speech rather than fixtures.

### G2 evidence — part 2 of 3: ASR in the app (§6.3, §11.3 item 2)

Commit `bfff0c4`. **Written and green on the laptop; nothing here has run on the phone
yet**, so no number in this section is a measurement — the measurements are part 3.

| Item | Status | Path / number |
|---|---|---|
| sherpa-onnx AAR on the `:app` classpath | **done** | `app/libs/sherpa-onnx-1.13.8.aar` (50 MB, gitignored), `implementation(files(...))` |
| API read from the AAR, not guessed | **done** | `evidence/sherpa_api_1.13.8.txt` (from P2 step 1) — every class and signature used here came from it |
| `AudioSource` / `MicAudioSource` / `WavAssetAudioSource` | **done** | `app/src/main/java/app/vaakku/asr/` — one interface, so the VAD and the engines cannot tell a mic from a clip |
| Silero VAD segmentation (§6.3 parameters) | **done** | `VadSegmenter.kt` — window 512, threshold 0.5, min speech 0.25 s, max speech 8.0 s, min silence 0.5 s |
| `segmentQuality` | **done** | `SegmentQuality.kt` + **10 unit tests** (`app/src/test/`), dBFS anchors taken from `evidence/asr_prescreen/vad_calibration.md` |
| `AsrEngine` interface + the five §6.3 engines | **done** | `AsrEngine.kt` (ids, required files, availability), `SherpaAsrEngine.kt` (1–4), `AndroidOnDeviceRecogniser.kt` (5) |
| Engine switching with unload | **done** | `AsrEngineHolder` closes the previous recognizer before constructing the next; the bake-off holds exactly one engine at a time |
| `numThreads` setting | **done** | 1 / 2 / 4 / 6 on both Dev screens, default 4, written into every exported file |
| `AsrSegment` → `SpokenExtractor` wiring | **done** | `AsrPipeline.kt` — source → VAD → engine → `AsrSegment` → observations, per segment, nothing retained |
| Debug "Live ASR" screen | **done** | `app/src/debug/java/app/vaakku/dev/LiveAsrScreen.kt` |
| Bake-off screen + CSV export | **done** | `AsrBakeoffScreen.kt` → `Download/Vaakku/evidence/G2_asr_bakeoff_<stamp>.csv` |
| `:app:assembleDebug` | **PASS** | zero Kotlin warnings, G0 baseline restored (4 pre-existing `:domain` warnings fixed in the same commit) |
| `:app:testDebugUnitTest` | **PASS** | 10 tests (`SegmentQualityTest`) |
| `:domain:test` / `fixtureReport` | **PASS** | unchanged: 26 fixtures, DIFFERS precision 1.00, recall 1.00, 0 mismatches |
| `checkBannedWords` | **PASS** | 59 files scanned, 0 findings |
| `scripts/check_manifest.sh` | **PASS** | no INTERNET, no ACCESS_NETWORK_STATE, in both the merged manifest and the APK |
| Release APK carries no answer key | **verified** | `app/build/intermediates/assets/release/` contains `R01_demo_pitch.wav` only — no `T*.wav`, no `labels.json` |
| `scripts/push_models.sh` run | **NOT DONE** | needs a human; the models have never been on the phone |
| `SessionService` (§6.7) | **NOT DONE** | still absent from the manifest; `check_manifest.sh` still asserts the P0 state |

**Superseded by part 3 below.** `push_models.sh` has now run, the bake-off has run on
the phone, and the default engine is set. One item remains: the live-mic scorecard.

### G2 evidence — part 3 of 3: the bake-off on the phone (§11.3 item 2, §13 P2)

Run on the phone, screen on, 4 threads, 22 clips per engine.
Evidence: **`evidence/G2_asr_bakeoff.csv`** (exported by the app, pulled with `adb`),
`evidence/G2_asr_bakeoff_summary.png`, `evidence/G2_engines_ready.png`,
`evidence/G2_push_models.txt`.

Device line from the CSV header, verbatim:
`vivo I2501 (I2501), SoC=QTI SM8850, rom=PD2505CF_EX_A_16.0.24.1.W30, sdk=36`.

| Engine | Slot accuracy (T01–T14) | Aggregate RTF | Worst RTF | Inside the 0.50 budget? |
|---|---|---|---|---|
| `SHERPA_WHISPER_TA` | **0.227** (5/22) | 0.324 | 0.381 | yes |
| `SHERPA_OMNI_300M` | 0.182 (4/22) | 0.106 | 0.109 | yes |
| `SHERPA_DOLPHIN_SMALL` | 0.000 (0/22) | 0.036 | 0.042 | yes |
| `SHERPA_DOLPHIN_BASE` | 0.000 (0/22) | 0.017 | 0.020 | yes |

Two independent runs agreed to within RTF rounding (Whisper 0.354 then 0.324; the
accuracies were identical both times), so these are reproducible numbers, not one lucky
pass.

**§11.5 check:** every engine is comfortably inside the ASR decode RTF budget of 0.50.
The budget is not the constraint here — accuracy is.

| Item | Status | Path / number |
|---|---|---|
| `scripts/push_models.sh` run | **done** | `evidence/G2_push_models.txt` — 21/21 files, 1.1 GB |
| All four engines report ready on the phone | **done** | `evidence/G2_engines_ready.png` |
| Bake-off CSV | **done** | `evidence/G2_asr_bakeoff.csv` (22 clips × 4 engines) |
| Human live-mic scorecard | **NOT DONE** | needs a teammate speaking T01–T04 at 1 m — see the Red Light list |

### G3 evidence — P3 OCR (§6.4, §5.6, §11.2)

Commits `70f8ba5`, `e113d78`, `c8e2ff7`, `ee8e3af`, `a706688`, `1026250`, `b7b7c85`, `d4513de`.
Built with three subagents under review; every task was reviewed and four fix rounds were
run. **One session has now reached the ledger from the camera** — found on the phone
2026-09-13 08:05 IST while checking whether an install would disturb anything, not
recorded at the time it happened. See the measurement block below the table.

| Item | Status | Path / number |
|---|---|---|
| Real prop document read and confronted | **done** | `testdata/prop/_Document.pdf` (10 pages); `RealPropDocumentTest` drives `WrittenExtractor` from the verbatim text of pages 5–6 |
| Five G3 clauses extracted from the real text | **done** | RATE {4,8} ILLUSTRATIVE · GUARANTEE false · LOCK_IN 60 · LIQUIDITY nil-before 60 · CHARGES 5% |
| BUNDLING confirmed absent | **done** | whole-document grep for `voluntary\|loan\|mandatory\|independent` finds nothing → NOT_IN_DOCUMENT is measured, not assumed |
| ML Kit bundled Latin recognizer → `OcrLine` | **done** | `app/src/main/java/app/vaakku/ocr/` — `MlKitTextRecognizer`, `OcrLineMapper` |
| Per-line OCR confidence | **REAL, not the 1.0 default** | `Text.Line.getConfidence()` returns a primitive `float` — verified by `javap` on the cached AAR and by bytecode (`aload_0; getfield zzb:F; freturn`), twice, independently |
| CameraX Preview + ImageCapture + ImageAnalysis stub | **done** | `DocumentCamera.kt`; the analyzer reads nothing and closes every frame — it is P4's seat |
| Clause list after each scan | **done** | `DocumentScanScreen.kt` — a reading of the document, carrying no state language |
| `DocumentScanCompleted` on "Done scanning" | **done** | multi-page sessions accumulate first |
| Evidence images + crops | **done** | `files/sessions/<sessionId>/`, crops q80 ≤ 800 px, `cropFile` filled app-side |
| P4 privacy-mask hook | **superseded by P4** | was `PrivacyMask.applyOrPassThrough`, a pass-through; now a real masker — see the G4 section above |
| `:domain:test` | **PASS** | 303 tests |
| `:domain:fixtureReport` | **PASS** | 26 fixtures, precision=1.00, recall=1.00, 0 mismatches |
| `checkBannedWords` | **PASS** | 66 files, 0 findings |
| `:app:testDebugUnitTest` | **PASS** | 23 tests incl. 8 new `SessionEvidenceTest` crop-clamp cases |
| `:app:assembleDebug` | **PASS** | — |
| `scripts/check_manifest.sh` | **PASS** | no INTERNET, no ACCESS_NETWORK_STATE, in merged manifest and APK; `SessionService` still absent |
| **5 scan sessions on the phone** | **2 of 5** | `session_2026-09-13_060746` (4 scans, written only) and `session_2026-09-13_081722` (7 scans, 5 pages, **first session where speech and document met**) |
| `evidence/G3_clauses.png` | **NOT DONE** | needs a human — no screenshot was taken of either session that worked |

**What G3 still requires:** the gate is "expected clauses extracted in ≥4 of 5 scans".
Two sessions have now produced clauses from a photograph, and the second one closed the
loop end to end: Tamil speech → claim, photograph → clause, reconciler → a state. What is
still missing is three more sessions and **LOCK_IN, LIQUIDITY and CHARGES, none of which
has ever been read from a photograph**. Those three are on page 6 and in the charges
table, so they are also the ones most exposed to the capture cap (decision 49) — if they
fail while RETURN_RATE and GUARANTEE keep succeeding, read the Red Light note about
`MAX_LONG_EDGE` before touching any regex.

**But the 081722 session says the cap is probably not the problem.** Its five pages were
pulled to `evidence/G3_session_081722_pages/` and looked at. Pages 3 and 4 are both
section 3–4 of the document (Eligibility, Your Policy at a Glance) — page 4 is upright,
sharp and completely legible, and it yielded nothing because **there is no lock-in,
surrender or charge clause printed on it**. Page 3 is the same content photographed at
90°. Page 5 is mostly desk. So the three missing clause types have not failed extraction;
**they have not yet been photographed**. The next session should start from the page that
carries them, not from page 1.

**MEASUREMENT — G3, first full session, phone 2026-09-13 08:17:22–08:20:23 IST**
Session `session_2026-09-13_081722`, 3 min 1 s, 7 `document_scan_completed`, 5 pages,
3 `spoken_observed`, 9 `written_observed`. Receipt
`evidence/G3_session_081722_receipt.json` (19,613 B, head `e2532e94239d692f…`).

| Ledger row | State | Reason | What was behind it |
|---|---|---|---|
| RETURN_RATE | **MATCHES** | VALUE_IN_SCENARIOS | spoken `8% ASSERTED` @0.763 (`உறுதியான எட்டு சதவீத வருமான`) vs written `{4,8} ILLUSTRATIVE` @0.885 |
| GUARANTEE | **UNCERTAIN** | SPOKEN_LOW_CONF | spoken `guaranteed=true` @**0.492** vs 6 written observations all `guaranteed=false`, best @0.900 |
| LOCK_IN | PENDING | NO_SPOKEN | nothing said, nothing on the pages photographed |
| LIQUIDITY | PENDING | NO_SPOKEN | " |
| BUNDLING | PENDING | NO_SPOKEN | " |
| CHARGES | PENDING | NO_SPOKEN | " |

Written observations came from page 1 (1 GUARANTEE) and page 2 (5 GUARANTEE,
3 RETURN_RATE) at 0.770–0.900 confidence. Pages 3, 4 and 5 produced none.

**Both of those states are correct, and the second one is the product working.**
RETURN_RATE → MATCHES on a spoken *asserted* 8% against a written *illustrative* 8% looks
wrong until you read the build plan: line 289 (case A4) and line 486 both specify exactly
this — "RETURN_RATE: MATCHES (in scenarios); GUARANTEE judged separately". The number was
in the illustrated set; the asserted-vs-illustrative question belongs to GUARANTEE, and
`compareRate` ignoring the qualifier is the specification, not an oversight.

And GUARANTEE is where it would have been caught: spoken "guaranteed" against six written
"not guaranteed" is the DIFFERS this whole app exists to show. It came out **UNCERTAIN**
instead, because ASR returned 0.492 on `து எப்டி மாதிரிதான் கேரண்ட` — a fragment that has
lost the word it needed. That is CLAUDE.md #2 working exactly as written: the honest
answer to a half-heard sentence is silence, not an accusation. It is also the sharpest
demonstration yet that **the demo stands or falls on ASR confidence**, not on the
reconciler. Open issue 27.


**MEASUREMENT — G3 partial, found on the phone 2026-09-13 08:05 IST**
Session `session_2026-09-13_060746`, started 2026-09-13 00:37 UTC, 4 `document_scan_completed`
events, 7 `written_observed` observations. Receipt saved to
`evidence/G3_partial_session_060746_receipt.json` (14,352 B, head `f2bb614c5d039acd`).

| Clause type | Best confidence | The line as OCR read it |
|---|---|---|
| RETURN_RATE | 0.861 | `Returns are NOT guaranteed. The 4% and 8% rates are illustrative only.` |
| GUARANTEE | 0.909 | `Non-Guaranteed Benefits` |
| LOCK_IN | — | never read |
| LIQUIDITY | — | never read |
| CHARGES | — | never read |

Both values are `{4,8} ILLUSTRATIVE` and `guaranteed=false` — the same values
`RealPropDocumentTest` asserts from the document's text, now arrived at from a photograph
instead. All six ledger rows were still PENDING at the end, because nothing was spoken
in that session; PENDING is silent by design (CLAUDE.md #1), so this is correct and not
a missing result.

### G4 evidence — P4 privacy masker + NPU (§6.5, §11.5)

Commits `e180d9a` (P4.1 masker), `646916e` (P4.2 benchmark + latency label).
**The NPU is proven.** The Hexagon DSP ran this app's mask model on the phone on
2026-09-13, twice: on the Dev-menu benchmark at 08:13–08:14 and — the one that
matters — on the **ordinary scan path of a live session at 08:19:11**. Two of G4's
three evidence items are in hand; the screenshot is not.

| Item | Status | Path / number |
|---|---|---|
| Model + Qualcomm v81 runtime packaged | **done** | `assets/npu/selfie_multiclass_256x256.tflite` (16,371,837 B) and 12 `.so` files in `lib/arm64-v8a/` — measured from the built APK |
| `extractNativeLibs="true"` in the merged manifest | **verified** | the QNN libraries are `dlopen`ed by path, so they must be on the filesystem, not mapped inside the APK |
| Mask arithmetic, pure Kotlin | **done** | `npu/MaskMath.kt` — argmax over 6 channels, separable dilation, nearest-neighbour index mapping |
| Mask arithmetic tests | **PASS** | `MaskMathTest` — 24 tests asserting *exact* masked-pixel sets, because a transposed or channel-planar mask gets every count and coverage figure right |
| Ladder NPU → GPU → CPU, one rung per request | **done** | `npu/PersonMasker.kt` — `Options` takes a *set* and resolves internally, so asking for two at once would produce a working model and no honest label |
| Three-outcome mask result | **done** | `ocr/PrivacyMask.kt` — `Masked` / `Unmasked` / `Withheld`; see decision 74 |
| Benchmark screen, 50× per rung | **done** | `dev/MaskBenchmarkScreen.kt` — 5 discarded warm-up runs, nearest-rank median and p90, CSV to `Download/` |
| Latency label on the scan sheet | **done** | `ScanSheet.kt` — `mask 7.9 ms · NPU` form, `totalMs`, shown only after a page that ran |
| `:domain:test` | **PASS** | 346 tests |
| `:domain:fixtureReport` | **PASS** | 26 fixtures, precision=1.00, recall=1.00, 0 mismatches |
| `checkBannedWords` | **PASS** | 100 files, 0 findings |
| `:app:testDebugUnitTest` | **PASS** | 75 tests |
| `:app:assembleDebug` | **PASS** | APK 133,032,153 B |
| `scripts/check_manifest.sh` | **PASS** | still no INTERNET and no ACCESS_NETWORK_STATE with the Qualcomm runtime packaged |
| **Benchmark CSV (NPU/GPU/CPU)** | **DONE** | `evidence/G4_mask_bench.csv` (run 2) and `evidence/G4_mask_bench_firstrun.csv` (run 1), 50 runs per rung, both rungs of both files measured |
| **Logcat excerpt proving NPU dispatch** | **DONE** | `evidence/G4_npu_logcat.txt` (curated, annotated) over `evidence/G4_npu_logcat_full.txt` (4,056 lines, unedited) |
| **Screenshot of the latency label** | **NOT DONE** | needs a human — the one thing G4 still lacks |

**MEASUREMENT — G4 benchmark, run on the phone 2026-09-13 08:13 and 08:14 IST**
Synthetic 3000×4000 page, 50 timed runs per rung after 5 discarded warm-ups, on
I2501 / QTI SM8850 / Android 16. Nearest-rank median and p90, in ms.

| Rung | inference median | inference p90 | total median | total p90 |
|---|---|---|---|---|
| NPU | **2.59** | 2.87 | 116.8 (run 1) / 159.3 (run 2) | 118.6 / 162.0 |
| GPU | 10.55 / 11.77 | 10.72 / 11.96 | 143.0 / 166.8 | 145.2 / 170.3 |
| CPU | 30.06 / 34.69 | 34.35 / 36.59 | 165.1 / 193.0 | 190.3 / 197.8 |

NPU inference is **4.1× faster than GPU and 11.6× faster than CPU** (run 1; 4.6× and
13.4× in run 2). §11.5 budgets 10 ms for `model.run`; the NPU comes in at 2.59 ms.

**Two things in that table are worth more than the headline.**

*First, the two runs disagree, and only in `total_ms`.* Inference is flat across both
runs (NPU 2.597 → 2.585) while total rises on every rung at once (NPU 116.8 → 159.3,
GPU 143.0 → 166.8, CPU 165.1 → 193.0). A uniform slowdown that spares the accelerator
is the CPU-side work — scaling a 3000×4000 page, normalising it, painting it — getting
slower, which is what thermal throttling looks like 35 seconds into back-to-back runs.
Run 1 is the number to quote; run 2 is the number to expect on demo day, after the
phone has been working.

*Second, the accelerator is not what dominates the wall clock.* Whichever rung runs,
~114 ms of scale-normalise-paint happens on the CPU. NPU is 12× faster than CPU at
inference and about **1.4× faster end to end**. Both numbers are true; only the second
one is what a person holding the phone feels. Do not quote the 12× without it.

**MEASUREMENT — NPU dispatch proven, live session 2026-09-13 08:19:11 IST**
From `evidence/G4_npu_logcat.txt`, every line verbatim from the phone:

- `BackendType : Htp(2)` — Hexagon Tensor Processor, asked for by name
- `Loading qnn shared library from "libQnnHtp.so"` → `QnnBackend_create done successfully`
- `QnnDevice_create done. device = 0x1. status 0x0`
- `htpPerfInfrastructureCreatePowerConfigId` — a call that exists on no other backend
- `Setting libnative architecture to v85 (requested arch is v81)` · `STAT: soc_type=SM8850`
- `VTCM: total_sz=8388608` — Hexagon's tightly-coupled vector memory; CPU and GPU have none
- `Partitioned subgraph<0>, selected 175 ops, from a total of 175 ops` — **no CPU fallback**
- `1 compiler plugins were applied successfully: Qualcomm compiler plugin (ver 0.1.0)`
- `graph qnn_partition_0 is loaded 1` → `Mask model loaded on NPU`

This licenses the word "NPU" under CLAUDE.md #8, and it does so from the **live scan
path** rather than from a debug screen — the app a buyer would use put the mask model
on the Hexagon DSP.

**A cost the demo has to plan for: `STAT: prepare_ms=1543`.** The QNN graph is compiled
on the device, once per process, the first time the masker is built — 1.5 seconds, and
it lands on the buyer's **first scan**, inside the tap. (This is what the 86 MB
`libQnnHtpPrepare.so` is for; the resulting context binary is 10,452,992 B.) At 08:19:11
a human waited through it without knowing what it was. Either warm the masker when the
session screen opens, or accept a 1.5 s first scan and never demo the first scan cold.
Open issue 26.

**The size this costs.** The Qualcomm runtime is 119,595,560 B uncompressed across 12
`.so` files, of which `libQnnHtpPrepare.so` alone is 86,301,344 B — that is the on-device
JIT compiler, and the §6.5 sample's `android_jit` path needs it. The debug APK is
133,032,153 B. This is a real cost and the alternative (AOT-compiled context binaries)
was not available in the timebox. If the APK size becomes a problem at submission, the
NPU rung is the thing to drop, and the app keeps working on GPU with an honest label.

**A deviation from §6.5, stated rather than glossed.** §6.5 describes the model running
"on every camera analysis frame" with the person "masked in the live preview overlay".
This implementation masks at **capture** time only; there is no live preview overlay. What
the receipt guarantee needs is that no unmasked file is written, and §6.4's ordering (OCR
on the in-memory capture, mask before the write) is what delivers that — a preview overlay
would be a second, cosmetic implementation of the same idea. It also means the ImageAnalysis
stub in `DocumentCamera` is still a stub. The screenshot G4 asks for will show the latency
label under the viewfinder, not a hatched overlay.

**The mask paints, and it is not always right.** `page_5.jpg` of session
`session_2026-09-13_081722` has several regions filled with `FILL_COLOR` (`0xFFFAF7F0`,
the paper tone) with the hard pixelated edges of a 256×256 mask upscaled to 2448×3264.
There is **no person in that frame** — the painted regions sit over a cable and a dark
desk. The argmax in `MaskMath` is not the suspect: it asks only "did a non-background
channel win", which is the formulation that survives a channel reordering. What this
shows is the *model* calling dark clutter "not background" on a frame it was never meant
to see. Harmless here (it covers desk, not text) but it is the first real evidence about
mask behaviour, and it points the other way from the risk in open issue 22: the mask
over-paints rather than under-paints. Still unanswered: whether it covers an actual
person. Open issue 22 stands.


### P5 evidence — session runtime + session UI (§6.6, §6.7)


Commits `a8f669d` (P5.2 runtime + microphone service) and `866da5b` (P5.3 session UI).
Verified on the phone 2026-09-13 02:30–02:57 IST.

| Item | Status | Path / number |
|---|---|---|
| Setup screen, Tamil | **done** | `evidence/P5_setup.png` — permissions Granted, Offline check On, `✈ இணைப்பு இல்லை ✓` |
| Session screen, listening | **done** | `evidence/P5_session_listening.png` — `கேட்கிறது…`, `பேச்சு 0 · ஆவணம் 0` |
| Scan sheet | **done** | `evidence/P5_scan_sheet.png` — preview bound, both buttons, clause list empty |
| Details ledger, all six claims | **done** | `evidence/P5_details.png` — every row `—` (silent) with the not-scanned wording |
| Session ended | **done** | `evidence/P5_session_ended.png` — `அமர்வு முடிந்தது` / `வேறுபாடு எதுவும் காட்டப்படவில்லை.` |
| **Microphone really opens** | **PROVEN in logcat** | `sherpa-onnx … silero_vad.onnx` loaded, then `AudioRecord: set(): inputSource 6, sampleRate 16000` → `start(189): return status 0` |
| Service teardown | **PROVEN** | after Close, `dumpsys activity services app.vaakku` → **0 ServiceRecords** |
| Navigation loop | **PROVEN** | Setup → Session → ended → Close → Setup (`அமர்வு தயாரிப்பு`), by uiautomator dump |
| `:domain:test` + `fixtureReport` + `checkBannedWords` + `:app:testDebugUnitTest` | **PASS** | 26 fixtures, precision=1.00, recall=1.00, 0 mismatches; 80 files scanned, 0 findings |
| `scripts/check_manifest.sh` | **PASS** | no INTERNET / ACCESS_NETWORK_STATE in merged manifest or APK; `SessionService` declared, `foregroundServiceType=microphone`, not exported |
| **Delta Card seen with a real card on it** | **NOT DONE** | needs speech + a scan in one session — the card path is proven only by `ReconcilerTest` and `CopyBuilder`, never by eye |
| **Three-finger debug overlay** | **NOT DONE** | SELinux on this ROM denies `sendevent` to `/dev/input/event6`, so multi-touch cannot be synthesized from adb. Needs three real fingers. |

**Screencap artifact, not a bug.** In `P5_scan_sheet.png` a black band appears over the two
caption lines under the camera preview. It is a capture artifact of how this ROM composites
the CameraX TextureView: the same single `Text` composable renders light-on-dark for its
first line and dark-on-light for its later lines, which cannot happen in real rendering. The
uiautomator dump settles it — the preview ends at y=1478 and the captions are at y=1508 and
y=1691, so there is no overlap. Anything under a camera preview will look like this in a
screenshot on this phone; check bounds, not pixels.

### G7 evidence — receipt chain + packet CLI (§7.1, §7.2, §7.4)

Commit `d664f29`. Run on the laptop 2026-09-13; nothing here needed the phone, and nothing
here proves the phone. Full output in `evidence/G7_receipt_integrity.txt`.

| Item | Status | Path / number |
|---|---|---|
| Canonical JSON + hash chain (`:domain`) | **done** | `domain/src/main/kotlin/app/vaakku/domain/receipt/` — every leaf a string, `h_{i-1}` joined as 64 hex chars |
| Deterministic fixture receipt | **done** | `evidence/receipt_fixture/receipt.json` — 7 events, all 6 claim types, head `dcb92a12…ece53` |
| **Chain reproduced by a second implementation** | **PROVEN** | `INTEGRITY: PASSED` from `tools/packet-cli`, which shares no code with the Kotlin — all 9 hashes agree |
| **Tamper detected, and named** | **PROVEN** | `receipt_tampered.json` (8% → 9% in one spoken claim *and* its ledger row) → `INTEGRITY: FAILED (event 2 of 7 does not match h2)`, exit 2 |
| §7.2 signature verified across runtimes | **PROVEN** | a real `keytool` EC P-256 key signs in the JVM, verifies in Node → `SIGNATURE: verified`; `scripts/receipt_fixture_signed.sh` |
| Broken signature is a failure | **PROVEN** | one flipped DER bit → `INTEGRITY: FAILED`, `SIGNATURE: FAILED`, exit 2, with the chain itself still intact |
| Input shapes: folder, zip, nested zip, single file | **done** | all four read; crops embed when present, and the packet says which image is missing when absent |
| Packet rendered, read by eye | **done** | `evidence/receipt_fixture/packet.pdf` — Tamil correctly shaped, violet on the DIFFERS row only, 3 rows with LOCK_IN/LIQUIDITY/CHARGES **silent** |
| No internal field can reach paper | **enforced, and it fired** | `assertNothingInternal` walks the built model; it caught `signature.reason` during this work (§7.4's verification reason, colliding with the ledger's `ReasonCode`) |
| `:domain:test` + `fixtureReport` + `checkBannedWords` | **PASS** | 26 fixtures, 45 assertions, precision 1.00, recall 1.00, 0 mismatches |
| **A receipt written by the phone** | **NOT DONE** | §7.2 Keystore, §7.3 export and the §6.6 screen that calls them are now written (`9081ee3`) and compile, but nothing below the laptop fixture has executed. See the P6 table. |
| **StrongBox / attestation on real hardware** | **NOT DONE** | the fixture reports `strongBox: false` because a JDK key is not in secure hardware. Honest by construction (CLAUDE.md #8). |
| **Office Kit half of this gate** | **NOT DONE** | the human has never moved a packet with it |

The signature check has a limit the packet states rather than hides: a verified signature
proves the key in the attached certificate signed the head. It does **not** prove that key
is in the phone's secure hardware — that needs the chain walked to Google's attestation
root, and this tool is offline and does not carry it. So `strongBox` is printed as "reported
by the phone", not as something verified here.

### P6 evidence — signing, export and the Receipt screen (§7.2, §7.3, §6.6 screen 5)

Commits `c0568a3` (signing + export), `9081ee3` (the screen), `262a36d` (the topics
count), `0d4d842` (the viewfinder) and `8e4c41b` (the zip line + plural). **Proven on
the phone 2026-09-13 06:20–06:46 IST**: a session was ended, saved, re-saved, and both
the folder and the zip were read back by `tools/packet-cli`.

| Item | Status | Path / number |
|---|---|---|
| §7.2 Keystore signer | **PASS (device)** | `ReceiptSigner.kt` — EC P-256, `SHA256withECDSA` over the head's 64 ASCII hex characters |
| §7.3 MediaStore export | **PASS (device)** | `Download/Vaakku/session_2026-09-13_062044/` + `session_2026-09-13_062044.zip` |
| §6.6 screen 5 | **PASS (device, both languages)** | `evidence/P6_11_receipt_0of6.png`, `P6_12_receipt_saved.png`, `P6_13_receipt_resaved.png` |
| **The exported folder read back by `tools/packet-cli`** | **PASS** | `evidence/P6_packet_verify.txt` — `INTEGRITY: PASSED`, `SIGNATURE: verified`, exit 0 |
| **The exported zip read back by `tools/packet-cli`** | **PASS** | same file — both shapes verify, identical head |
| **StrongBox actually measured** | **PASS — this phone has one** | `SIGNATURE: verified (StrongBox reported by the phone: yes)`; `"strongBox": true` in `evidence/P6_receipt_sample.json`, so it is in the record and not only in the CLI's report |
| The head on screen is the head in the file | **PASS (device)** | screen showed `a7a3 d7c6 6e8c bd2f` **before** Save; file head is `a7a3d7c66e8cbd2f06498336595adaf6a1e3510dea32aa15e7bc1fd5e8038104` |
| Re-signing cannot move the head — decision 64 | **PASS (measured)** | saved twice; structural diff of the two receipts is **exactly one field**, `/signature/signature` (the ECDSA per-signature nonce). Head and every other field byte-identical. |
| Re-export replaces rather than accumulating | **PASS (device)** | same two files, timestamps 06:27 → 06:32, no `(1)` copies in `Download/Vaakku/` |
| Packet PDF renders from a device export | **PASS** | `--out` produced a 61 KB PDF; "0 row(s)" because all six rows were PENDING, which the CLI correctly omits |
| The receipt carries no banned word | **PASS** | checked against the §2.4 list; `entries` carry `state`/`reason` only, `deviceModel` is a model name, the attestation chain holds the package name and a challenge — no IMEI, no serial |
| Export allowlist — CLAUDE.md #4's second lock | **PASS (JVM test)** | `ReceiptExportTest` seeds `segment.wav`, `session.pcm`, `page_1.png`, `pages_1.jpg`, `transcript.txt` and a `page_dir.jpg` *directory*; export yields exactly `["page_1.jpg"]` |
| "Saved" means the receipt saved | **fixed in review** | decision 68 — `receiptWritten` was `fileCount > 0` |
| Topics count counts topics | **fixed, then confirmed on device** | decision 69 — read `6 / 6` on an empty session, now reads `0 / 6` (`evidence/P6_11_receipt_0of6.png`) |
| The zip is named on screen | **fixed on device** | decision 71 — `Result.zip` was computed and dropped, so "2 files saved" left a third file unmentioned |
| "1 file saved", not "1 files saved" | **fixed on device** | decision 72 — `<plurals>`, both forms read back out of the APK with `aapt2 dump resources` |
| Page ordering is numeric, not lexical | **PASS (JVM test)** | `page_1, page_2, page_10, page_11` |
| Duration + short-head formatters | **PASS (JVM test)** | `ReceiptScreenFormatTest` |
| App unit tests | **PASS** | 51 across 7 classes, `skipped="0"` |
| Gate | **PASS** | `:domain:test`, `fixtureReport` 26 fixtures / 45 assertions / precision 1.00 / recall 1.00, `checkBannedWords`, `check_manifest.sh` **PASS** |
| **Page images in the export are masked only if a rung loads, and none has been tried** | **KNOWN GAP** | open issue 19 — the masker is built (P4) but has never run on the phone |
| **A failed session's receipt** | **NOT DONE** | the failure path at the top of the screen has not been exercised |

#### The viewfinder now frames what the camera saves (commit `0d4d842`)

Found by measuring `evidence/P6_05_scan_sheet.png`, fixed, re-measured in
`evidence/P6_10_scan_clipped.png`.

| | before | after |
|---|---|---|
| preview box | 1290 × 1125 px | 1069 × 1425 px |
| camera surface | 1290 × 1720 px, y 150…1870 | 1069 × 1425 px, y 447…1871 |
| overflow past the box | **~298 px, over the aim text and the CLAUDE.md #8 mask note** | **none** — stops 1 px inside |
| surface ratio | 0.75 (3:4) | 0.7502 |
| `page_1.jpg` as saved | 2448 × 3264 px = 0.7500 | unchanged |

The box now carries the stream's own ratio, so the border is an honest viewfinder:
what it frames is what lands in `page_<n>.jpg`, to 2 parts in 10,000. Preview pixels
vary 2…4 rather than a flat 0, which is a live surface looking at a dark desk and not
an unrendered one.

## Decisions log


1. **Version catalog is `gradle/libs.versions.toml`.** Every version comes from
   `handoff/versions_from_scratch.txt` (the prompt's `docs/versions_from_scratch.txt`
   does not exist; the file is in `handoff/`). The one addition not covered there is
   JUnit 5 (`junit-jupiter` 5.11.4 + `junit-platform-launcher`), needed because §4
   requires `:domain` with JUnit 5 tests. Gradle 9 will not run JUnit 5 without the
   launcher on `testRuntimeOnly`.
2. **Configuration cache is disabled** in `gradle.properties`. The guard tasks must
   actually re-run and re-read sources at each gate; a cached green result would be
   worse than no result.
3. **ML Kit is declared at P0** even though OCR arrives in P3. Declaring it now makes
   `check_manifest.sh` a real test: ML Kit contributes `INTERNET` and
   `ACCESS_NETWORK_STATE` through manifest merging, so the guard proves the
   `tools:node="remove"` lines work. Adding it later would leave that untested.
4. **`fixtureReport` is an honest placeholder.** It writes
   `evidence/fixture_report.md` stating that no fixtures exist yet and that precision
   on DIFFERS is not yet measured. P1 builds the real confusion matrix. No number is
   invented (CLAUDE.md #7, #8).
5. **Fonts were downloaded from official upstreams, not extracted from a bundle.**
   See Open issues — `docs/kaaval-fonts.zip` does not exist on this machine.
6. **The Dev menu is an activity in `app/src/debug/`,** reached from MainActivity by
   component name behind a `BuildConfig.DEBUG` guard. It is absent from release
   builds rather than hidden.
7. **`checkBannedWords` also scans `app/src/debug/`.** The debug source set is what
   runs on the phone during the event, so it follows the same language rules.
8. **`push_models.sh` skips files whose remote size already matches.** A full model
   set is several hundred megabytes; re-pushing it on every iteration would cost
   minutes per loop. Size rather than hash, because hashing over adb costs about what
   the push would.
9. **A `<queries>` element for `android.speech.RecognitionService` is in the
   manifest.** This is a correctness fix, not a convenience. `isRecognitionAvailable()`
   and `isOnDeviceRecognitionAvailable()` both call `queryIntentServices` internally,
   and package-visibility filtering blocks that on targetSdk 30+. Without the element
   the Rung-0 probe would report "no recognizer available" on a phone that has one,
   and we would mis-plan the entire ASR rung. It grants visibility of speech services
   only — no network capability, no effect on the offline guarantee.
10. **The Rung-0 probe queries the default recognizer *and* the on-device recognizer
    separately, and reports both.** These are frequently different services with
    different language packs — on a vivo ROM the default may be Jovi while the
    on-device service is Google's, and engine 5 (`AndroidOnDevice`) is the latter.
    Reading only the default could have caused us to abandon a working offline Tamil
    path. `Rung0Report` therefore holds a `probes: List<RecognizerProbe>`, each with
    its own `LanguageSupport` for `ta-IN` and `en-IN`.
11. **The v2 prototype is the design source of truth, and it supersedes the palette
    in `docs/VAAKKU_UI_DESIGN_SPEC.md` §3.1 and the P0 fallback.** The human supplied
    `KAAVAL Prototype.html` and the design spec; both are now in `docs/`. v2 moves to
    neutral grey paper (`#F4F3F1`, not the green-tinted `#EDEFE9`), near-black ink
    (`#111111`, not blue-black `#17242E`), and adds a brand amber the v1 markdown does
    not contain. The full token set — Day and Night — is transcribed in
    `ui/theme/Color.kt` with the source `:root` blocks named. This closes open issue 2.
12. **Amber is permitted; it does not weaken CLAUDE.md #9.** #9 bans colour-coding
    *states*. Counting actual usage in the prototype rather than assuming: amber
    appears 4 times (wordmark, two offline chips, session strip) and never on a claim;
    violet appears 25 times and is always a delta. The two accents therefore carry
    disjoint meanings — `Brand` = "this app, and it is offline"; `Stamp` = "these two
    copies differ" — and that separation is what must be protected. The reasoning is
    written into `ui/theme/Color.kt` so it cannot be lost. **No state is colour-coded
    anywhere, and there is no red/amber/green ramp.**
13. **Visual design from v2, copy from the build plan.** The prototype is written
    entirely in English, including its `ta:` fields, and its wordmark reads KAAVAL. The
    build plan and CLAUDE.md require Tamil, and the build plan wins over every other
    document. So layout, tokens, spacing and structure come from v2; every user-facing
    string stays Tamil-first with an English gloss, marked TAMIL-REVIEW.
14. **Three typographic voices are wired as named roles, not typefaces.** `VoiceSpeech`
    (Noto Sans Tamil) for spoken claims and UI, `VoiceDocument` (Noto Serif Tamil) for
    quoted written clauses, `VoiceRecord` (IBM Plex Mono) for tabular values. Spoken
    sans against written serif is the product encoded typographically — on a Delta Card
    you can tell which half is which without reading it. Named by role because getting
    them backwards inverts the metaphor silently.
15. **Night is implemented but `MainActivity` passes `night = false` explicitly.** Day
    reads as paper and photographs better under stage lighting, and the demo must not
    flip because the phone happened to be in dark mode. `VaakkuTheme(night: Boolean)`
    takes the flag as a parameter rather than reading `isSystemInDarkTheme()` directly.
16. **`Domain` (six document kinds) is orthogonal to the six claim types.** INSURANCE /
    LOAN / RENTAL / PURCHASE / JOB / SERVICE decide which claim types are *armed* and
    what the camera expects; RETURN_RATE / GUARANTEE / LOCK_IN / LIQUIDITY / BUNDLING /
    CHARGES are what gets compared. A rental agreement and an insurance illustration can
    both carry a CHARGES claim. The enum sits in `ui/` at P0 because it carries no rules
    yet; when the arming table is written it belongs in `:domain` as pure data.
17. **The Setup screen keeps the two build-plan §6.6 rows the prototype frame omits.**
    Frame 07 has no ASR or accelerator row; §6.6 requires both, and the build plan wins.
    Both are worded to satisfy CLAUDE.md #8: the ASR row reports *the recognizer the ROM
    ships* and says in as many words that the app has loaded nothing yet, and the
    accelerator row reads "not measured" and will keep reading that until logcat proves
    a dispatch. Neither invents a name. `Rung0Probe.quickAvailability()` was added for
    this — a cheap synchronous metadata read, not the full probe, which creates
    recognizers and is far too heavy for a screen that re-reads state every second.
18. **The §6.6 debug override is attached to its own line, not to the Start button.**
    Development needs the radio on (adb), so the airplane-mode gate must be bypassable
    in debug and nowhere else. Whether a *disabled* Material button lets a long-press
    reach an ancestor has changed between Compose versions; a dev escape hatch that
    silently stopped working would cost debugging time at the worst possible moment. The
    flag is `allowOverride = BuildConfig.DEBUG`, so the override state cannot become
    true in a release build.
19. **Window insets are applied explicitly on every full-screen surface.** targetSdk 36
    means Android draws the window edge-to-edge with no opt-in, so the status bar and
    the gesture pill sit *over* the content. The first on-device screenshot caught it:
    the Tamil title அமர்வு தயாரிப்பு was sheared off at the top. This matters more in
    Tamil than in English — headline glyphs carry marks above the x-height, so a
    clipped line is unreadable rather than merely untidy, and design spec §3.4's
    "reserve 30% vertical overflow" is the same rule stated for type. Both
    `SetupScreen` and `Rung0ProbeScreen` now read `WindowInsets.safeDrawing` and add
    the top/bottom insets to their own padding. Any new full-screen composable must do
    the same; verify it with a screenshot, not by eye in a preview.
20. **Setup screen's language row is unlocked (human's direct request, supersedes
    the "locked" part of decision 13).** Two selectable options now: `TAMIL_ENGLISH`
    (v1 default — Tamil-first strings with an English gloss under the ones that had
    one) and `ENGLISH_ONLY` (every `localized()` string on the screen switches to
    its English companion; the disclosure block collapses to one English line
    instead of Tamil + gloss). This is a **display** setting only — it does not
    touch ASR. The product still listens for code-switched Tamil–English speech
    regardless of this flag (build plan §1); a buyer's own reading preference is a
    different axis from what the seller says out loud. Selection persists across
    restarts in a small `SharedPreferences` file (`AppLanguage.kt`), read once into
    an `AppLanguageState` held at the activity root and provided via
    `LocalAppLanguage`. New English strings were written by the assistant, not by
    Dhaanish — unlike the Tamil, which carries a `TAMIL-REVIEW` obligation, these
    are plain functional English and should still get a human once-over before the
    demo, same spirit as any other unreviewed copy. Verified on-device (M2 below):
    every string on the Setup screen switches, the choice survives a force-stop,
    and switching back to Tamil + English is clean.
21. **Material `primary` is INK, not the stamp violet.** `VaakkuTheme` previously
    mapped `primary = colors.stamp`, which meant every unstyled `Button`, `Switch`,
    `Slider` and text cursor in the app rendered violet — the Dev menu's Refresh button
    already did. CLAUDE.md #9 reserves violet for a DIFFERS card, so that mapping was a
    standing trap that would have violated the rule the first time anyone dropped a
    plain `Button` on a user-facing screen. `primary` is now `colors.ink`; the stamp is
    reachable only as `VaakkuTheme.colors.stamp`, so a Delta Card has to ask for it by
    name. Verified on-device: the Refresh button samples `#222222`.
22. **P1 (`:domain`) was built test-first, one commit per §13 P1 deliverable
    (1 through 11), never batched.** Each commit's `./gradlew :domain:test` was green
    before moving to the next deliverable — the ten spec ambiguities resolved along the
    way, and the one real bug the fixtures caught, are written up in full under "G1
    evidence" above rather than repeated here.
23. **`evidence/fixture_report.md` and `evidence/asr_slot_accuracy.md` were updated even
    though the P1 prompt's edit scope names only `domain/**`, `testdata/testaudio/labels.json`
    and this file.** CLAUDE.md #7 ("Evidence or it didn't happen") and rule "Always do"
    item 4 ("Record evidence paths in STATUS.md") require it, and both files are Gradle
    task *output*, not hand-edited product code — no `app/**` or root Gradle file was
    touched to produce them.
24. **The human's recordings were 44.1 kHz; they are now 16 kHz, and the 44.1 kHz
    originals are untouched in git.** §11.1 specifies 16 kHz mono 16-bit PCM.
    `tools/asr_prescreen/prepare_testaudio.py` resamples in place (polyphase, `scipy`)
    and keeps the originals at `testdata/testaudio/original_44k/` (gitignored — commit
    `3ee21a8` already holds them byte-exact at their original paths, so that folder is a
    convenience, not the backup). **The pre-screen transcripts were not invalidated by
    this**: measured, not assumed — sherpa-onnx logs `Creating a resampler: in_sample_rate:
    44100 output_sample_rate: 16000` and decoding at the native 44.1 kHz produced text
    *byte-identical* to an explicit resample to 16 kHz. The conversion was done anyway
    because `WavAssetAudioSource` will share `MicAudioSource`'s fixed 16 kHz pipeline
    (§6.3), where a 44.1 kHz asset would play 2.76× slow.
25. **`accept_waveform()` is always given the file's TRUE sample rate, never a
    hard-coded 16000.** Passing a false 16000 for a 44.1 kHz file does not fail — it
    silently decodes garbage (a Tamil clip came back as `つかパパかけシャ。`). This is
    written into `tools/asr_prescreen/prescreen.py` as a comment because it is the kind
    of bug that looks like "the model is bad."
26. **The models were exonerated before the engines were ranked.** A 0–39% slot accuracy
    could equally mean "these models are weak on Tamil" or "I configured them wrong," and
    those have opposite consequences. Control experiment: each model decoded its own
    author-supplied `test_wavs/`. `dolphin_base`/`dolphin_small` transcribed their Chinese
    `0.wav` correctly; `omnilingual_300m` transcribed en/de/es/fr near-perfectly. The
    configs are right; the weak Tamil output is real.
27. **`sherpa-onnx` 1.13.8 has no language parameter for the Omnilingual CTC model.**
    Read from the installed `offline_recognizer.py`:
    `from_omnilingual_asr_ctc(model, tokens, num_threads, decoding_method, debug, provider)`.
    The model therefore picks its own output script, and for Tamil audio it sometimes
    emits Gurmukhi or Kannada. Recorded as a real limitation of that engine, not worked
    around — a language hint that does not exist cannot be passed.
28. **`whisper_small_ta` truncates every clip and drops the leading character.** Not
    accepted on faith: `tail_paddings` (-1 / 2000 / 4000) and `language` ("ta" / "" /
    "en") changed nothing, and output byte-length varies per clip, ruling out a fixed
    buffer cap. It is genuine behaviour of this third-party `ippocode/indic-asr-onnx`
    export under sherpa-onnx 1.13.8, and it is the direct cause of the single wrong-value
    slot (T07). Worth re-testing on the phone, where the decode path is C++/JNI rather
    than the Python binding.
29. **The lexicon is not the bottleneck, and the fuzzy budget will not be widened to
    compensate.** The suspicion was that ASR writes English loanwords in Tamil script and
    the lexicon misses them. Checked rather than assumed: `கேரண்டி`, `பர்சென்ட்` and
    `லாக்கின்` are all already in `lexicon_ta_en.json` — P1 anticipated exactly this. The
    loss is ASR mangling words beyond the §5.3 Levenshtein-1 budget, and raising that
    budget would start matching words nobody said, which is how a false DIFFERS gets
    produced. CLAUDE.md #2 forbids that trade, so the budget stays at 1.
30. **`SlotChecker.describe()` was added so the accuracy report says WHY a slot missed.**
    A bare correct/total cannot distinguish "heard the wrong number" from "heard nothing,"
    and those have opposite fixes (§11.3: calibrate the lexicon vs change engine). It
    renders the extractor's actual output in the same vocabulary `labels.json` uses, so
    the report reads `expected 60, got 36`. Test-first; three new cases in
    `SlotCheckerTest`. This is what made decisions 28 and 29 possible to reach.
31. **`segmentQuality`'s energy anchors are measured, not chosen.** §6.3 says "mean VAD
    speech probability × a clipped energy factor" and leaves the clip points open. They
    are load-bearing: `SpokenExtractor` sets `confidence = matchQuality × segmentQuality`
    and `Reconciler` refuses DIFFERS on a single mention below `spokenStrong` (0.80), so
    with `FuzzyMatcher.EXACT_QUALITY` at 0.95 **any segment scoring under 0.842 can never
    produce a DIFFERS card** however clearly the words were said. An energy curve
    pessimistic by two tenths does not lower confidence a little — it silences the
    product. So `tools/asr_prescreen/vad_calibrate.py` ran the real Silero VAD over all
    15 recordings (`evidence/asr_prescreen/vad_calibration.md`): 24 speech segments, RMS
    dBFS min −22.5, p10 −14.9, median −13.3. Full credit starts at **−30 dBFS** (≈7 dB
    below the quietest real segment, margin for a phone-recorder's gain control versus
    raw `AudioRecord`, and for a speaker a metre away in a hall) and runs out at **−50**
    (room tone). Linear in dB, not in amplitude, because an amplitude-linear ramp puts
    every realistic speech level into the top few percent of the scale.
32. **`segmentQuality` deliberately does not try to grade transcription quality.** It
    answers "was this loud enough and speech-like enough to be worth believing" and
    nothing else. A loud, confidently-voiced segment that the recogniser mangled still
    scores high — guarding against mangled text is the lexicon's and the reconciler's
    job, and a number that pretended to do both would be trusted for a property it
    cannot see.
33. **Engine 5 is not an `AsrEngine` and takes no part in the bake-off.** It owns the
    microphone and returns text, never PCM, so it cannot be fed a WAV and cannot be
    scored against the same clips as engines 1–4. Forcing it into the interface would
    have produced a column in the evidence CSV that looked comparable and was not. It
    lives in `AndroidOnDeviceRecogniser.kt` as a separate `Flow<AsrSegment>` source, and
    the CSV header says in words why it is absent. (M1 already put it out for Tamil;
    this is about not fabricating a comparison, not about that.)
34. **Unmeasurable audio gets `segmentQuality = 0.80`, and the number is arithmetic, not
    taste.** Engine 5 never hands over samples, so there is no energy to measure. 0.80 is
    above `Thresholds.spokenMin` (0.55), so a claim heard there still enters the ledger
    and can still reach NOT_IN_DOCUMENT; and 0.80 × 0.95 = 0.76 is below `spokenStrong`
    (0.80), so **one** unmeasured mention can never on its own produce a DIFFERS card. A
    claim said twice still can, because `Reconciler` accepts `mentionCount >= 2`. This is
    CLAUDE.md #2 applied to a missing measurement rather than papered over with 1.0.
35. **The bake-off scores through `:domain`'s `SlotChecker`, not a second scorer.** The
    phone CSV and `evidence/asr_slot_accuracy.md` are meant to corroborate each other; a
    separate scoring implementation on the app side would be a second thing that can
    quietly disagree with the file it is corroborating. `AsrBakeoff` loads the same
    `labels.json` and calls the same `SlotChecker.describe`, so a slot that reads
    `expected 60, got 36` reads identically in both places.
36. **The VAD is constructed fresh per clip, and `VadSegmenter` holds two `Vad`
    instances.** Silero is recurrent: state from the end of one clip changes the
    segmentation of the next, which would make bake-off rows depend on clip order. And
    `acceptWaveform` and `compute` share that hidden state inside one instance, so
    measuring per-window speech probability with the same object that is segmenting
    corrupts the segmentation — one instance segments, one meters.
37. **Test audio is copied into assets at build time, and the answer key is debug-only.**
    `syncRehearsalAudio` puts `R01_demo_pitch.wav` into **main** assets (it ships in
    release, because §13's fallback makes the rehearsal clip the demo's primary path);
    `syncBakeoffAudio` puts `T01`–`T14` and `labels.json` into **debug** assets only.
    Copying at build time rather than committing a second copy keeps 3.4 MB of WAVs out
    of the source tree, and the split was verified against the built release assets, not
    assumed. A release APK carrying the answer key would be an accuracy claim shipped
    next to the thing it grades.
38. **Stop the audio source; do not cancel the flow.** `AsrPipeline` ends when
    `AudioSource.read` returns a negative value, and `VadSegmenter.flush()` then emits
    the sentence still sitting in the VAD's buffer. Cancelling the collecting coroutine
    also stops it, but throws that segment away — and in a sales pitch the last sentence
    is the one that closes. Both Dev screens' Stop buttons call `source.stop()`.
    Related trap, fixed before it shipped: `MicAudioSource.read` returns **0** when no
    frames are ready yet, so the loop is `if (n < 0) break; if (n == 0) continue`, not
    `if (n <= 0) break` — the obvious version ends the stream on the first quiet moment.
39. **The Live ASR screen shows text, timing, quality and claims on the same row on
    purpose.** A segment with good text and no claims is a lexicon problem; a segment
    with no text at all is an engine problem; and those have opposite fixes (§11.3).
    Showing only the transcript would make them look like the same failure.
40. **The ASR engine is `SHERPA_WHISPER_TA`, and §13's fallback applies as well.**
    This is the §13 P2 decision, taken on the measurements in G2 part 3. The rule is
    "highest slot accuracy among engines whose phone RTF is at or under 0.50; ties to
    the smaller model". All four engines are inside the RTF budget, so the rule turns
    on accuracy alone and selects Whisper at 0.227. **But 0.227 is far below the 0.70
    §13 asks for**, so §13's own fallback is in force too: *the demo runs from the
    rehearsal WAV as primary, and live mic is a "try it" moment, not the thing being
    demonstrated.* Both halves are binding. The engine is recorded in code as
    `AsrEngineId.DEFAULT` with the CSV numbers in its KDoc, so it cannot be changed
    without confronting the evidence.
41. **The bake-off keyed `labels.json` by filename while the file keys it by stem,
    and the bug printed a confident conclusion off zero data.** `AsrBakeoff.runClip`
    looked up `T01_guarantee_fd.wav`; `labels.json` — and `:domain:evalTranscripts` —
    key by `T01_guarantee_fd`. Every clip matched no label, every row scored 0/0, and
    `slotAccuracy` returned `0.0` for "nothing was scoreable", so the screen printed
    "under the 0.70 §13 asks for, so the fallback applies" — a decision, stated
    confidently, off no measurement at all. Three fixes, because the instance and the
    class of failure are different bugs: a shared `clipId()` helper; `slotAccuracy` is
    now `Double?` and renders as `not-measured`, never `0.000` (an absent measurement
    and a measured zero must not look alike — CLAUDE.md #7); and a pre-flight `check()`
    that aborts before a single model loads if any clip is unlabelled.
42. **`R01_demo_pitch` is measured for RTF but excluded from slot accuracy.** §13 names
    T01–T14. R01 carries six expected claims — more than any T clip — so letting it into
    the denominator would hand about a quarter of the score to a single recording that
    exists to be performed, not measured. It still runs, because it is the longest clip
    and therefore the most honest contribution to the RTF aggregate that §11.5 is
    actually about. `T11_numbers` scores 0/0 by design: it must produce *no* claim, so
    the table cannot show it passing. Both facts are comment lines in the CSV header.
43. **Whisper's truncation is Whisper's, not the audio's or the VAD's.** Every Whisper
    transcript stops mid-sentence, and it stops exactly where the number would be —
    `T01` gives `…கேரண்டி எட்` and ends, losing the RETURN_RATE slot. The bounded check
    that settles the cause: **`dolphin_base` decodes the same clip in full**, producing
    `…எட்டு பசன் ட்ரிட்டன்.` (eight percent return) with a closing full stop, and
    `T12_formal_tamil` comes back from Whisper as a complete sentence ending in a full
    stop. So the WAVs are intact and the segmentation is fine; the Whisper export stops
    decoding early. Not pursued further — §13 has a fallback for exactly this situation
    and the timebox is better spent on P3.
44. **The accuracy numbers are limited by lexicon coverage as much as by the engines.**
    `dolphin_base` scored 0.000 while demonstrably *hearing* the content: on T01 it
    returns `பசன் ட்ரிட்டன்` where the lexicon expects `பர்சன்ட் ரிட்டர்ன்`, and on T08
    it returns the complete "up to eight percent" phrase. The slot was lost to Tamil
    orthography, not to the microphone. This matters for P5 calibration (§11.3 item 3):
    adding real ASR misspellings to the lexicon is likely to move slot accuracy more
    than switching engines would. Recorded here rather than acted on, because changing
    the lexicon now would invalidate the bake-off the decision above rests on.

45. **§11.2's "prop document" paragraph was a hypothesis, and the real PDF differs
    from it in one structural way that matters.** The build plan described the prop as
    e.g. "Guaranteed returns: No" and "Premium allocation charge: 5% in year 1". The real
    `testdata/prop/_Document.pdf` is a 10-page benefit illustration in which (a) the §6
    sentence is **"Guaranteed Returns on premiums paid: No."** — an intervening phrase
    that broke the §5.6 GUARANTEE regex outright, now fixed; and (b) the 5% charge is a
    genuine **three-column table row** ("Premium Allocation Charge" | "Year 1" | "5% of
    Annualised Premium"), not a colon-joined sentence, so it only reads correctly if
    `RowAssembler` merges three separately-boxed OCR cells. Everything else in §11.2
    agrees, including "silent on loans" — a whole-document grep for
    `voluntary|loan|mandatory|independent` finds nothing, so BUNDLING = NOT_IN_DOCUMENT is
    confirmed against the real paper rather than assumed. The extractor was fixed; the
    expectations were not touched.
46. **No single page of the prop carries all five G3 clauses; pages 5 and 6 together do.**
    RATE, GUARANTEE, LOCK_IN, LIQUIDITY and CHARGES are spread over pages 3–6. §6.4
    explicitly allows multiple pages per scan session, so one "scan" in the G3 sense is a
    two-page session. The human has printed pages 5–6 only, which is the minimum paper
    that can satisfy the gate.
47. **`WrittenExtractor.extract()` is called once per captured page, never over a
    concatenation of pages.** `RowAssembler` groups OCR lines purely by vertical pixel
    position and has no page awareness, and every photo has its own near-(0,0) pixel
    space — so concatenating two pages' lines into one call can merge a row from page 5
    with a row from page 6, producing an observation whose text appeared on neither page
    and whose provenance crop would point at the wrong photo. Per-page extraction costs
    nothing: the reconciler already holds written observations per claim type as a list.

48. **P3 was built by three subagents under review, and the reviews found two defects that
    would have reached the demo.** The first: the §5.6 GUARANTEE regex could not read the real
    document's clearest disclosure ("Guaranteed Returns on premiums paid: No.") at all, so the
    document's own statement that returns are not guaranteed was silently invisible. The second:
    `ImageCapture` was uncapped at the sensor's maximum, ~201 MB per ARGB_8888 bitmap and ~402 MB
    peak across the rotate — an almost certain `OutOfMemoryError` on the first Scan tap, and a
    breach of both §11.5 budgets. Neither was visible from a passing build. Four fix rounds ran
    in total; every round ended with a scoped re-review that verified the fix rather than the
    claim.
49. **The capture is capped at a 4000 px long edge, overriding §6.4's "full resolution".**
    §6.4's phrase exists to contrast the OCR capture against the ~256 px `ImageAnalysis` stream —
    it means "enough to read small print", not "the sensor's maximum" — and §11.5's budgets are
    numeric where that phrase is not. The cap is applied **during** the JPEG decode via
    `inSampleSize`, not after it, because a post-decode downscale still has to allocate the frame
    the cap exists to avoid. Peak per tap: ~402 MB → ~110 MB. An A4 page filling the 3000 px short
    edge is about 360 px/inch, so 8 pt table type is about 40 px tall — **whether that is enough
    to read is a G3 measurement, not a claim.** If fine print is lost, raise `MAX_LONG_EDGE`.
50. **The written-confidence path carries real numbers, not the 1.0 the build plan allows.**
    §6.4 says to use ML Kit's line confidence "if the API exposes it in this version, else 1.0,
    and tell me which". It does: `Text.Line.getConfidence()` returns a **primitive `float`** (so
    it cannot be null and cannot crash), established twice independently by `javap` and bytecode
    inspection of the AAR actually resolved onto the classpath. This matters downstream: §5.6's
    written confidence is `min(OCR line confidences) × format plausibility`, so a blurred or
    badly-lit line can genuinely fall under `writtenMin` and go silent — which is the behaviour
    CLAUDE.md #2 asks for, and it would have been impossible with a hardcoded 1.0.
51. **`RowAssembler` now partitions by `frameId`, and the app also extracts once per page.**
    Belt and braces, deliberately. The reasoning is decision 47's; what changed is that relying
    on the caller's contract alone was judged too weak after the implementer tripped the failure
    *by accident* while drafting a test, producing a fabricated `Charges(percent=4, label="Policy
    Administration Charge")` from a page-5 cell spliced into a page-6 row. An unenforced
    convention that yields a confidently wrong observation is worse than a few lines of
    partitioning. Verified behaviour-identical for all 26 pre-existing single-frame fixtures.
52. **The privacy mask is a hook that does nothing, and the scan screen says so on every run.**
    §6.4 requires the saved page image to be masked before it is written; P4 builds the masker.
    Until then `PrivacyMask.applyOrPassThrough` returns its input unchanged and the UI states that
    framing is the only mitigation. CLAUDE.md #8 — no label may claim work that is not happening.

53. **Navigation is derived state, not a callback.** `MainActivity` shows `SetupScreen` while
    `SessionRuntime.state.phase == IDLE` and `SessionScreen` otherwise, so `SessionRuntime.clear()`
    *is* the way back. `SessionScreen` has no `onExit` parameter. One source of truth for "is there
    a session", shared with the microphone service, and no way for the UI and the mic to disagree.
    P6 inserts the Receipt screen by branching on `SessionPhase.ENDED` in the activity.

54. **The Delta Card sizes its alert text on a character-count ladder (40 / 37 / 34 sp), not by
    measuring.** `TextAutoSize` could not be verified present in the cached foundation artifact, and
    a hand-rolled measure-and-shrink loop fails by rendering *nothing* on the frame it gets wrong.
    A blank card is the worst possible outcome — it is the one fact the buyer came for. The ladder
    is deterministic and never yields empty. §6.6's floor of 34 sp is the bottom rung.

55. **Sheet headers are `heightIn(min = 56.dp)`, never a fixed height.** Found on the phone:
    `ஆவணத்தை ஸ்கேன் செய்` wraps to two lines and a fixed 56.dp clipped the second one mid-word
    (uiautomator: the title measured exactly 210 px = 56 dp). Tamil sets longer than the English
    the Material rhythm was chosen against, so this will keep happening — every header on every
    screen now grows. The constant lives in `SessionScreen.kt` as `HEADER_MIN_HEIGHT`.

56. **The empty-state card stops saying "கேட்கிறது…" once the session ends.** The mic is closed
    at that point, so the word is a false statement about the hardware (CLAUDE.md #8). The status
    line keeps the mic state (`அமர்வு முடிந்தது`) and the card carries the result
    (`வேறுபாடு எதுவும் காட்டப்படவில்லை.`). The result line is deliberately a statement about the
    *app*: a silent session must not read as clearing the person across the table, just as a card
    must not read as a charge against them.

57. **The manifest deliberately does not set `enableOnBackInvokedCallback`.** Logcat asks for it.
    Ignored on purpose: during a session, back must do *nothing* — the phone is lying on a table
    between two people and a stray edge swipe must not disturb the one line being read. Predictive
    back would play a peek animation on every such swipe before snapping back. `BackHandler` works
    identically on the legacy path, and the sheets rely on registration order (innermost open sheet
    wins) which is unchanged.

58. **`SessionEvidence.newSessionId` takes a prefix.** `session_<stamp>` for a real session,
    `scan_<stamp>` for a bench scan from the Dev menu. The receipt is built from a session folder,
    so when the folder name is all you have, the two must be tellable apart.

59. **`sendevent` is denied to the shell on this ROM, so multi-touch cannot be synthesized.**
    `/dev/input/event6` is `crw-rw---- root input` and the shell *is* in the `input` group, but
    SELinux refuses the write anyway. Consequence: the three-finger debug overlay can only be
    opened by a human hand, and any future gesture must be verified the same way. `input tap`
    and `input swipe` still work, so single-pointer UI can be driven from adb.
60. **LIQUIDITY durations are shown in months unless they divide evenly into years.**
    §8.3 gives only a years-denominated phrase for both liquidity values, and
    `ValuePhrase.forLiquidity` took that literally: it divided by 12 unconditionally, so
    integer division rendered an 18-month withdrawal cliff as "1 வருடம்" — a wrong number,
    shown at 40 sp, to someone deciding whether to sign. `forLockIn` had always had the
    correct `% 12 == 0` branch; liquidity just never got it. Fixed by adding
    `v_withdraw_after_months` and `v_surrender_nil_before_months` (CLAUDE.md #2: if the app
    cannot say it exactly it must not say it approximately). **Every liquidity duration in
    the 26 fixtures is an exact number of years, and so was every liquidity shape in
    `CopyResTest.valueShapes()` — neither suite could have caught this.** Both now carry
    non-exact-year cases, and the guard was proved by deleting one registration and
    watching `CopyResTest` fail at line 144. The months wording is shorter than the years
    wording, so `DeltaCard`'s character ladder is unaffected.

61. **`head` hashes a closing record, not just the last event — the one documented
    extension to §7.1.** As written, §7.1 chains the events and calls the final link the
    head. That leaves the session details and the final ledger — the rows a person
    actually reads — outside the chain: anybody could change `DIFFERS` to `MATCHES` in
    `entries`, or move `startedAtMs`, and the receipt would still verify, because no hash
    ever covered those bytes. So `head = SHA-256(h_n || canonical(closing))` where
    `closing` is schema + sessionId + appVersion + deviceModel + startedAtMs + endedAtMs +
    entries. The shape §7.1 and §7.2 depend on is unchanged: `hashes` is still
    `[h0 … h_n, head]`, `head` is still its last element, and §7.2 still signs `head`. The
    signature now covers the whole document except itself. `closingRecord` in
    `tools/packet-cli/lib/verify.js` uses the tool's own `schema` constant rather than the
    file's field, so a receipt whose schema line was edited cannot re-derive its own head
    under different rules.

62. **The chain is checked by a second implementation that was written from the spec, not
    ported from the first.** `HashChainTest` only ever proved that this code agrees with
    itself, which is worth nothing for §7.4, where a different language has to arrive at
    the same 64 hex characters. `tools/packet-cli/lib/canonical.js` was therefore written
    from §7.1's written rule with the Kotlin closed — a translation would have reproduced
    `CanonicalJson.kt`'s mistakes faithfully and then agreed with itself. Two choices made
    the agreement reachable at all: **every leaf in canonical JSON is a string**, so there
    is no number case to diverge on (Java prints `1.0` where JS prints `1`), and
    **`h_{i-1}` joins as its 64 hex characters** rather than raw bytes, so the join is
    self-delimiting and reproducible in one line of Node. `verifyChain` re-canonicalizes
    every event from the parsed JSON, so a disagreement about sort order, escaping or
    Tamil is a hash mismatch rather than a silent pass — there is no way to pass that
    check by accident.

63. **A signature that does not match is a FAILED record, and the packet prints times in
    IST.** Two separate fixes to the same instinct that a technically-true line is good
    enough. (a) The CLI first reported a broken signature beside `INTEGRITY: PASSED` and
    exited 0, on the reasoning that the chain really was intact. But a good chain with a
    signature that does not match is precisely what re-chaining a forged event looks like
    — the one thing the signature exists to catch — so `overallIntegrity` folds the two
    checks into the one line §7.4 prints, and the exit code follows it. A missing
    signature stays PASSED, because a receipt built on a laptop has none. (b) The packet
    rendered `startedAt` in UTC, so a session at 04:12 IST printed as `2026-09-12T22:42Z`
    under a file named `session_2026-09-13T04-12-00` — a document contradicting itself in
    front of the person least equipped to work out which line to believe. `istStamp` adds
    the offset arithmetically rather than through `Intl`: India has no daylight saving,
    and a fixed number cannot depend on which ICU data a laptop ships.

64. **The hash head shown on screen is the same object that gets exported, by
    construction rather than by care.** The Receipt screen prints a head before the buyer
    has saved anything, and the obvious way to build that — format a head for display,
    then build the receipt again at save time — gives two code paths that can drift, on
    the one value whose entire purpose is that it cannot. So `ReceiptWriter` splits into
    `build()`, which is pure and returns the `Receipt`, and `signAndExport()`, which takes
    *that object*. The screen prints its head and hands the same instance on; there is no
    way to reach the second step without having done the first. The property that makes
    this safe is in the chain itself (decision 61): `head` is computed **over** the closing
    record and the signature block is attached afterwards, so `signedWith()` cannot move
    the head. Showing it before signing is therefore not an optimistic preview — it is the
    final value.

65. **மறுபரிசீலனை disappears once the session has ended, for two independent reasons and
    either would be enough.** The product reason: re-check means "set this aside and bring
    it back the moment it is said again" (§5.7 rule 9), and nothing will be said again
    after the microphone closes — on an ended session the button is a promise the app
    cannot keep. The mechanical reason: `UserRecheck` is a reconciler event, and an event
    appended after the Receipt screen has built its receipt would change the hash head the
    buyer is looking at. This was the last remaining way decision 64's guarantee could have
    been broken, and it is closed in `DetailsSheet` by a phase check rather than by a
    disabled button, so there is nothing to tap.

66. **A session that *failed* still gets a receipt, and the failure is shown on the
    Receipt screen rather than routed around it.** `SessionService.listen()` ends the
    session from a `finally`, so a missing model file goes STARTING → `failed(...)` →
    ENDED exactly like a normal session does. Routing ENDED to the new screen therefore
    made the Session screen's failure display unreachable, and the first fix that came to
    mind — keep failed sessions on the Session screen — is wrong: a microphone that died
    in the fourth minute must not cost the buyer the record of the first three. The
    failure block is instead the first thing on the Receipt screen, above the session
    fields, with the exception verbatim in mono. Three now-unreachable ENDED branches in
    `SessionScreen` were deleted along with their four strings, verified unreferenced by
    grep across `app/src`, `domain/src` and `tools/` first.

67. **The Receipt screen shows no count of differences and no "nothing differed" line.**
    Both were written and both were removed. A total — "3 differences in this session" —
    is a finding about the person on the other side of the table, which CLAUDE.md #1
    forbids outright; and a sentence saying nothing differed is the app volunteering a
    summary of a conversation it only partly heard, which is the same mistake with the
    sign flipped (§5.7 rule 4 already says absence of a difference is not a finding). What
    the screen does show is `receipt_topics`, "N of 6" — **topics covered**, not findings —
    phrased with the denominator precisely because a bare "3" at the end of a session
    would be read as three things wrong. StrongBox is likewise absent from the screen: it
    is in the receipt JSON where the packet can qualify it as "reported by the phone", and
    on screen there is no room for that qualification, which would leave an unqualified
    hardware claim — the one thing CLAUDE.md #8 rules out.

68. **"Saved" is now a fact about `receipt.json`, not about the file count.**
    `ReceiptExport.Result.receiptWritten` was `fileCount > 0`, and `fileCount` counts
    pages and crops too — so a run where the receipt write threw and one page copy
    succeeded would have printed `%d கோப்புகள் சேமிக்கப்பட்டன` over a folder holding
    images and no record. That folder is not something `tools/packet-cli` can read at
    all: `findReceipt` looks for `receipt.json` and there would be none. Found by
    reading the field while writing the Red Light test list for it, not by a test — the
    JVM tests cannot reach `export()` because MediaStore needs a device, which is
    precisely why the honest-label logic had to be simple enough to check by eye.
    `receiptWritten` is now a constructor field set from the one write it names, and the
    zip follows the same rule: no receipt, no zip, because the zip is the artefact
    somebody hands over unopened and a zip of loose page images is not a record of
    anything. The images stay in the folder regardless, so nothing of the buyer's is
    withheld. The general lesson is the one CLAUDE.md #8 keeps restating: a label
    derived from a proxy will eventually be a lie, so derive it from the thing it
    claims.

69. **The topics field counts topics that were observed, not rows in the ledger.**
    The Receipt screen read `பேசப்பட்ட தலைப்புகள் 6 / 6` at the end of a session in
    which nothing was said and no clause was read. The field was `session.ledger.size`,
    and `Reconciler.ledger()` is `ClaimType.entries.associateWith { decide(it) }` — one
    row per type at all times — so that number is the constant 6 for every session there
    has ever been. `LedgerEntry.observed` (`spoken != null || written.isNotEmpty()`) is
    now what the screen counts. It is deliberately **not** a question about any
    `DeltaState`: an entry counts whether it matched, differed or stayed silent, because
    counting states is what would turn a coverage number into a finding about a person
    (CLAUDE.md #1). Nine tests in `ReconcilerTest` drive the count through the real
    reconciler. Same failure mode as decision 68 — a label derived from a proxy — and
    found the same way, by reading the field rather than by a test. Confirmed fixed on
    the phone: the same shape of session now reads `0 / 6`
    (`evidence/P6_11_receipt_0of6.png`).

70. **The scan viewfinder carries the camera stream's own aspect ratio.**
    The preview was `fillMaxWidth().height(300.dp)` and `PreviewView`'s default
    `FILL_CENTER` scales a 3:4 portrait stream to match the width, so ~298 px of live
    camera image painted over the aim text and over the CLAUDE.md #8 note that says the
    saved page is not masked yet. Compose layout bounds do not clip a child View's
    drawing, so `height` never contained it. Cropping the preview to fit would have been
    the worse fix: `FILL_CENTER` shows the middle ~75% of the frame while `ImageCapture`
    saves all of it and `PrivacyMask` is a pass-through until P4, so the buyer would be
    exporting a quarter of an unmasked photograph they were never shown. The box now
    carries the stream's ratio and the border is an honest viewfinder — measured against
    a saved `page_1.jpg` at 2448 × 3264 px, the two agree to 2 parts in 10,000.
    `clipToBounds` stays as the structural guard for a device that reports some other
    ratio. **If the `RATIO_4_3` in `DocumentCamera.bind` ever changes, `PREVIEW_ASPECT`
    in `ScanSheet.kt` is wrong and the frame stops being honest — change both together.**

71. **The zip is named on the Receipt screen, because it exists.**
    `ReceiptExport` writes `<sessionId>.zip` beside the folder and returns its path;
    the screen dropped it, so a buyer told "2 files saved" had three files in Downloads
    and no way to learn it there. The direction that matters: somebody who deletes the
    folder believing the record is gone would leave a complete copy of their own
    document behind (CLAUDE.md #8). It is also the copy that gets attached to an email,
    and a path nobody was told is a path nobody uses.

72. **User-facing counts use `<plurals>`.** "1 files saved." was on screen for a
    one-file session. The duration phrases already use plurals for exactly this reason
    (see the comment above `v_years`); `receipt_saved` was the one string left with a
    count and no plural form. `localizedPlural` is the plural sibling of `localized`.
    Anything new that prints a number followed by a noun goes through it.

73. **`adb pull` and `adb push` on this laptop truncate a derived destination name by
    one character.** Measured with platform-tools 36.0.1 on Windows/Git Bash:
    `adb pull <remote>/session_2026-09-13_062044.zip ./dir/` lands as
    `session_2026-09-13_062044.zi`, a directory pull lands as `…_06204`, and
    `adb push probe.txt /sdcard/x/` arrives as `probe.tx`. **File contents are
    byte-exact — only the name adb derives is wrong.** An explicit destination path is
    always correct. This is the real cause of the `prop-05.pn` / `prop-06.pn` names on
    the phone that were "fixed" with `adb shell mv`; the fix was on the wrong side of
    the cable. `scripts/push_models.sh` is unaffected because line 109 pushes to an
    explicit `${remote_path}` — which is why the model tree on the phone is intact and
    ASR works. **Rule: never let adb derive a name. Always give `pull`/`push` a full
    destination path.** Open issue 20.

74. **A failed mask withholds the page image; it never writes an unmasked one.**
    `PrivacyMask` has three outcomes, not two. `Masked` writes the masked bitmap.
    `Unmasked` writes the raw capture and happens only when *no* accelerator would load
    the model at all — the screen says plainly that no mask is running, which is exactly
    the behaviour that shipped before P4 and is therefore not a regression. `Withheld`
    happens when a masker exists but this page failed it: nothing is written, because the
    screen has been telling the human that masking is on. **The invariant is that the file
    on disk always matches what the screen said** — which is stricter than "always mask",
    since masking can be honestly unavailable. This is only affordable because §6.4 runs
    OCR on the in-memory capture *before* masking: a withheld page costs corroboration and
    never costs a clause (CLAUDE.md #2).

75. **Each accelerator rung is requested alone.** `CompiledModel.Options` accepts a *set*
    of accelerators and resolves it internally, so `Options(NPU, GPU)` returns a working
    model with no way to ask which one is underneath — precisely the case where a label
    reading "NPU" would be a guess. `PersonMasker.createOn` requests exactly one, so a rung
    either loads or throws, and the rung that loaded is the honest label. `create()` walks
    NPU → GPU → CPU over it and keeps every refusal with its LiteRT message, because "why
    is it on GPU" is the first question anyone will ask on demo day.

76. **The scan sheet's latency label shows `totalMs`, not `inferenceMs`.** Inference alone
    is the smaller, more flattering number: it omits scaling the capture to 256×256 and
    painting the mask back over a full-resolution page, which happen on the CPU whichever
    rung ran the model. The label is there to state what the person actually waited for.
    The split is in the benchmark CSV, where a reader is equipped for it. Quoting the
    component that makes the accelerator look good is the kind of number CLAUDE.md #8
    exists to prevent.

77. **The latency label is not shown after a withheld page.** It renders only when the last
    page's summary is `Ran`. Otherwise the number would describe an earlier page while the
    state line directly above it describes this one — two true statements arranged into a
    false impression.

78. **The benchmark's percentiles are nearest-rank, and every run is exported.** An
    interpolated p90 is a figure no run actually produced, and this number goes into a
    gate. The CSV carries one row per run with the summary as leading `#` lines, so the
    median can be recomputed by anyone who doubts it. Refused rungs get a `# refused` line
    with LiteRT's own words rather than being omitted.

79. **All benchmark and CSV number formatting is pinned to `Locale.ROOT`.** The default
    locale chooses the decimal separator: on a phone set to a comma-decimal locale,
    `String.format("%.2f")` writes `7,91` into a comma-separated file and silently splits
    one column into two — in the export that is supposed to *be* the evidence. The
    on-screen figures use the same formatter, so a screenshot and the CSV show the same
    characters.
80. **G4's NPU proof is taken from the live session, not from the benchmark.** Both ran
    on Hexagon, and the benchmark is the screen built to measure it — but the benchmark
    is a debug screen, and a debug screen proving its own point is the weaker claim. The
    08:19:11 lines in `evidence/G4_npu_logcat.txt` come from the ordinary scan path: a
    human tapped "Scan document" in a real session and the mask model went to the DSP.
    A second reason: the 4 MiB ring buffer had already discarded the benchmark's model
    creation by the time anything was pulled, and creation is where the backend names
    itself (`BackendType : Htp(2)`, `libQnnHtp.so`). The buffer is now at 16 MiB.
81. **Curated evidence files carry the filter that produced them and what they lack.**
    `evidence/G4_npu_logcat.txt` is 94 lines chosen out of 4,056, which is an editorial
    act on a file whose only job is to be trustworthy. It therefore states the exact
    filter, names `G4_npu_logcat_full.txt` as the unedited source beside it, marks which
    lines came from which run when timestamps jump, and ends with a "WHAT THIS DOES NOT
    PROVE" section. An excerpt that hides its own selection rule is not evidence.
82. **The empty `document_scan_completed` objects are correct and were left alone.**
    Seven of them in the 081722 receipt, each `{"event":"document_scan_completed"}` and
    nothing more. §6.4 and §7.1 define it as a bare marker that gates `NOT_IN_DOCUMENT`;
    `ReceiptJson.event` serialises exactly the one key. It fired seven times for five
    pages because the human tapped "Done scanning" repeatedly, and the reconciler sets a
    boolean, so repeats cost a line in the receipt and nothing else. The temptation was
    to put the page id and OCR confidence in it — that is issue 28, and it changes the
    schema and the hash chain, so it is not a change to make while chasing a gate.
83. **Mask coverage goes to logcat, not to the receipt.** Issue 28 offered a cheap fix and
    an honest one. The honest one — a coverage field in the receipt — is the right long-run
    answer and was rejected *for now* on timing: it changes the receipt schema and therefore
    the hash chain, and G7 is already PASS against the current schema with a verified export
    (M3). Re-opening a passed gate to add a diagnostic field, days before a demo, trades a
    proven property for a convenience. Logcat answers the question that is actually blocking
    — issue 22, does the mask land on the person — and answers it today. **The deferral is
    recorded rather than quietly dropped**: if the packet's recipient ever needs to know how
    much of a page was painted, the receipt is where it belongs and this decision should be
    revisited, not treated as settled.
84. **All three mask outcomes log, not just the successful one.** The obvious
    implementation logs coverage where coverage exists, i.e. only on `Masked`. That makes
    an absent line ambiguous between "the masker never ran", "it was withheld" and "it ran
    and painted nothing" — three states with completely different meanings, one of which
    (Withheld) means a page is missing from the evidence folder on purpose. Logging all
    three makes silence in logcat mean exactly one thing: the scan path did not execute.
85. **LiteRT 2.1.6 cannot cache the QNN context binary, and that was established by
    reading the API, not by trying.** `javap` over `litert-api-2.1.6.aar` (the method that
    produced `evidence/sherpa_api_1.13.8.txt`) shows `Environment.Option` with three
    entries and `QualcommOptions.Key` with fourteen, and no key anywhere takes a cache
    directory — `IR_JSON_DIR` and `DLC_DIR` are debug dumps. The 1543 ms JIT therefore has
    no code fix available at this dependency version, which turns issue 26 from an
    engineering task into a demo-day step. Recorded because "we could cache it" is the kind
    of plausible half-idea that gets re-proposed every session until someone writes down
    that it was checked.

## Measurements

### M1 — Rung-0 probe, run on the phone 2026-09-12 12:06 IST

Source: `evidence/G0_rung0_probe.txt`, exported by the app itself (not retyped).
Device: **vivo I2501 (iQOO 15), SoC QTI SM8850, rom PD2505CF_EX_A_16.0.24.1.W30,
Android SDK 36.**

| Question | Answer |
|---|---|
| `isRecognitionAvailable()` | **true** |
| `isOnDeviceRecognitionAvailable()` | **true** |
| recognition services visible | **2** |
| default recognizer | `com.google.android.tts/…GoogleTTSRecognitionService` |
| on-device recognizer | `com.google.android.as/…AiAiSpeechRecognitionService` |
| **ta-IN on-device** | **NOT SUPPORTED** — absent from the list of 31 |
| **en-IN on-device** | supported, `installed: (none)`, `needs_download: true` |

**The decisive line: `ta-IN` is not in the on-device recognizer's supported set.**
The full list it returned is en-US, de-DE, es-ES, fr-FR, it-IT, en-AU, en-GB, en-IE,
en-SG, ja-JP, de-AT, de-BE, de-CH, en-CA, en-IN, es-US, fr-BE, fr-CA, fr-CH, hi-IN,
id-ID, it-CH, ko-KR, pt-BR, th-TH, cmn-Hans-CN, cmn-Hant-TW, pl-PL, ru-RU, tr-TR,
vi-VN — 31 languages, including Hindi and Indian English, and **no Tamil**. Note that
`needs_download` is *false* for ta-IN: it is false because Tamil is not offered at
all, not because it is ready. This is not a "download it before the event" situation.

**Consequence (build plan §6.3 go/no-go tree): engine 5 `AndroidOnDevice` is OUT for
Tamil. Rung 1 (sherpa-onnx) carries Tamil ASR.** That was the expected outcome and is
why the models are already downloaded, but it is now measured rather than assumed.

Two caveats recorded honestly:

- The probe ran with **airplane mode ON**. The *default* (Google TTS) recognizer
  answered `ERROR_CANNOT_CHECK_SUPPORT` (14) for both languages, which is what an
  online-capable recognizer does with no network — that result is **inconclusive, not
  negative**. It does not change the conclusion: the *on-device* recognizer answered
  fully offline, and it is the one engine 5 would use.
- `online_languages` is `(none)` everywhere for the same reason. Nothing here says
  anything about online Tamil, and the product does not care.

Validation of decision 9 as a side effect: the `<queries>` element works — two
recognition services were visible. Without it this table would have read
"no recognizer available" and we would have mis-planned the ASR rung.

### M2 — Language row, run on the phone 2026-09-12 12:23–12:24 IST

Source: screenshots driven over adb, not retyped —
`evidence/language_setting_before.png` (Tamil + English default, row unlocked),
`evidence/language_setting_english.png` and `_scrolled.png` (English only, whole
screen), `evidence/language_setting_persisted_after_restart.png` (after
`am force-stop` + relaunch), `evidence/language_setting_back_to_tamil_english.png`
(switched back).

| Check | Result |
|---|---|
| Tapping "English" switches every string on the Setup screen | **yes** — title, domain grid + source line, language caption, counterparty block, permissions row, offline-check block + chip, ASR/NPU rows, background hint, Start button + blocked hint |
| Disclosure block in English mode | **one English line**, not Tamil + gloss |
| Debug-only strings (`Open Rung-0 probe`, override lines) | **unchanged** — stay English in both modes, as designed |
| Choice survives `am force-stop` + relaunch | **yes** — `SharedPreferences` round-trip confirmed |
| Switching back to "தமிழ் + English" | **clean** — no leftover English strings |

`:app:assembleDebug`, `checkBannedWords` (21 files, 0 findings), and
`scripts/check_manifest.sh` (INTERNET still absent) all re-ran green before install.

### M3 — P6 receipt saved and verified, run on the phone 2026-09-13 06:20–06:46 IST

Source: `evidence/P6_packet_verify.txt`, `evidence/P6_receipt_sample.json`,
`evidence/P6_10…P6_14*.png`. Session `session_2026-09-13_062044`, driven over adb.

| Question | Answer |
|---|---|
| Does a session end produce a receipt on the phone? | **yes** — `receipt.json`, 5 919 B |
| Does `tools/packet-cli` verify the exported **folder**? | **`INTEGRITY: PASSED` · `SIGNATURE: verified` · exit 0** |
| Does it verify the exported **zip**? | **yes, identical head** |
| Does this phone have StrongBox? | **yes** — `"strongBox": true` in the receipt itself |
| Is the head on screen the head in the file? | **yes** — `a7a3 d7c6 6e8c bd2f` shown before Save; file head `a7a3d7c6…8104` |
| Does re-signing move the head? | **no.** Saved twice; structural diff is exactly one field, `/signature/signature` (ECDSA nonce). Decision 64 measured, not argued. |
| Does a re-export accumulate `(1)` copies? | **no** — same two files, timestamps 06:27 → 06:32 |
| Does the packet PDF render from a device export? | **yes** — 61 KB; "0 row(s)" because all six rows were PENDING, which the CLI omits by design |
| Topics field on an empty session | **`0 / 6`** (was `6 / 6` — decision 69) |
| Camera preview vs saved image | box 0.7502, `page_1.jpg` 2448 × 3264 = 0.7500 — decision 70 |
| Does the Setup screen block a session while the radio is on? | **yes**, in both languages — `evidence/P6_14_setup_radio_guard.png` |
| Does ML Kit OCR run offline? | **yes** — bundled models, no network permission in the APK |

Not measured here and still open: a **failed** session's receipt, and the first-Save
latency (the tap was timed over adb, which adds round trips — the number would be a
measurement of the cable, so it is not recorded).

## Open issues

1. **`docs/kaaval-fonts.zip` does not exist anywhere on this machine.** The task
   assumed it. Sixteen static font files were downloaded instead from the official
   upstreams (`notofonts/notofonts.github.io` and `IBM/plex`), verified as valid
   distinct TTFs (unique md5, `00010000` magic), and pinned in
   `app/src/main/res/font/`. Licences are recorded in `licenses/`. **The human should
   confirm this substitution is acceptable** — it is a deviation from the written plan,
   though not from the originality rule, since fonts are assets and were not copied
   from the prototype repo.
2. ~~**`docs/VAAKKU_UI_DESIGN_SPEC_v2.md` and `docs/kaaval-tokens.css` are also
   absent.**~~ **RESOLVED.** The human supplied `KAAVAL Prototype.html` and
   `VAAKKU_UI_DESIGN_SPEC.md`. Both are now committed in `docs/`, along with
   `docs/prototype/prototype_v2_extracted.html` (the design gallery recovered from the
   self-extracting bundle, so the markup is readable without running its JavaScript).
   The fallback palette is gone; see decisions 11–14. Two things the human should still
   confirm: the prototype wordmark reads **KAAVAL**, not VAAKKU (the name ruling above
   is still unknown, and `app_name` is the single string that changes), and the
   prototype's own copy is English while the app ships Tamil.
3. **`docs/VAAKKU_FINAL_LOCKED_SPEC.md` does not exist**; the file is
   `docs/FINAL LOCKED BUILD SPECIFICATION.md`.
4. **The prompt names `docs/versions_from_scratch.txt`** and
   `docs/kaaval-fonts.zip`; both live (or would live) elsewhere. Worth fixing in the
   next prompt so the next session does not re-discover this.
5. ~~**The phone was not attached at build time.**~~ **RESOLVED.** The iQOO 15
   (`10BFBK0GN7001GJ`, model I2501) is attached over USB. Build, install, launch,
   screenshot and the Rung-0 probe all ran on it; see Measurements M1. `push_models.sh`
   has still never been exercised — models are not yet needed and P1 does not need them
   either.
6. **Four permissions beyond the six in §6.2 arrive through library manifest merging.**
   The full requested set is: `RECORD_AUDIO`, `CAMERA`, `FOREGROUND_SERVICE`,
   `FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`, `POST_NOTIFICATIONS` (all ours, per the
   plan) plus `FOREGROUND_SERVICE_DATA_SYNC` (from `com.google.ai.edge.litert:litert`),
   `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` (from `androidx.work:work-runtime`, pulled
   in by ML Kit), and the AndroidX-generated app-scoped
   `app.vaakku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **None of these grants network
   access, so the offline guarantee is intact** — but they are unplanned and visible in
   the merged manifest a reviewer may read. Each needs either a `tools:node="remove"`
   line or a one-line justification before the G6 go/no-go. Do not add the removals
   blind: check whether the LiteRT/ML Kit paths actually exercise them first (P3/P4).
   Note `evidence/G0_manifest_check.txt` lists the requested set, not the
   `android:permission` attributes that merely *guard* library components
   (`INSTALL_PACKAGES`, `DUMP`, `BIND_JOB_SERVICE`) — those are not requested by us.
7. **`en-IN` on-device speech is supported but not installed, and downloading it needs
   a network.** This is the one pre-event action with a deadline attached: the Dev menu
   has a `triggerModelDownload(ta-IN)` button, but for **en-IN**, not Tamil. Whether we
   want it at all is a real question — Rung 1 (sherpa-onnx) is planned to carry both
   halves of the code-switched stream, and a second recognizer is a second failure mode.
   Decide in P2. If the answer is yes, it must happen **before airplane mode goes on**,
   and there is no way to do it at the venue if the venue Wi-Fi is the only network.
8. **The Rung-0 probe has only been run with the radio off.** The default recognizer's
   `ERROR_CANNOT_CHECK_SUPPORT` is therefore inconclusive (see M1). Re-running it once
   with the radio on would complete the picture and cost about a minute. It would not
   change the Tamil conclusion, which came from the on-device recognizer and is already
   definitive — so this is a completeness item, not a blocker.
9. **The long-press on the screen title did not open the Dev menu on the phone.** The
   visible "Open Rung-0 probe" button (debug builds only) works and was used for all
   the evidence above, so nothing is blocked. But the long-press path is what the Red
   Light test list assumed, and injected long-presses via `adb input` did not trigger it
   at verified-correct coordinates, while an ordinary `input tap` on the same text
   worked. Unresolved whether this is a gesture-injection artefact or a real
   `pointerInput` key-stability bug — `onOpenDevMenu` is a fresh lambda on every
   composition, which restarts the gesture detector. Worth 10 minutes in P1: key the
   `pointerInput` on `Unit` and hold the callback in `rememberUpdatedState`. **Do not
   rely on the long-press during a demo until it has been confirmed by a human finger.**
   (P1 was domain-only per its own scope — this is still open for whichever phase next
   touches `SetupScreen`/`Rung0ProbeScreen`.)
10. ~~**`testdata/testaudio/` has no `.wav` files in the repo.**~~ **RESOLVED.** The
    human recorded and delivered all 14 clips (T01–T14); `R01_demo_pitch.wav` is built
    from T01–T04 by `tools/asr_prescreen/prepare_testaudio.py`. All 15 are now 16 kHz
    mono (decision 24) and all 15 have been decoded by four engines. The *second* half
    of the original HUMAN ACTION — line-by-line confirmation that each `labels.json`
    script matches what was actually said — has **not** been reported back, and the
    pre-screen gives an indirect reason to ask again: see open issue 11.
11. **One label needs a human ear before the ASR numbers can be fully trusted.** In
    `T07_selfcorrect`, `labels.json` expects `LOCK_IN = 60` (the corrected value), and
    three of four engines recovered nothing while `whisper_small_ta` and `dolphin_small`
    both returned **36**. Decision 28 explains that as Whisper truncating before the
    self-correction, and that explanation is consistent with the transcripts — but the
    other reading is that the recording says three years and the label is wrong. These
    are distinguishable in ten seconds by a human.
    === HUMAN ACTION NEEDED ===
    WHAT:   Listen to testdata/testaudio/T07_selfcorrect.wav and T06_honest_lockin.wav.
    WHY:    T07 is the only clip where any engine produced a WRONG value rather than
            silence. Either the ASR truncated the correction (expected) or labels.json
            says 60 where the recording says 36 (a bad ground truth, which would make
            every accuracy number on this page slightly wrong).
    STEPS:  1. Play T07_selfcorrect.wav to the end.
            2. Say what lock-in the speaker lands on LAST — three years or five years.
            3. Do the same for T06_honest_lockin.wav (labelled 60 months / 5 years).
    REPORT: "T07 ends on <N> years, T06 says <N> years" — two numbers is enough.
    ===========================
12. **CONFIRMED ON THE PHONE — no ASR engine reaches the §13 P2 slot-accuracy
    threshold.** The laptop pre-screen said 39% for `whisper_small_ta`; the phone
    bake-off measured **0.227** on the same engine against a stated 0.70. The phone did
    not move the numbers in our favour. §13's fallback is therefore in force (decision
    40): **the demo runs from the rehearsal WAV as primary and live mic is a "try it"
    moment.** This is not a blocker, but it is a strategy change the human must know
    before the demo is rehearsed — the pitch cannot promise live Tamil transcription.
    The default engine is now set (`AsrEngineId.DEFAULT`), so the "do not set it yet"
    condition on this issue is discharged. Decision 44 records the one lead worth
    pursuing if time allows: the losses are as much lexicon coverage as engine quality.
13. **RESOLVED — the models are on the phone, and `push_models.sh` had three bugs.**
    All 21 files (1.1 GB) are at `/sdcard/Android/data/app.vaakku/files/models/` and all
    four engines report ready (`evidence/G2_engines_ready.png`). Getting there required
    fixing the script (commit `239d529`): (a) every `adb` call needed `</dev/null`,
    because adb forwards its stdin to the device and was draining the `find` output that
    fed the `while read` loop — it pushed 2 of 21 files and exited 0; (b) a completeness
    check now compares pushed+skipped against the real file count and exits non-zero on
    a mismatch, so a partial push can never look like a success again; (c) `chmod -R 777`
    per model dir, because `adb shell mkdir` creates directories owned by `shell` with
    mode 2770 and no world-execute — the app could `stat` the directory but not traverse
    into it, so `File.isDirectory()` succeeded while `File.isFile()` on each child
    failed, and all four engines reported "missing: model.int8.onnx, tokens.txt"
    immediately after a verified 1.1 GB push.
14. **RESOLVED — `SessionService` (§6.7) is written, running and torn down cleanly.**
    It is the foreground-service host the real session screen needs; the Dev screens run
    the pipeline in a plain coroutine scope instead, which is fine for a screen you are
    looking at and wrong for a session that must survive the screen turning off. `<service
    android:name=".session.SessionService" android:foregroundServiceType="microphone"
    android:exported="false"/>` is in the manifest, `scripts/check_manifest.sh` was updated
    off the P0 assertion and now asserts the declaration positively (**RESULT: PASS**), and
    `dumpsys activity services app.vaakku` reports **0 ServiceRecords** after Close — the
    service does not linger. Logcat proves the mic really opens: `AudioRecord: set():
    inputSource 6, sampleRate 16000` → `start(189): return status 0`.
15. **Two MCP servers in this environment need authorization and could not be used:**
    `plugin:catalyst-by-zoho:catalyst-by-zoho` and `plugin:supabase:supabase`. Neither is
    needed by VAAKKU, which is offline by design and has no backend — recorded only so
    nobody spends time wondering why those tools are inert. Authorize via claude.ai
    connector settings, or `claude mcp` / `/mcp` in an interactive session.
16. **The three grievance destinations printed on every packet are UNVERIFIED.** §7.4's
    "where you can take this" section lists the insurer's grievance officer, Bima Bharosa
    (`bimabharosa.irdai.gov.in`) and the Insurance Ombudsman (`cioins.co.in`). The
    institution names are stable facts of the Indian insurance system; **the two URLs were
    written from knowledge and have not been checked**, and this tool is offline so it
    cannot check them. They are marked `UNVERIFIED` in `DESTINATIONS` in
    `tools/packet-cli/lib/render.js` — one place to fix. §7.4 assigns this to the human.
    A wrong address on a document somebody carries to make a complaint is worse than no
    address, so **if the human cannot verify them before the demo, the honest move is to
    cut the two URLs and keep the institution names.**
17. **The packet's own Tamil headings have not been reviewed.** Claim labels, state labels
    and the free-look note are read out of `app/src/main/res/values/strings.xml`, so they
    are the lines the human already reviewed (§8.1) and they cannot drift. But the
    headings that exist only on paper — cover line, "session details", "what was said and
    what the document says", "integrity of this record", "where you can take this", the
    empty-state line, and the banner printed when a record does not verify — are defined
    in `PACKET` in `tools/packet-cli/lib/strings.js` and are marked **TAMIL-REVIEW**. They
    were written in this window and no native speaker has read them. The banner one
    matters most: `இந்தப் பதிவு எழுதப்பட்டபடி இல்லை.` is the sentence a person reads when
    their evidence does not verify.
18. **The free-look note now exists in two places that cannot check each other.**
    `tools/packet-cli/lib/strings.js` reads `free_look_note` **by name** out of
    `app/src/main/res/values/strings.xml` and throws if it is absent — so the resource
    name is part of an interface, and renaming it breaks the packet renderer with no
    compiler to catch it (a comment now says so in both files). The English half is worse:
    `free_look_note_en` was added to `strings.xml` for the app, while the packet carries
    its own English in `PACKET.freeLookEn`. **Two copies of one legal sentence, and nothing
    fails if they diverge.** The Tamil is safe because there is only one copy of it. Fix
    when P7 touches either file: have `strings.js` read `free_look_note_en` by name too,
    and delete `PACKET.freeLookEn`.
19. **RESOLVED — page images in the export are masked, and the mask has been seen
    painting.** P4 landed, so `PageScanner` runs every capture through `PrivacyMask`
    before `SessionEvidence` writes it, and a page that fails while masking is running
    is not written at all (decision 74). On 2026-09-13 at 08:19:11 the masker was built
    on the phone **on NPU** during a live session (`evidence/G4_npu_logcat.txt`), and
    `page_5.jpg` of `session_2026-09-13_081722` carries visible `FILL_COLOR` regions —
    so the masker loaded, ran, and wrote a painted file. The `Unmasked` fallback still
    exists for a phone where no rung loads, and the scan screen still says plainly when
    no mask is running, so the operational rule is unchanged and cheap: **look at the
    scan screen before sharing an export.** What remains unknown is whether the mask
    lands on a *person*; that is issue 22, now narrowed.
20. **`adb` on this laptop truncates a *derived* destination name by one character.**
    platform-tools 36.0.1, Windows, Git Bash. `adb pull <remote>/x.zip ./dir/` writes
    `x.zi`; a directory pull loses the last character of the directory name; `adb push
    probe.txt /sdcard/d/` arrives as `probe.tx`. Contents are byte-exact — it is only
    the name adb derives from the source. **Always give `pull` and `push` an explicit
    full destination path.** This is what really produced `prop-05.pn` / `prop-06.pn`
    on the phone, which were renamed with `adb shell mv` on the assumption the fault
    was device-side; it was not. `scripts/push_models.sh` is immune because it pushes
    to an explicit `${remote_path}` (line 109), which is why the model tree is intact.
    Anything in `evidence/` that was pulled with a derived name should be re-checked.
    Recorded as decision 73.
21. **The Receipt screen's failure path has never been exercised.** Decision 66 puts a
    failed session's reason at the top of the screen, and `SessionService.listen()`
    ends from a `finally` so a failed session does arrive in ENDED — but no run has
    actually failed on the device. The cheap way to force one is to rename a model
    directory under `/sdcard/Android/data/app.vaakku/files/models/` and start a
    session; **rename it back afterwards** (CLAUDE.md forbids deleting `models/`).
22. **NARROWED — the mask has never been checked against a photograph of a person.**
    This issue used to hold three unknowns. Two are now closed: the NPU rung loads
    (`evidence/G4_npu_logcat.txt`, 08:19:11) and `totalMs` at capture size is 117–159 ms
    (`evidence/G4_mask_bench.csv`). **The third stands, and is the one that matters.**
    `MaskMathTest` proves the arithmetic against hand-built tensors; nothing has checked
    it against a real photograph of a real person. A mask off by a transpose would still
    report a plausible coverage figure and still paint something.

    What is new is a first data point, and it points *away* from silent under-painting:
    `page_5.jpg` of `session_2026-09-13_081722` has painted regions over a cable and a
    dark desk with **no person in the frame** — the model is willing to call dark clutter
    "not background". Over-painting fails safe for privacy (it covers more, not less) and
    unsafe for OCR (it could cover text). Neither is proof about a person.

    **Still needs a human to point the camera at a person holding a page, scan it, and
    look at the written file.** Until that look happens, the `Active` line on the scan
    screen is a promise the app has not earned.
23. **The APK is 133 MB, and 86 MB of that is one file.** `libQnnHtpPrepare.so` is the
    on-device JIT compiler the §6.5 sample's `android_jit` path requires. If a submission
    rule or a transfer constraint makes this a problem, the fix is to drop the Qualcomm
    runtime: the app then falls to GPU, the label says GPU, and nothing else changes.
    Decide before the freeze, not during it.
24. **§6.5's live preview overlay was not built.** The plan describes the model running on
    every analysis frame with the person masked in the preview; this implementation masks
    at capture time only. The privacy guarantee is unaffected — §6.4 masks before the
    write, which is what keeps a face out of the receipt — but a demo audience will not
    *see* the mask working unless someone opens a saved page image. If the demo needs a
    visible mask, budget for the overlay or plan to show a saved page.
25. **A real G3 result sat on the phone for two hours and nobody knew.** The 06:07 session
    read two clause types from photographs — the single most important open question in the
    project at that moment — and STATUS.md went on saying "no page has ever been through
    the camera into the ledger" until 08:05, when the phone was inspected for an unrelated
    reason. Nothing was broken; the evidence was simply never collected from the device.
    **The receipts are the record, and they are already on the phone in
    `Download/Vaakku/<sessionId>/receipt.json`.** Before writing "not done" against any
    gate, check the device: `adb shell ls -lt /sdcard/Download/Vaakku/` costs one command
    and would have caught this. The same risk applies to G4 — after the benchmark run, pull
    the CSV before concluding anything.

    **It happened again, same day, 08:28.** STATUS.md said all three G4 items were NOT
    DONE. Two of them were already on the phone: both benchmark CSVs, and a logcat buffer
    holding complete proof of Hexagon dispatch from a live session. The rule above was
    written before this and was not followed. Check the device *first*.

26. **The NPU's first scan costs 1.5 seconds, and it lands inside the buyer's tap.**
    `STAT: prepare_ms=1543` — the QNN graph is JIT-compiled on the device, once per
    process, when the masker is first built. At 08:19:11 a human waited through it mid
    session. It does not recur, and it is the price of the `android_jit` path (and of the
    86 MB `libQnnHtpPrepare.so`), but a demo that scans cold will show a 1.5 s pause at
    the worst possible moment.

    **Narrowed 2026-09-13 by reading the API rather than guessing.** Three fixes were on
    the table; one is now ruled out and one is already built:

    - *Cache the 10,452,992 B context binary* — **not available in LiteRT 2.1.6.** Read
      from the AAR with `javap` (`litert-api-2.1.6.aar`, the same method that produced
      `evidence/sherpa_api_1.13.8.txt`): `Environment.Option` has exactly three entries —
      `CompilerPluginLibraryDir`, `DispatchLibraryDir`, `SystemRuntimeHandle` — and
      `CompiledModel.QualcommOptions.Key` has fourteen, of which the only path-valued two
      are `IR_JSON_DIR` and `DLC_DIR`, both debug dumps rather than a context-binary
      cache. There is no key that takes a cache directory. LiteRT *does* serialise the
      binary (`qnn_manager.cc:399` in the logcat proves it) but exposes no way to keep it.
      Building this would mean going below the Kotlin API, and CLAUDE.md forbids upgrading
      the dependency to look for a newer one.
    - *Warm the masker earlier* — **already built, and it is why the cost was survivable.**
      `ScanSheet.kt` calls `privacyMask.warmUp()` in a `LaunchedEffect` when the sheet
      opens, so the compile overlaps the seconds the human spends aiming. Moving it to
      session-screen open would buy a few more seconds at the cost of compiling a model on
      every session whether or not anyone scans.
    - *Never demo the first scan cold* — the remaining zero-code option, and now the
      recommended one. **Scan once before the audience arrives.** This is a demo-day step,
      not a fix, and it belongs in the P8 runbook.

27. **The demo's weakest link is ASR confidence, not the reconciler.** In
    `session_2026-09-13_081722` the GUARANTEE row — the row that carries the whole point
    of the product — came out UNCERTAIN because ASR returned **0.492** on
    `து எப்டி மாதிரிதான் கேரண்ட`, a fragment missing the word it needed. The reconciler was
    right to stay silent (CLAUDE.md #2) and the written side was flawless: six observations,
    all `guaranteed=false`, up to 0.900. The gap is entirely on the spoken side. Before the
    demo, the sentence that carries GUARANTEE needs to be spoken slowly and cleanly enough
    to clear `spokenMin`, and that should be rehearsed against the Live ASR screen rather
    than discovered on stage. Related: G2's bake-off numbers describe clips, not a person
    speaking across a table in a noisy hall.

28. **Mask coverage is computed, shown for a moment, and then lost.** — **FIXED (the cheap
    half), 2026-09-13.** `MaskMath.personCoverage` produced the fraction of each page
    painted out; it reached `PrivacyMask.MaskSummary.Ran` and the scan screen, and then
    nothing. It was not logged and it is not in the receipt, so after the fact a page
    masked at 40% and a page masked at 0% were indistinguishable — exactly the question
    issue 22 needs answered.

    `PrivacyMask.apply` now takes the page's frame id and logs one line per page under tag
    `VaakkuNpu`, the same tag as "Mask model loaded on …", so one grep tells the whole
    masking story of a session:

    ```
    page_3 2448x3264: masked coverage=0.0000 on NPU (inference 7.9 ms, total 121.4 ms)
    page_5 2448x3264: masked coverage=0.1837 on NPU (inference 7.6 ms, total 118.2 ms)
    ```

    All three outcomes log, not just the successful one — a withheld page and a page that
    ran with no masker are both recorded, because "nothing appeared in logcat" must not be
    ambiguous between "it did not run" and "it ran and painted nothing". The pixel size
    travels with the line because coverage alone cannot tell a mask that landed on the
    person from one that landed beside them, and `width×height` is the input to
    `MaskMath.maskIndexFor`'s mapping — which is the geometry issue 22 is actually about.

    **The honest fix is still not done and is deliberately deferred.** A coverage field in
    the receipt changes the schema and the hash chain, and `DocumentScanCompleted` is a
    bare marker by design (§6.4/§7.1, decision 82). That is not a change to make between
    G4 and the demo. Logcat is enough to answer issue 22; the receipt question can wait
    for a phase that can afford a schema revision.

## On-device verification status (P0)

Verified on the iQOO 15 by driving the real app over adb, each backed by a screenshot
or a file in `evidence/`:

- [x] Tamil renders in Noto Sans Tamil — **no tofu boxes** anywhere: title, all six
      domain labels, the long offline help line, the disclosure paragraph.
- [x] Nothing clips — **after** fixing the inset bug this screenshot exposed
      (decision 19). Re-verified.
- [x] Domain selection works: tapping the BNK cell moved the ink fill from
      காப்பீடு to கடன், verified by sampling cell fills, not by eye.
- [x] Permission row reads அனுமதிக்கப்பட்டது with mic + camera granted.
- [x] Airplane mode ON → amber offline chip shows and **Start is enabled**.
- [x] Accelerator row reads அளக்கப்படவில்லை (not measured) — CLAUDE.md #8 holding.
- [x] ASR row names the ROM's recognizer and says the app has loaded nothing yet.
- [x] Dev menu → Rung-0 probe → export produced a real file (M1).
- [x] `evidence/G0_setup.png`, `evidence/G0_rung0_probe.png` captured.

**Not yet verified — these need a human finger and belong in the next Red Light window:**

- [ ] Toggle airplane mode **off** and confirm Start *disables* again. This is the
      offline proof and only half of it has been demonstrated; the half that was shown
      is the half that passes trivially.
- [ ] Tap the offline row and confirm it opens the real airplane-mode settings panel.
- [ ] Type in the counterparty field — confirm the Tamil hint disappears, the underline
      shows, and the soft keyboard does not cover the field.
- [ ] Tap the remaining four domain cells and confirm the source line changes with each.
- [ ] Long-press the debug override line with the radio off (see open issue 9).
- [ ] Long-press the screen title → Dev menu (see open issue 9 — currently unconfirmed).

## Next Red Light test list

**The current build is installed and P4 has run on it.** The APK on the phone contains the
masker, the benchmark screen and the latency label, and all three have executed —
`adb install -r` succeeded on 2026-09-13 and the 08:19 live session dispatched the mask
model to the Hexagon DSP. No install is needed before the items below. If one becomes
necessary, it must not land in the middle of a G3 scan run, because it restarts the app;
`adb install -r` is safe for `models/`, `adb uninstall` is what wipes them, and CLAUDE.md
forbids it.

**Before installing anything, re-check nothing is in flight:**
`adb shell run-as app.vaakku ls -lt files/sessions/` and compare the newest timestamp with
`adb shell date`.

What is left:

- [ ] **Live mic, a teammate speaking T01–T04 from 1 m**, in the room's real noise, with
      `SHERPA_WHISPER_TA`. Score by ear against `labels.json` and write the result here
      as a MEASUREMENT line. **This is the last G2 evidence item** and the only one that
      tests the microphone path rather than the WAV path. Expect it to be poor — see
      decision 40 — and record what it actually is, not what we hoped.
- [ ] **Listen to `T07_selfcorrect.wav` and `T06_honest_lockin.wav`** and answer open
      issue 11: does T07 end on the same number of years that T06 states? The label
      arithmetic depends on it and no ASR output can settle it.
- [x] **P3: prop pages 5+6 printed** (human confirmed).
- [ ] **P3 / G3: five scan sessions.** Each session is BOTH printed pages, then "Done
      scanning" — not five single photos. `DocumentScanCompleted` must fire before any
      NOT_IN_DOCUMENT can appear, and BUNDLING = NOT_IN_DOCUMENT is one of the six expected
      demo outcomes. Record for each session which of the five clauses appeared. The gate is
      ≥4 of 5. Screenshot one good session to `evidence/G3_clauses.png`.
      **Two sessions already count** (`session_2026-09-13_060746` at 06:07 and
      `session_2026-09-13_081722` at 08:17) — both read RETURN_RATE and GUARANTEE and
      nothing else. **The thing to fix in the next two is which page is in front of the
      camera.** The 08:17 session's five pages are saved under
      `evidence/G3_session_081722_pages/` and page 4 is upright and perfectly legible — it
      simply does not carry LOCK_IN, LIQUIDITY or CHARGES. Those clauses have not failed
      to extract; they have never been photographed. **Start the next session on the page
      that carries §7 and §8**, and only then judge the capture cap.
- [ ] **P3 / first scan only: does the shutter click?** `takePicture` can trigger the platform
      shutter sound on some devices and locales, and the app cannot always suppress it.
      CLAUDE.md #9 is "no sound, ever". If it clicks, we handle it at the device (media volume
      / silent mode) and record that as a demo-day step — we do not pretend the code fixed it.
- [ ] **P3 / first scan only: is 4000 px enough for the small print?** The capture is capped
      (decision 49). If the §7 charges table's 5% row or the §8 lock-in line fails to read
      while larger text reads fine, that is the cap, not the extractor — raise `MAX_LONG_EDGE`
      in `DocumentCamera.kt` and re-scan before concluding anything about the regexes.
      **Do not raise it pre-emptively.** The 08:17 pages are evidence against the cap being
      the current problem: page 4 is legible at the capped resolution and the missing
      clauses are simply not on it. Photograph the right page first; only if §7/§8 are in
      frame, in focus, and still unread does the cap become the suspect.
- [ ] Watch the phone's temperature during a bake-off re-run if one is needed.
      §11.5 budgets thermal at ≤ MODERATE after 15 minutes.
- [x] **P4 / G4: Dev menu → Mask benchmark → Run.** Done twice on 2026-09-13, 08:13:31 and
      08:14:06. **No rung refused** — NPU, GPU and CPU all loaded and all ran 50×. Both
      CSVs are pulled: `evidence/G4_mask_bench.csv` and `evidence/G4_mask_bench_firstrun.csv`.
      NPU 2.60 ms vs CPU 35.3 ms at inference; see the MEASUREMENT table in the G4 section
      for why the end-to-end figure is only ~1.4× and must always be quoted alongside it.
- [x] **P4 / G4: the logcat pull.** Done — `evidence/G4_npu_logcat_full.txt` (4056 lines,
      unedited, filtered to pid 12257 and tags qnn/litert/tflite/VaakkuNpu) and the curated
      excerpt `evidence/G4_npu_logcat.txt`. **Hexagon dispatch is proven**: `BackendType :
      Htp(2)`, `libQnnHtp.so` loaded, `QnnDevice_create done`, `htpPerfInfrastructure*`,
      VTCM `total_sz=8388608`, `soc_type=SM8850`, **175 of 175 ops in 1 partition**, and
      `graph qnn_partition_0 is loaded 1`. The proof is taken from the **live session at
      08:19:11**, not the benchmark — see decision 80. `VaakkuNpu` answered
      `deviceSupported=true libraryReady=true` before anything was tried, so none of the
      three failure modes applies. **The 4 MiB ring buffer had already rolled** when this
      was pulled; it is now raised to 16 MiB (`adb logcat -G 16M`) so a future capture
      keeps a whole run.
- [ ] **P4: scan one page with a person in frame, then open the saved `page_*.jpg`.**
      This is the only test that checks the mask is *correct* rather than merely present
      (open issue 22). A mask that is off by a transpose still reports a plausible coverage
      figure and still paints something. Look at the file, not at the coverage number.
      **Partly answered and still open:** `evidence/G3_session_081722_pages/page_5.jpg`
      shows the masker painting `FILL_COLOR` over a cable and a dark desk **with no person
      in frame** — so it over-paints dark clutter, which fails safe for privacy and unsafe
      for OCR. That says nothing about whether it covers an actual person. Still needs a
      human in the picture.
      **This test is now instrumented** (issue 28, decision 83): every page logs its
      coverage. Pull it straight after the scan with
      `adb logcat -d --pid=$(adb shell pidof -s app.vaakku) | grep VaakkuNpu`
      and read the `coverage=` figure beside each `page_<n>`. **Look at the image anyway** —
      a transposed mask still reports a plausible coverage number, which is exactly why
      the log line carries `width×height` and why the file is still the evidence. The
      number tells you *whether* something was painted; only the file tells you *where*.
      Screenshot the latency label under the viewfinder to `evidence/G4_latency_label.png` —
      that is G4's third and last evidence item, and it can be captured during the same
      session as the G3 scans above.
- [ ] **P4: check the scan screen's mask line says the right one of three things.** Before
      any scan it should be silent (warm-up unfinished) or say masking is running; if no
      rung loaded it must say the saved image is the raw capture. **The benchmark settles
      which branch is correct on this phone**: every rung loaded, so the line must say
      masking is running and must name NPU. If it says the saved image is the raw capture,
      that is a bug and not a wording problem.
- [x] **P6 / G7: end one session and save the receipt.** Done on the phone — see M3 and
      the P6 evidence table. `INTEGRITY: PASSED`, `SIGNATURE: verified`, exit 0, from both
      the folder and the zip; `strongBox: true`; the hex-vs-bytes signing trap this test
      existed to catch did not fire. **G7 is PASS.**
- [x] **P6: save twice.** Done — same folder, byte-identical head, one field different
      (`/signature/signature`). See decision 64 and M3.
- [ ] **P6: end a session with the models deliberately missing.** Rename one model dir,
      start a session, and confirm the app lands on the Receipt screen with the mic
      failure at the top — not on a blank Session screen. This is decision 66's whole
      point and it is one adb command to set up. **Then rename it back** — CLAUDE.md
      forbids deleting `models/`, and a half-finished test leaves the app broken for the
      next one. Open issue 21; the only P6 path that has never executed.
- [ ] **The Receipt screen and the packet PDF need a Tamil read.** `evidence/P6_12_receipt_saved.png`
      and the PDF from `Download/Vaakku/<sessionId>/` are both on the phone. Nobody who
      reads Tamil has seen either. Open issue 17.
- [ ] The P0 "Not yet verified" list above is still the standing queue underneath this.

**Superseded queue (done — kept for the record):**

- [ ] Dev menu → **Live ASR** → source `T01_*.wav`, engine Whisper small (Tamil) →
      Start. Expect segments with non-empty text, `q=` above 0.8, and an `rtf=` line.
      This is the first proof that sherpa-onnx runs at all on this phone.
- [ ] Same clip, each of the other three engines. Engine 5 must stay un-selectable.
- [ ] Dev menu → **ASR bake-off** → Run → Export CSV. Pull it from
      `Download/Vaakku/evidence/` — this is the G2 evidence file.
- [ ] **Live mic, a teammate speaking T01–T04 from 1 m**, in the room's real noise, with
      the best engine from the bake-off. Score it by ear against `labels.json` and write
      the result here as a MEASUREMENT — it is the third G2 item and the only one that
      tests the microphone path rather than the WAV path.
- [ ] Watch the phone's temperature during the bake-off (four engines, fifteen clips).
      §11.5 budgets thermal at ≤ MODERATE after 15 minutes; this is the first workload
      heavy enough to test it.
- [ ] The P0 "Not yet verified" list above is still the standing queue underneath this.

## Handoff notes for the next session

- Read `CLAUDE.md` → `STATUS.md` → `docs/VAAKKU_BUILD_PLAN.md`, in that order.
- **`:domain` is done (P1, G1 PASS)** — model/lexicon/normalize/extract/reconcile/copy,
  277 tests, 26 fixtures, DIFFERS precision & recall both 1.00, schema guard clean. See
  "G1 evidence" above for the ten spec-ambiguity resolutions and the one real bug the
  fixtures caught before touching this again. P2 (ASR) is next; when its output starts
  reaching the domain layer, it should slot into `SpokenExtractor.extract(AsrSegment)`
  and `Reconciler` unchanged — that boundary was the whole point of building `:domain`
  phone-free and pure-JVM.
- `testdata/testaudio/` now holds all 15 `.wav` files at 16 kHz (open issue 10 closed),
  and `evidence/asr_prescreen/<engine>/<file>.txt` holds 60 real transcripts.
  `:domain:evalTranscripts` scores them with no code change and writes
  `evidence/asr_slot_accuracy.md`. **Re-run it after any lexicon or extractor change** —
  it is now the cheapest regression test the project has against real speech.
- **P2 step 2 is the app.** The sherpa-onnx API surface has already been read out of the
  AAR and written to `evidence/sherpa_api_1.13.8.txt` — use that file instead of guessing
  or re-running `javap`. Three things in it are easy to get wrong: `OfflineRecognizer`'s
  and `Vad`'s first constructor parameter is a **nullable** `AssetManager` (pass `null`
  to load from `/sdcard/...` absolute paths), `OfflineModelConfig` is one flat class with
  a field per model family (`whisper`, `dolphin`, `omnilingual`, …) rather than a
  sealed hierarchy, and the VAD's segment type is `SpeechSegment(start: Int, samples:
  FloatArray)` where `start` is in **samples**, not milliseconds.
- **P2 step 2's code is written and green (commit `bfff0c4`); what is missing is the
  phone.** `app/src/main/java/app/vaakku/asr/` holds the whole subsystem and
  `app/src/debug/java/app/vaakku/dev/` holds the two instruments that produce G2's
  evidence. Nothing in it has ever executed on a device — the models have never been
  pushed (open issue 13) and `adb devices` currently reports the phone as
  `unauthorized`. Start there, not by reading the code again.
- **The three ASR numbers to keep straight.** Laptop RTF (measured, indicative only),
  **phone** RTF (the one §13's P2 rule actually gates on, still unmeasured), and slot
  accuracy (39% at best on the laptop, against a 70% threshold). Do not quote a laptop
  RTF as if it settled the gate, and do not set a default engine before the phone
  numbers exist.
- **`segmentQuality` under 0.842 silences a single-mention DIFFERS.** That arithmetic
  (decision 31) is the reason `SegmentQuality`'s dBFS anchors were calibrated against
  real recordings instead of picked. If a live-mic test shows claims registering but no
  DIFFERS cards, check `q=` on the Live ASR screen before suspecting the reconciler.

- **The Rung-0 question is answered — do not re-litigate it.** See M1.
  `isOnDeviceRecognitionAvailable()` is *true*, but the on-device recognizer does not
  offer `ta-IN` at all, so engine 5 (`AndroidOnDevice`) is out for Tamil and Rung 1
  (sherpa-onnx) carries it. The subtlety worth keeping: the availability flag being
  true is not the same as the language being there, and `needs_download: false` on
  ta-IN means "never offered", not "ready".
- ~~`SessionService` is deliberately NOT in the manifest.~~ **Stale — it is written,
  declared and proven.** See open issue 14 and the P5 evidence table:
  `foregroundServiceType="microphone"`, not exported, `check_manifest.sh` asserts it
  positively, and `dumpsys` reports 0 ServiceRecords after Close.
- The Gradle wrapper is generated and pinned to 9.3.1; `./gradlew` works in Git Bash
  with `JAVA_HOME` set to Android Studio's JBR. If it is ever missing, `gradle wrapper`
  regenerates it from the cached distribution.
- **Check the Android platform API surface against `$ANDROID_HOME/sources/android-36.1/`
  before using it.** During P0 a non-existent member (`SpeechRecognizer.getRecognizerInfo`)
  was assumed from memory and had to be caught by the compiler; reading the real
  `SpeechRecognizer.java` then found the package-visibility bug in decision 9 above.
  The SDK sources are on disk — use them instead of recalling an API.
- **The design source is `docs/prototype/prototype_v2_extracted.html`**, not the
  original bundle. `docs/vaakku_prototype_v2.html` is a self-extracting file: line 379
  is a JSON manifest of base64+gzip assets and the actual design gallery is a
  JSON-encoded HTML shell on line 391. The extracted copy is the one to read. Frames
  beyond the v1 spec's list exist there: 12b, 15b, 27, and a six-domain Home.
- **The theme is token-driven; do not reach for a top-level colour.** Read
  `VaakkuTheme.colors` / `.type` / `.space` inside a composable. A top-level `Ink` or
  `Paper` would silently stay in Day colours when Night is active, which is why the
  retired names were removed rather than kept as aliases.
- **Driving the app over adb works and is worth reusing.** `adb exec-out screencap -p`
  plus PIL to locate text bands beats guessing tap coordinates, and sampling a
  region's average colour is how the domain-selection and violet-button checks were
  verified rather than eyeballed. Two traps: Git Bash rewrites `/sdcard/...` into a
  Windows path, so export `MSYS_NO_PATHCONV=1` before any `adb shell`/`adb pull` that
  names a device path; and a tap issued right after a scroll lands during the fling and
  is swallowed, so let the list settle before tapping.
- **`checkBannedWords` scans comments too.** Two of its first findings were prose in a
  doc comment ("risk ramp", "never a verdict"), not product strings. The guard was
  right both times. Reword the comment; never touch the list.
- ~~**The receipt chain is proven; the phone half of §7 is written but has never run.**~~
  **Stale — the phone half has now run, and G7 is PASS.** See M3 and the P6 evidence
  table. A session was ended, saved, and re-saved on the iQOO 15; `tools/packet-cli`
  read both the folder and the zip back as `INTEGRITY: PASSED` / `SIGNATURE: verified` /
  exit 0. Three things that were theory until then and are now measurements:
  **StrongBox is real on this phone** (`"strongBox": true` in the receipt itself, not
  only in the CLI's report); the hex-vs-bytes signing trap **did not fire** — the
  signature covers the head's 64 ASCII hex characters, the same convention as
  `ReceiptFixtureRunner.sign` and `verify.js`, so all three agree; and re-signing the
  same session changes **exactly one field**, `/signature/signature` (the ECDSA nonce),
  while the head stays byte-identical — decision 64 measured instead of argued. The head
  shown on screen before Save equals the head in the file. Re-export replaces in place,
  with no `(1)` copies. What remains untested in P6 is the *failure* path only — open
  issue 21.
- **Never let adb derive a filename.** Decision 73 / open issue 20: on this laptop
  (platform-tools 36.0.1, Windows), any `pull` or `push` where adb works out the
  destination name itself drops the **last character** — `x.zip` arrives as `x.zi`, a
  directory pull became `…_06204`, `probe.txt` pushed to a directory became `probe.tx`.
  Contents are byte-exact; only the name is wrong. Always write the full destination
  path on both sides: `adb pull /sdcard/a/b.zip ./evidence/b.zip`, never `./evidence/`.
  This is also the real cause of the `prop-05.pn` / `prop-06.pn` names that were once
  "fixed" with `adb shell mv` — that fix was on the wrong side of the cable.
  `scripts/push_models.sh` is immune (it passes an explicit `${remote_path}`), which is
  why the model tree is intact and ASR works.
- **`PREVIEW_ASPECT` in `ScanSheet.kt` and the ratio in `DocumentCamera.bind` must move
  together.** The viewfinder used to be a 4:3 surface inside a taller box, so the
  preview overflowed its frame by ~298 px and the buyer aimed at a crop of what was
  actually saved (decision 70; before/after pixels in the P6 evidence table). The fix
  pins the Compose box to the same ratio the capture uses. If one changes and the other
  does not, the frame silently stops telling the truth about what the photo will contain
  — and nothing will fail to compile.
- **Three bugs of one shape were found in P6 by reading, not by test: a label derived
  from a proxy for the thing it claims.** "N / 6 topics" was `ledger().size / 6`, which
  is `6 / 6` for every session ever recorded, including an empty one — `ledger()` is
  `ClaimType.entries.associateWith { … }`, so its size is a constant and can never
  measure anything (now `count { it.observed }`, with 9 tests pinning it). "Saved" was
  `fileCount > 0`. And the zip was computed and then never named on screen, so "2 files
  saved" left a third file in Downloads unmentioned — a buyer who deleted the folder
  would leave a full copy of their document behind. When a number or a label on the
  Receipt screen looks right on a happy-path session, check what it is actually reading;
  that is the screen where a plausible proxy survives longest.
- **The Receipt screen is where CLAUDE.md #1 is easiest to break by accident.** It is
  the one screen that sees the whole ledger at once, so every instinct to summarise
  lands here — a count, a "nothing differed", a badge. Decision 67 records what was
  written and then removed, and why "N of 6" is topics rather than findings. If a future
  task asks for "a summary at the end", that decision is the answer. The `observed`
  tests in `ReconcilerTest.kt` guard the other half of it: they assert the count is
  blind to `DeltaState`, because a count that knew which states it was counting would be
  a finding about a person.
- **Regenerate the receipt fixtures with the Gradle task; never hand-edit them.**
  `./gradlew :domain:receiptFixture` rewrites `receipt.json` and
  `receipt_tampered.json` deterministically — every value is a literal, so a re-run on
  any machine is byte-identical and a diff means something really changed. The signed
  one needs `scripts/receipt_fixture_signed.sh` (it mints a throwaway key with
  `keytool` and deletes it on exit), and **its signature is deliberately not
  deterministic** — ECDSA uses a random nonce, so re-running it always makes a real
  diff. `evidence/receipt_fixture/packet.html` is gitignored: it embeds ~950 KB of
  fonts that are already in the repo. The PDF beside it is the artefact to review.
- **If you add a field to the packet and the CLI crashes, the guard is probably
  right.** `assertNothingInternal` in `render.js` refuses `confidence`, `reason`,
  `ambiguous`, `mentionCount`, `dismissed`, `hedged`, `negated` and `conditional`
  anywhere in the rendered model. It fired once during P6 on `signature.reason` — the
  verifier's own failure text, unrelated to the ledger's `ReasonCode`, and genuinely
  needed on paper. The fix was to copy that sub-object field by field under a different
  name, **not** to add an exception to the guard. Do the same.
- **P4's code is written, compiles and is in the APK — and it has never met the NPU.**
  `app/src/main/java/app/vaakku/npu/` holds the masker (`MaskMath`, `MaskAccelerator`,
  `PersonMasker`), `ocr/PrivacyMask.kt` holds the three-outcome policy, and
  `dev/MaskBenchmarkScreen.kt` produces G4's CSV. **Start with one Dev-menu run and one
  logcat pull, not by reading the code again** — the code cannot tell you what the NPU
  rung will say, and that message is the whole of what P4 does not yet know (open issue
  22). The build plan's §6.5 snippet does not compile as written: it omits the
  `Environment`, and the real call is
  `Environment.create(BuiltinNpuAcceleratorProvider(context))` then
  `CompiledModel.create(assets, path, options, env)`.
- **Never request more than one accelerator in `CompiledModel.Options`.** It takes a set
  and resolves it internally, so `Options(NPU, GPU)` gives you a working model and
  destroys the ability to say honestly which one ran (decision 75). If you find yourself
  adding a rung to that call to "make it work", you are deleting the label.
- **The masker's model shapes were measured, not assumed.** Input `[1,256,256,3]`
  FLOAT32, output `[1,256,256,6]` FLOAT32, read out of the `.tflite` by walking the
  flatbuffer by hand (`tools/npu/tflite_shapes.py`) because TensorFlow is not on this
  laptop. Re-run it with
  `python tools/npu/tflite_shapes.py handoff/npu/selfie_multiclass.tflite`.
  `MaskMath`'s constants carry a test asserting they still match. If the model
  file is ever swapped, that test is the thing that will tell you.
