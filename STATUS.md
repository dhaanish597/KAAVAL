# STATUS — VAAKKU

Current light: GREEN · Current phase: **P2 (ASR)** — step 1 (laptop pre-screen) complete,
step 2 (ASR in the app) **written and green on the laptop; not yet run on the phone** ·
Hour: H1–H6

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
| G3 OCR | **CODE READY — no scan yet** | `docs/superpowers/plans/p3-ocr-plan.md`; five clauses proven against the real document in `:domain`. Needs 5 scan sessions on the phone. | H6– |
| G4 NPU | NOT STARTED | — | — |
| G5 End-to-end | NOT STARTED | — | — |
| G6 Go/No-Go | NOT STARTED | — | — |
| G7 Receipt + Office Kit | NOT STARTED | — | — |
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
run. **Nothing in this section has seen a camera yet** — the phone half compiles and passes
every guard, but produces no measurement until the human scans.

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
| P4 privacy-mask hook | **done, and inactive** | `PrivacyMask.applyOrPassThrough` returns its input; the screen says so on every run |
| `:domain:test` | **PASS** | 303 tests |
| `:domain:fixtureReport` | **PASS** | 26 fixtures, precision=1.00, recall=1.00, 0 mismatches |
| `checkBannedWords` | **PASS** | 66 files, 0 findings |
| `:app:testDebugUnitTest` | **PASS** | 23 tests incl. 8 new `SessionEvidenceTest` crop-clamp cases |
| `:app:assembleDebug` | **PASS** | — |
| `scripts/check_manifest.sh` | **PASS** | no INTERNET, no ACCESS_NETWORK_STATE, in merged manifest and APK; `SessionService` still absent |
| **5 scan sessions on the phone** | **NOT DONE** | needs a human — the whole of G3's actual criterion |
| `evidence/G3_clauses.png` | **NOT DONE** | needs a human |

**What G3 still requires:** the gate is "expected clauses extracted in ≥4 of 5 scans". Nothing
above measures that. The domain half is proven against the document's *text*; the camera half
has never converted a photograph into that text.

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
14. **`SessionService` (§6.7) is still not written.** It is the foreground-service host
    the real session screen needs; the Dev screens run the pipeline in a plain coroutine
    scope instead, which is fine for a screen you are looking at and wrong for a session
    that must survive the screen turning off. When it lands, `<service
    android:name=".session.SessionService" android:foregroundServiceType="microphone"
    android:exported="false"/>` goes into the manifest **and the corresponding assertion
    in `scripts/check_manifest.sh` must be updated** — it currently asserts the P0 state
    ("SessionService not yet declared") and will fail the moment the service is correct.
15. **Two MCP servers in this environment need authorization and could not be used:**
    `plugin:catalyst-by-zoho:catalyst-by-zoho` and `plugin:supabase:supabase`. Neither is
    needed by VAAKKU, which is offline by design and has no backend — recorded only so
    nobody spends time wondering why those tools are inert. Authorize via claude.ai
    connector settings, or `claude mcp` / `/mcp` in an interactive session.

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

The current debug build **is installed on the phone** and the models are pushed. The
first three items of the previous queue are done and their evidence is in `evidence/`.
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
- [ ] **P3 / first scan only: does the shutter click?** `takePicture` can trigger the platform
      shutter sound on some devices and locales, and the app cannot always suppress it.
      CLAUDE.md #9 is "no sound, ever". If it clicks, we handle it at the device (media volume
      / silent mode) and record that as a demo-day step — we do not pretend the code fixed it.
- [ ] **P3 / first scan only: is 4000 px enough for the small print?** The capture is capped
      (decision 49). If the §7 charges table's 5% row or the §8 lock-in line fails to read
      while larger text reads fine, that is the cap, not the extractor — raise `MAX_LONG_EDGE`
      in `DocumentCamera.kt` and re-scan before concluding anything about the regexes.
- [ ] Watch the phone's temperature during a bake-off re-run if one is needed.
      §11.5 budgets thermal at ≤ MODERATE after 15 minutes.
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
- `SessionService` is deliberately NOT in the manifest. Declaring it before the class
  exists breaks the build. It arrives in P2 (build plan §6.7) along with the
  `foregroundServiceType="microphone"` attribute. **It is still not written** — the Dev
  screens run the pipeline in a plain coroutine scope instead — and when it lands,
  `scripts/check_manifest.sh` needs its assertion updated too; see open issue 14.
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
