# P3 — OCR (document reading). Implementation plan.

**Spec (binding authority):** `docs/VAAKKU_BUILD_PLAN.md` §6.4, §5.6, §11.2, §2.3, §5.9.
**Prompt this plan argues from:** `docs/VAAKKU_CLAUDE_CODE_PROMPTS.md` "PROMPT P3 — OCR [GREEN]".
**Gate:** G3 — expected clauses (RATE {4,8} ILLUSTRATIVE, GUARANTEE false, LOCK_IN 60 months,
LIQUIDITY nil-before 60, CHARGES 5%) extracted in ≥4 of 5 scans; screenshot
`evidence/G3_clauses.png`; results table in STATUS.md.

---

## Global Constraints (bind EVERY task; from `CLAUDE.md`, non-negotiable)

1. **No verdicts. Ever.** No field, class, string, enum constant, comment or UI element may
   express risk, score, severity, fraud, suspicion or judgement of a person. User-facing states
   are only MATCHES / NOT_IN_DOCUMENT / DIFFERS. PENDING and UNCERTAIN are **silent**.
   `:domain:test` contains a schema-guard test and there is a `checkBannedWords` Gradle task;
   never weaken either. Banned names include: CONSISTENT, UNSUPPORTED, CONTRADICTED, Judge,
   verdict, risk, score, severity, fraud, suspicious, KAAVAL (as an internal name).
2. **Default failure is silence.** On any doubt — low OCR confidence, implausible value,
   ambiguous normalization — the state is UNCERTAIN, never DIFFERS and never NOT_IN_DOCUMENT.
3. **Offline.** No INTERNET permission in the merged manifest. No networking libraries.
   `scripts/check_manifest.sh` must pass.
4. **Audio never touches disk.** (Images may; audio may not.)
5. **`domain/` is pure JVM.** No `android.*`, `androidx.*`, ML Kit, sherpa or LiteRT imports
   anywhere under `domain/`.
6. **Originality.** Write the code in-window. Never copy from pre-event prototypes. Official
   samples may be read as reference only.
7. **Evidence or it didn't happen.** Never claim a number you did not measure. Never report a
   test as passing without the real command output.
8. **Honest labels.** Never display "NPU" unless logcat proves NPU dispatch.
9. **No colour coding of states.** No red/amber/green anywhere. No sound, ever.

**Never do:** `git reset --hard` · delete `models/` · `adb uninstall` · upgrade any dependency
version in `gradle/libs.versions.toml` · remove or skip tests to make a gate pass · add network
libraries.

**Always do:** before any commit run `./gradlew :domain:test :domain:fixtureReport checkBannedWords`.
Commit on `main` after every green step, message form `P3.<n> <what>`.
Shell is Git Bash on Windows; `./gradlew` works there.

---

## Context

`:domain` already contains, from P1, the pure-JVM half of this phase:
- `domain/.../extract/RowAssembler.kt` — groups `OcrLine`s into visual rows (§5.6).
- `domain/.../extract/WrittenExtractor.kt` — regex rules per claim type (§5.6).
- `domain/.../model/Types.kt` — `OcrLine`, `Box`, `Observation`, `ClaimValue`, `Provenance.Written`.
- 26 fixtures in `domain/src/test/resources/fixtures/`, including D01–D06 (demo) and H01–H04
  (honest) which already contain *hypothesised* prop-document lines.

What P3 adds is (a) confronting that extractor with the **real** prop document, and (b) the
Android half: camera → ML Kit → `OcrLine` → those two classes → `WrittenObserved` events.

The real prop document is `testdata/prop/_Document.pdf` — a 10-page fictional benefit
illustration ("Nambikkai Life Insurance Company Limited (specimen)", Suraksha Savings Plan).
Extract its text with `pdftotext -layout testdata/prop/_Document.pdf -` (pdftotext is on PATH in
Git Bash).

The five G3 clauses live on these pages:

| Clause | Page | Verbatim document text |
|---|---|---|
| RATE {4,8} ILLUSTRATIVE | 3, 4, 5 | "Assumed rates of return used in this illustration: 4% p.a. and 8% p.a." · §4 table row "Assumed Rate of Return (Illustrative) \| 4% p.a. and 8% p.a." · "Returns are NOT guaranteed. The 4% and 8% rates are illustrative only." |
| GUARANTEE false | 4, 5 | §4 table row "Guaranteed Returns \| No" · "Returns are NOT guaranteed." · §6 "Guaranteed Returns on premiums paid: No." |
| LOCK_IN 60 months | 6 | §8 "Lock-in Period: 5 years from the date of commencement of the policy." |
| LIQUIDITY nil-before 60 | 6 | §8 "Surrender Value: Nil before completion of the 5th policy year." |
| CHARGES 5% | 6 | §7 table row "Premium Allocation Charge \| Year 1 \| 5% of Annualised Premium" |

**BUNDLING is expected to be absent** — §11.2 says the prop is "silent on loans", and the
expected demo result is BUNDLING = NOT_IN_DOCUMENT. A grep of the whole document for
`voluntary|loan|mandatory|independent` finds only the words "condition"/"conditions" in
unrelated sentences (policy terms, free-look, tax). Nothing must produce a BUNDLING observation.

**Consequence for the human's scan protocol:** no single page carries all five clauses.
Pages 5 and 6 together do. §6.4 explicitly allows multiple pages per scan session, so one
"scan" in the G3 sense is a scan *session* of page 5 + page 6.

---

## Task 1 — Confront `WrittenExtractor` with the real prop document `[domain, R-OK, pure JVM]`

**Only touch:** `domain/src/main/kotlin/app/vaakku/domain/extract/**`,
`domain/src/test/kotlin/app/vaakku/domain/extract/**`,
`domain/src/test/resources/fixtures/**`.
Do not touch anything under `app/`.

**Goal.** Today the extractor is tested against hypothesised document lines. Make it face the
real ones, find where it fails, and fix the *extractor* — never the expectation.

**Steps.**

1. Extract the real document text: `pdftotext -layout testdata/prop/_Document.pdf -`.
   Read pages 3–6 carefully.

2. Add a new test, `RealPropDocumentTest.kt`, in
   `domain/src/test/kotlin/app/vaakku/domain/extract/`. It builds `OcrLine`s from the **verbatim**
   text of document pages 5 and 6 (one `OcrLine` per visual line, with plausible `Box`
   geometry so `RowAssembler` has real vertical positions to cluster; `confidence = 1.0`),
   runs `WrittenExtractor().extract(...)`, and asserts:
   - RETURN_RATE observed with `percents == setOf(4, 8)` and `qualifier == ILLUSTRATIVE`
   - GUARANTEE observed with `guaranteed == false`
   - LOCK_IN observed with `months == 60`
   - LIQUIDITY observed with `surrenderNilBeforeMonths == 60`
   - CHARGES observed with `percent == 5`
   - **no BUNDLING observation at all** (the document is silent on loans)
   Two-column table rows (§7 charges table, §4 glance table) must be supplied as *separate*
   `OcrLine`s at the same vertical position — that is what ML Kit will hand us, and exercising
   `RowAssembler` is the point.

3. **Known defect to fix (found by inspection, verify it yourself first):**
   page 5 §6 reads "Guaranteed Returns on premiums paid: No." The current `GUARANTEE_FALSE`
   regex is `not guaranteed|non-guaranteed|guaranteed returns?\s*:?\s*no`, which requires "no"
   to follow "Guaranteed Returns" immediately — the words "on premiums paid" break it, so this
   sentence yields nothing. The §5.6 spec's intent is that a document stating guaranteed
   returns are "No" reads as `Guarantee(false)`. Widen the pattern to tolerate a short run of
   intervening words, and add a test for the verbatim sentence. Keep it tight enough that
   "Guaranteed Returns ... Yes" can never be read as false — add a test for that too.

4. Look for any **other** row in pages 3–6 that produces a wrong or spurious observation, and
   fix the extractor for each. In particular check: the §5 year-by-year table (many rows of
   rupee figures and a "non-guaranteed" column header); the §3 eligibility table ("Policy Term
   10 years" must NOT become LOCK_IN — fixture A06 already asserts this, keep it green); the
   page header/footer, which repeat "Benefit Illustration" on every page; the glossary on page
   7 ("Lock-in Period — the minimum period during which…" has the label but no duration).

5. The §7 charges table has three Premium Allocation Charge rows (5% year 1, 2% years 2–5, Nil
   year 6 onward). Decide and **document in a KDoc comment** what the extractor should emit for
   each, and make the test assert it. The gate needs the 5% one present; a second observation
   at 2% is not automatically wrong, but the behaviour must be deliberate and written down.

6. Run `./gradlew :domain:test :domain:fixtureReport checkBannedWords`. All must pass, including
   the 26 pre-existing fixtures — if a fix to the extractor breaks D01–D06 or H01–H04, that is a
   regression, not progress.

7. Commit: `P3.1 real prop-document fixture + written extractor fixes`.

**Report additionally:** the exact list of observations the real pages 5+6 produce, and any
place where the real document and the §11.2 expectation disagree.

---

## Task 2 — `ocr/`: ML Kit Latin recognizer → `OcrLine` `[app]`

**Only touch:** `app/src/main/java/app/vaakku/ocr/**`, `app/src/test/java/app/vaakku/ocr/**`.
Do not touch `domain/`, `app/src/debug/`, or the manifest.

**Goal.** A small, testable seam between ML Kit and the pure-JVM extractor.

**Steps.**

1. Create `app/src/main/java/app/vaakku/ocr/`.

2. `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` — the **bundled** Latin
   recognizer, already declared as `libs.mlkit.text.recognition` (`com.google.mlkit:text-recognition:16.0.1`).
   Do NOT switch to the play-services variant and do NOT change the version.

3. **Answer this explicitly and report it:** does `com.google.mlkit.vision.text.Text.Line` (and
   `Text.Element`) expose a confidence in **this pinned version**? Find out from the actual
   resolved artifact, not from memory — e.g.
   `find ~/.gradle/caches/modules-2 -name "text-recognition-16.0.1*"` then inspect the classes
   (`javap` on the extracted jar/aar, or unzip and read). §6.4 says: use line/element confidence
   if the API exposes it, otherwise 1.0 — and **tell the human which**. Whatever you find, put
   the answer in a KDoc comment at the mapping site with the evidence you used, and repeat it in
   your report.

4. Map each `Text.Line` to `app.vaakku.domain.model.OcrLine`: `text` from `line.text`, `box`
   from `line.boundingBox` (a `Rect`; `OcrLine.Box` is `left/top/right/bottom` Ints), `confidence`
   per step 3, `frameId` from a caller-supplied id.
   A line whose `boundingBox` is null must be **dropped**, not defaulted to a zero box — a zero
   box would make `RowAssembler`'s median line height wrong for the whole page and silently
   corrupt every row on it. Comment that reasoning where you drop it.

5. Expose a suspend API returning both the lines and the **elapsed recognition time in ms**
   (§6.4/P3 item 6, and the §11.5 "scan → clauses ≤ 1.5 s" budget). Measure with
   `SystemClock.elapsedRealtime()` around the recognition call only.

6. Unit-test (JUnit 5 — `:app` already uses `useJUnitPlatform()`, see `app/build.gradle.kts`)
   whatever is pure: the `Rect` → `Box` conversion, the null-box drop, and the confidence
   policy. `unitTests.isReturnDefaultValues = true` is already set, so `android.graphics.Rect`
   can be constructed in a JVM test only if you avoid its native methods — if that fights you,
   introduce a tiny pure mapping function that takes four Ints and test that instead. Do not
   add Robolectric or any new dependency.

7. Build: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`, and
   `./gradlew :domain:test :domain:fixtureReport checkBannedWords` before committing.

8. Commit: `P3.2 ML Kit recognizer → domain OcrLine mapping`.

---

## Task 3 — Scan surface: CameraX, clause list, evidence, `DocumentScanCompleted` `[app]`

**Depends on Task 2** (its recognizer API) and Task 1 (extractor behaviour).
**Only touch:** `app/src/main/java/app/vaakku/ocr/**`, `app/src/main/java/app/vaakku/session/**`,
`app/src/debug/java/app/vaakku/dev/**`.

**Goal.** The human can point the phone at the printed prop document, tap Scan, and see the
clauses that were read — plus an evidence trail on disk.

**Steps.**

1. **Scan screen lives in the debug Dev menu** for now, alongside the existing `LiveAsrScreen`
   and `AsrBakeoffScreen` (`app/src/debug/java/app/vaakku/dev/`). The real session UI is P5's
   job; P3 must not pre-empt its design. Follow the existing dev-screen idiom exactly
   (`DevSection`, `DevMono`, `VaakkuTheme.colors`, an explicit Close button). Register it in
   `DevMenuScreen.kt` / `DevMenuActivity.kt` the same way the other two are.

2. CameraX: `Preview` + `ImageCapture` (full resolution, for OCR) + `ImageAnalysis`
   (**a stub** — it receives frames, records nothing, and calls `imageProxy.close()`; it exists
   so P4's NPU masker has a seat to sit in). Bind to the lifecycle via `ProcessCameraProvider`.
   `CAMERA` permission is already in the manifest; request it at runtime.

3. Tap **Scan** → `ImageCapture.takePicture` → recognizer (Task 2) → `RowAssembler` +
   `WrittenExtractor` → a list of `Observation`s. Emit them as `ReconcilerEvent.WrittenObserved`
   (see `domain/.../reconcile/Reconciler.kt` for the event type).

4. **Show the detected clauses immediately after each scan** as a short list (§6.4). Show each
   claim type and its value in plain words. This list is a *reading of the document* — it is not
   a comparison and must carry no state language at all: no MATCHES/DIFFERS/NOT_IN_DOCUMENT here,
   no colour coding, no judgement. Also show the OCR time in ms (P3 item 6).

5. **"Done scanning"** control → `ReconcilerEvent.DocumentScanCompleted`. Multiple scans (pages)
   accumulate before it. Make it visible in the UI which page count has been captured.

6. **Evidence (§6.4).** Write under `context.filesDir`/`sessions/<sessionId>/`:
   - the full page image, and
   - one JPEG crop per line that produced an observation, quality 80, max 800 px wide, under
     `sessions/<sessionId>/crops/`.
   Set `Provenance.Written.cropFile` to the crop's path so a P5 card can show it.
   **Leave an explicit, named hook for P4's privacy mask** — §6.4 requires the saved page image
   to be masked *before* it is written, and P4 has not built the masker yet. A single clearly
   named function (e.g. `PrivacyMask.applyOrPassThrough`) that currently returns the bitmap
   unchanged, with a KDoc saying P4 replaces the body and why the evidence must never contain a
   person. Do not fake, label or imply masking that is not happening.

7. **Sanity:** no audio is written anywhere by this code (CLAUDE.md #4).

8. Run `scripts/check_manifest.sh` (must PASS — ML Kit must not have smuggled INTERNET back in),
   then `./gradlew :domain:test :domain:fixtureReport checkBannedWords` and
   `./gradlew :app:assembleDebug`.

9. Commit: `P3.3 CameraX scan sheet, clause list, session evidence`.

**Report additionally:** the exact tap coordinates / navigation path to reach the scan screen
from app launch, so the human can be given adb-free instructions.

---

## Out of scope for P3 (do not build)

- The NPU privacy masker itself (P4) — only the hook.
- The real session UI, Delta Cards, provenance sheet, Tamil strings (P5).
- `SessionService` (P5) — `scripts/check_manifest.sh` has an assertion waiting for it.
- Auto-capture / text-region detection (P4 stretch N2).
