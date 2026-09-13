# VAAKKU

An offline Android app that listens to an insurance sales conversation, reads the
benefit illustration the seller is holding, and shows the buyer — in Tamil, in one
large line — **where what was said differs from what is written.**

It runs entirely on the phone. There is no server, no account, and no network
permission in the built APK.

---

## What it actually does

A session has two inputs running at once:

- **The microphone.** Tamil–English sales speech, transcribed on-device, scanned for
  claims about six things.
- **The camera.** The printed benefit illustration, photographed page by page and read
  with on-device OCR.

Both sides produce *observations* about the same six **claim types**:

| Claim type | The question it answers |
|---|---|
| `RETURN_RATE` | What return was promised, and was it called illustrative? |
| `GUARANTEE` | Was it called guaranteed? |
| `LOCK_IN` | How many months is the money locked in? |
| `LIQUIDITY` | When can it be withdrawn, and what is the surrender value before then? |
| `BUNDLING` | Was insurance presented as tied to the investment? |
| `CHARGES` | What charges were named, and at what percentage? |

Each claim type ends the session in exactly one of three user-facing states:

- **MATCHES** — said and written agree.
- **NOT_IN_DOCUMENT** — it was said, and the document does not contain it.
- **DIFFERS** — said and written disagree. This is the only state that gets the violet
  stamp.

Two further states exist internally and are **deliberately invisible**: `PENDING` (not
resolved yet) and `UNCERTAIN` (not confident enough to say anything). They render as
nothing at all. That is the point — see *Silence is the default failure* below.

### What it will never do

The app renders **no verdict**. There is no score, no risk rating, no severity, no
percentage of confidence on screen, and no statement about a person's honesty. It
reports a difference between two texts and stops. Where the difference came from — a
misunderstanding, a rounding, a different product variant, a lie — is not something a
phone can know, and the app does not guess.

This is enforced mechanically, not by good intentions:

- `checkBannedWords` (a Gradle task) fails the build if a banned word appears in app or
  domain sources. It also fails if it can't see the whole source tree, so it cannot go
  quietly blind. See `evidence/P7_checkbannedwords_control.txt`.
- A schema-guard test scans domain classes for verdict-flavoured fields and class names.
- Neither may be weakened to make a build pass.

### Silence is the default failure

Every ambiguity resolves toward saying nothing. Low ASR confidence, a hedge ("around
eight percent"), an unclear negation, a conditional, an ambiguous normalization, or
low OCR confidence all produce `UNCERTAIN`, which shows nothing — never `DIFFERS` and
never `NOT_IN_DOCUMENT`.

The asymmetry is on purpose. A missed difference costs the buyer a card they might have
found useful. A **false** difference puts words in a real person's mouth in front of
them. The two are not equally bad, so the thresholds are not set symmetrically:
`confidence = matchQuality × segmentQuality`, and a `DIFFERS` on a single mention needs
confidence ≥ 0.80, which with an exact match quality of 0.95 means a speech segment
scoring below ~0.842 **cannot** produce a `DIFFERS` card however clearly the words were
said.

---

## What runs where

Two Gradle modules, and the boundary between them is load-bearing.

### `domain/` — pure JVM, no Android

All the logic that decides anything. No `android.*`, no `androidx.*`, no ML Kit, no
sherpa, no LiteRT — enforced by review and by the module having no Android dependency at
all. It is testable on a laptop with no phone attached, which is why it was built first
and why it has real test coverage.

| Package | What lives there |
|---|---|
| `model/` | The claim types, observations, provenance, states |
| `normalize/` | Tamil/English text and number normalization, tokenizing |
| `lexicon/` | The phrase lists each claim type is recognised by |
| `extract/` | `SpokenExtractor` (from ASR segments), `WrittenExtractor` (from OCR rows) |
| `reconcile/` | `Reconciler` — the only thing that compares said against written |
| `copy/` | Tamil/English sentence construction for the card |
| `receipt/` | The canonical-JSON, hash-chained session receipt |
| `fixtures/` | The fixture corpus and its runner |
| `gate/` | Threshold constants |

### `app/` — Android, arm64-v8a only

Everything that touches hardware, and nothing that decides anything.

| Package | What lives there |
|---|---|
| `asr/` | sherpa-onnx offline recognizer, Silero VAD segmentation, segment quality metering |
| `ocr/` | ML Kit text recognition, row assembly, the privacy mask, evidence JPEG writing |
| `npu/` | LiteRT + QNN delegate for the mask model |
| `session/` | The foreground service that owns the microphone, session state, evidence on disk |
| `receipt/` | Signing on-device (StrongBox where available) |
| `ui/` | Compose screens — setup, session, scan sheet, delta cards, receipt |

The seam is thin on purpose. `PageScanner` is a good example: ML Kit and
`android.graphics` stop inside it, it hands `Observation`s to the domain layer, and it
decides nothing about any claim. Domain code never learns a file path — `Provenance`
comes out of the extractor with `cropFile = null` and the Android side fills it in once
the file exists.

### `tools/` — laptop-side, not shipped

| Tool | What it does |
|---|---|
| `packet-cli/` | Verifies a grievance packet exported from the phone: hash chain, signature, integrity |
| `asr_prescreen/` | Scores candidate ASR engines against labelled audio; VAD energy calibration |
| `npu/` | The mask model |

### `scripts/`

| Script | What it does |
|---|---|
| `check_manifest.sh` | Proves no INTERNET / ACCESS_NETWORK_STATE in the merged manifest **and** in the built APK, for **both** debug and release |
| `install.sh` | Manifest guard, build, `adb install -r`, launch. Never uninstalls — that would wipe the pushed models |
| `push_models.sh` | Pushes ASR models to the phone's app-files directory |
| `receipt_fixture_signed.sh` | Produces a signed receipt fixture plus a tampered copy |

---

## Offline, and how that is proven

`CLAUDE.md` rule 3 is that the app is offline. The proof is not "we didn't add
networking":

ML Kit's transport and logging artifacts contribute `INTERNET` and
`ACCESS_NETWORK_STATE` through manifest merging. The app manifest removes both with
`tools:node="remove"`, and `scripts/check_manifest.sh` verifies the removal survived
into the artifact that actually installs. `aapt2 dump permissions` on the release APK
lists `RECORD_AUDIO`, `CAMERA`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`, `POST_NOTIFICATIONS`,
`FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` — and no
`INTERNET`, no `ACCESS_NETWORK_STATE`.

Both variants are checked, because P7 installs release and debug and release are
produced by different tasks.

**Audio never touches disk.** Not in release, not in debug, not behind a flag. Text
transcripts can be exported in debug builds; the audio itself exists only in memory
for as long as it takes to transcribe. `PageScanner` and the session service are both
written so that no code path can write a `.wav`.

Page images that *are* written are masked first (a person holding the page is painted
over on the DSP), and if the masker cannot vouch for a page while masking is on, the
page is **not written at all** rather than written unmasked.

---

## Measured numbers

Every number here came from a command whose output is in `evidence/`. Numbers that were
not measured are marked as such rather than estimated.

### Domain correctness (laptop, reproducible)

```
./gradlew :domain:test :domain:fixtureReport checkBannedWords
```

346 tests, 26 fixtures, 45 assertions, all passing. On the fixture corpus:

| expected \ actual | MATCHES | NOT_IN_DOCUMENT | DIFFERS | SILENT |
|---|---|---|---|---|
| MATCHES | 23 | 0 | 0 | 0 |
| NOT_IN_DOCUMENT | 0 | 3 | 0 | 0 |
| DIFFERS | 0 | 0 | 12 | 0 |
| SILENT | 0 | 0 | 0 | 7 |

Precision on `DIFFERS` 1.00 (TP=12, FP=0); recall 1.00 (TP=12, FN=0).

**This is precision on a hand-built corpus, not in the field.** It says the reconciler
does what the fixtures describe. It does not say the fixtures describe every real
conversation.

### Privacy mask, on the phone (iQOO 15, SM8850, Android 16)

50 runs per accelerator, 5 warmup runs discarded, synthetic 3000×4000 page.
`evidence/G4_mask_bench.csv`.

| Accelerator | Inference, median | Full pipeline, median |
|---|---|---|
| NPU (Hexagon) | **2.59 ms** | 159.4 ms |
| GPU | 11.78 ms | 166.9 ms |
| CPU | 34.70 ms | 193.1 ms |

The NPU is **13.4× faster than CPU on inference** — and that is the honest but
misleading number, so here is the useful one: end to end the page goes from 193 ms to
159 ms, about 18% better, because scaling, normalising and painting the 12-megapixel
bitmap dominate and none of that is on the DSP.

**"NPU" is only printed where dispatch was proven.** `evidence/G4_npu_logcat.txt`
shows LiteRT asking for `BackendType : Htp(2)` (Hexagon Tensor Processor) with all 175
ops accepted, during a real session at 08:19:11 — not a synthetic benchmark. Where
dispatch cannot be proven, the label says what actually ran.

### Tamil ASR — the weakest link, stated plainly

75 transcripts of 15 labelled recordings, scored by running the real `SpokenExtractor`
over them and requiring an exact value match. `evidence/asr_slot_accuracy.md`.

| Engine | Slots correct | Slot accuracy |
|---|---|---|
| whisper-small-ta (2× pad) | 15 / 28 | **54%** |
| whisper-small-ta | 11 / 28 | 39% |
| omnilingual-300m | 5 / 28 | 18% |
| dolphin-base | 3 / 28 | 11% |
| dolphin-small | 0 / 28 | 0% |

**The best engine gets 54%, and the project's own risk register set 70% as the line
below which Tamil ASR counts as "poor in the hall".** So it is below the bar that was
written down before the measurement, and this is the thing most likely to disappoint a
demo. The mitigation is the same asymmetry as everywhere else: a slot the ASR misses
produces silence, not a wrong card.

Note what the 54% is measuring — *slot* accuracy, i.e. did the right **value** come
out end to end, which is a much harder test than word error rate and is the only test
that matters here. A transcript can be largely right and still score zero on a slot by
getting one number wrong.

---

## What is not proven yet

Kept here, in the README, because a document that lists only the passing gates is a
sales document.

- **The full loop has never produced a `DIFFERS` card on real speech.** OCR has been
  proven end to end: one session read all five expected clause types off photographed
  pages at exactly the expected values. The spoken side has not cleared its confidence
  threshold in a live session yet, so *said vs written* has not actually met on the
  phone. This is the single most important open item and it is a measurement problem,
  not a missing feature.
- **OCR gate is 1 of the 4 sessions it requires** (all five clause types per scan, in
  ≥4 of 5 scans).
- **What the privacy mask covers** is instrumented but not settled — it runs, and it
  paints at the page edge where a hand grips the paper, but it has not been tested
  against a camera pointed at a person holding the page.
- **P7 hardening is half done.** The laptop half is finished (release build, offline
  proof, crash review, both build guards proven able to fail). The soak test, thermal
  and battery readings, OriginOS background survival, and three airplane-mode demo runs
  all need the phone in a human's hands.
- **Tamil copy has not been reviewed by a fluent reader** for the receipt screen, the
  packet, and the scan-mask strings.

`STATUS.md` carries the full gate table, every decision with its reasoning, and the
open-issue list.

---

## Building and running

Requires the Android SDK, JDK 17+, and a device. Gradle 9.3.1 / AGP 8.13.1 /
Kotlin 2.3.0, all pinned in `gradle/libs.versions.toml`. Dependency versions are not
upgraded casually — a version bump mid-event is a self-inflicted outage.

**Laptop-only checks** (no phone needed — this is most of the domain work):

```bash
./gradlew :domain:test :domain:fixtureReport checkBannedWords
```

**Prove it's offline** (run before every install):

```bash
scripts/check_manifest.sh
```

**Install on a connected phone.** Runs the manifest guard, builds, installs with
`adb install -r`, and launches. Debug by default; `--release` for the build that ships:

```bash
scripts/install.sh
```

```bash
scripts/install.sh --release
```

It never uninstalls. Debug and release share the package name `app.vaakku` with no
suffix, so they replace each other in place and the pushed ASR models — hundreds of
megabytes in the app's files directory — survive either way. An `adb uninstall` would
delete them.

Installing release loses two things, both deliberate: the Dev menu (which only exists in
`app/src/debug`) and the three-finger-hold debug overlay (gated on `BuildConfig.DEBUG`).
The scan sheet's mask latency label is not debug-gated and still shows.

**Push the ASR models** (once per device; ~hundreds of MB):

```bash
scripts/push_models.sh
```

**Score ASR transcripts against the labels** — re-run after any lexicon or extractor
change, since it's the cheapest regression test against real speech:

```bash
./gradlew :domain:evalTranscripts
```

**Verify an exported grievance packet** — a Node script, not a Gradle module. Exit codes:
0 verified, 2 the record did not verify, 3 the input is not a readable receipt. `--out`
renders a PDF; omit it to verify only:

```bash
node tools/packet-cli/index.js --in <session-folder-or-zip>
```

Target: minSdk 31, compile/target 36, **arm64-v8a only**. Package `app.vaakku`, with no
debug suffix, so debug and release install over each other — which is deliberate, since
the models live in the app's files directory and an uninstall would wipe them.

Developed and tested on an iQOO 15 (Android 16 / OriginOS 6, Snapdragon 8 Elite Gen 5 /
SM8850, Hexagon v81).

---

## Repository layout

```
domain/          pure-JVM logic: extract, reconcile, copy, receipt  (no Android)
app/             Android app: ASR, OCR, NPU mask, session service, Compose UI
tools/
  packet-cli/    verifies an exported grievance packet
  asr_prescreen/ ASR engine scoring + VAD energy calibration
  npu/           the mask model
scripts/         manifest guard, install, model push, receipt fixtures
domain/src/.../fixtures/   the fixture corpus
testdata/        labelled test audio
models/          ASR + VAD models (not committed; see models/MANIFEST.md)
evidence/        command output, screenshots, CSVs — every claimed number's source
docs/            the build plan (the specification; it wins over other documents)
STATUS.md        gate table, decisions log, open issues
CLAUDE.md        the rules this project is built under
```

---

## The rules this was built under

Condensed from `CLAUDE.md`, because they explain most of the design decisions above:

1. **No verdicts, ever.** No field, class, string or UI element may express risk, score,
   severity, fraud, suspicion or judgement of a person.
2. **Default failure is silence.** In doubt → `UNCERTAIN` → show nothing.
3. **Offline.** No INTERNET in the merged manifest. No networking libraries.
4. **Audio never touches disk.** Not even in debug.
5. **`domain/` is pure JVM.**
6. **Evidence or it didn't happen.** A gate passes only with real command output, a file
   in `evidence/`, a screenshot, or a measurement recorded by a human.
7. **Honest labels.** Never display "NPU" unless logcat proves NPU dispatch. Never claim
   accuracy numbers that were not measured.
8. **No colour coding of states.** No red/amber/green. Violet stamp ink marks a
   `DIFFERS` card. No sound, ever.

Rule 6 is why this README cites file paths instead of adjectives, and rule 7 is why the
ASR section says 54% instead of "promising early results".
