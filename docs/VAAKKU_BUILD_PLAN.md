# VAAKKU — MASTER BUILD PLAN (Claude Code edition)

**Event:** iQOO Hackathon 2026 · Chennai City Battle · Sept 12–13, 2026 (30-hour event, ~25 h of hacking)
**Device:** iQOO 15 loaner · Android 16 / OriginOS 6 · Snapdragon 8 Elite Gen 5 (SM8850, Hexagon v81)
**This document is the single source of truth for building the app.** It supersedes every older plan, gate list and prompt queue (`BUILD_PLAN.md`, `KAAVAL_STAGE3_PROMPTS.md`, G6–G15 gates, etc.). Older documents are reference only.

---

## 0. READ THIS FIRST (instructions to Claude Code)

1. **You have no memory between sessions.** Your memory is three files: this plan, `CLAUDE.md`, and `STATUS.md`. Read all three at the start of every session before touching code.
2. **Document precedence when things conflict:**
   1. This build plan
   2. `docs/VAAKKU_FINAL_LOCKED_SPEC.md` (the final research reconciliation)
   3. `docs/KAAVAL_UI_DESIGN_SPEC_v2.md` + `docs/kaaval-tokens.css` (visual design only)
   4. Everything else in `docs/` (history and background only)
3. **Old names map to new names.** Older documents say KAAVAL, CONSISTENT, UNSUPPORTED, CONTRADICTED, "Judge", "verdict". In code and UI use the names in §2.3. Never introduce the old names.
4. **The human (Dhaanish) is your hands.** Only the human can hold the phone, speak into it, point the camera, tap "Allow" on prompts, move files with Office Kit, talk to organizers, and judge Tamil wording. When you need any of that, stop and print a HUMAN ACTION block (format in §12.3), then wait.
5. **Evidence rule.** A gate is passed only with evidence: a command's real output, a file in `evidence/`, an adb screenshot, or a measurement the human reported. "Should work" is never evidence. The previous build attempt reported gates as passing when `assembleDebug` had never run. Do not repeat that.
6. **Originality rule.** All app code is written inside the event window, in this fresh repository. Never copy code from any pre-event prototype folder. Third-party libraries, SDKs, pre-trained models, and official sample code used as reference are allowed.
7. **Red Light / Green Light.** Tasks are tagged **[G]** (needs the laptop to build Android or talk to the phone over adb) or **[R-OK]** (pure-JVM or text work that does not build the Android app). `STATUS.md` records the current light and the organizers' ruling on what is allowed during Red Light. If the human says "RED LIGHT", do only what that ruling allows (see §10).

---

## 1. PRODUCT SUMMARY (LOCKED — do not change)

**What it is:** A fully offline Android app. It listens to an insurance/loan sales pitch (code-switched Tamil–English) and reads the printed benefit illustration with the camera. Then it shows, in one large Tamil line before the buyer signs, exactly where what was *said* differs from what is *written*.

**Positioning line:** "There are a dozen well-funded companies recording sales conversations. Every one of them works for the person doing the selling. This one works for the buyer."

**The load-bearing rule:** The app **never** renders a verdict, score, risk rating, severity, or any judgement about the agent. It only places two statements side by side: one spoken, one written. "It never accuses. It quotes." This is enforced **in code** (§5.9 schema guard test and §9 banned-words check), not just by policy.

**Name:** Display name comes from one string resource, `app_name`. Default is `VAAKKU`. If organizers do not allow a rename from the registered name, the human changes that one string to `KAAVAL`. Internal package: `app.vaakku`. Session ID prefix: constant `SESSION_PREFIX = "VKU"`. Format: `VKU-DDMM-NNNN`.

---

## 2. DOMAIN DEFINITIONS (LOCKED)

### 2.1 The six claim types
| Enum | Meaning | Spoken example | Written example |
|---|---|---|---|
| `RETURN_RATE` | Return / interest rate | "எட்டு percent return" | "Assumed rate of return: 4% p.a. and 8% p.a." |
| `GUARANTEE` | Guaranteed vs illustrative | "guaranteed" / "உறுதியான" | "Returns are NOT guaranteed" |
| `LOCK_IN` | Lock-in period | "lock-in ஒரு வருஷம் தான்" | "Lock-in period: 5 years" |
| `LIQUIDITY` | Withdrawal / surrender terms | "one year கழிச்சு எடுக்கலாம்" | "Surrender value: Nil before completion of 5th policy year" |
| `BUNDLING` | Required for a loan | "இந்த policy எடுத்தா தான் loan" | "Purchase is voluntary / not a condition for any loan" (usually absent) |
| `CHARGES` | Charges, fees, commissions | "charges எதுவும் இல்லை" | "Premium allocation charge: 5% in year 1" |

### 2.2 Sources
`SPOKEN` (from ASR) and `WRITTEN` (from OCR). The two producers are independent; one reconciler merges them.

### 2.3 States (internal enum → what the user sees)
| Internal state | Shown to user? | User-facing label (Tamil / English) | Meaning |
|---|---|---|---|
| `PENDING` | **No (silence)** | — | Only one side seen yet, or document not scanned yet |
| `UNCERTAIN` | **No (silence)**; at most a neutral "scan again" hint in Details | — | A gate failed: low confidence, hedge, negation ambiguity, normalization ambiguity |
| `MATCHES` | Yes, low emphasis (ink grey, never green) | பொருந்துகிறது / Matches | Spoken value is supported by the document |
| `NOT_IN_DOCUMENT` | Yes (open dotted field) | ஆவணத்தில் இல்லை / Not in document | Document fully scanned; confidently contains no value of this type |
| `DIFFERS` | Yes (Delta Card, violet perforation) | வேறுபடுகிறது / Differs | Both sides confident, same type, values differ beyond tolerance, no hedge/negation |

> **Precision refinement (why two silent states exist):** The locked spec says the default failure is silence. `NOT_IN_DOCUMENT` has visible copy ("not in the document — ask"). If we showed it when the OCR simply failed to read a line, we would be making a false statement. So low-confidence cases map to `UNCERTAIN` (silent), not `NOT_IN_DOCUMENT`. The three user-facing labels are unchanged.

### 2.4 Banned words (code identifiers, UI strings, notifications, PDF packet)
`verdict, judge, judgement, score, risk, severity, fraud, scam, suspicious, suspect, mis-sell, missell, mislead, misleading, lie, liar, cheat, guilty, accuse, danger, warning, alarm, trust score, confidence %`
Also banned in UI: red, amber or green colour for any state; traffic-light ramps; gauges; percentages of "confidence" shown to the user; sounds.
Internal `confidence: Double` fields are allowed (they are gates, not displayed).

---

## 3. EVENT CONSTRAINTS THAT SHAPE THE BUILD

- **Rubric (official, Reskilll):** End product quality 30 · Novelty & impact 20 · Creative phone use 15 (HackTracker, device data) · Technical depth 15 · Office Kit usage 10 (HackTracker, device data) · Demo & presentation 10.
  - *Consequence 1:* camera, mic and on-device AI must be genuinely running **on the phone** during the build. Install builds early and often.
  - *Consequence 2:* Office Kit must be used for real work (file transfer of receipts/evidence, screen mirroring, Remote PC if allowed), not only in the demo.
- **Red/Green:** ~55% Red Light (phone only; laptop closed as a build machine; Office Kit is the only bridge), ~45% Green Light. The schedule is announced by organizers. **Always make sure a fresh, working APK is installed on the phone before each Red Light window starts**, because Red Light is when the human tests.
- **Originality:** fresh repo, created in-window. Pre-downloaded models/SDKs/fonts/test audio/printed props/docs are assets, not code.

---

## 4. REPOSITORY LAYOUT

```
vaakku/                                   (repo root, created in-window)
├── CLAUDE.md                             (provided by human, copy verbatim)
├── STATUS.md                             (you create in P0, update every task)
├── docs/                                 (human copies reference docs here; read-only for you)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml             (versions from docs/versions_from_scratch.txt)
├── domain/                               (PURE Kotlin/JVM — zero android.* imports)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/app/vaakku/domain/{model,normalize,extract,reconcile,receipt,copy}/
│       ├── main/resources/lexicon/lexicon_ta_en.json
│       └── test/{kotlin/..., resources/fixtures/*.json}
├── app/                                  (Android app)
│   ├── build.gradle.kts
│   ├── libs/sherpa-onnx-<ver>.aar        (human-provided; gitignored)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/app/vaakku/{asr,ocr,npu,session,receipt,ui,debug}/
│       ├── assets/npu/                   (NPU .tflite model)
│       ├── assets/testaudio/             (copied from testdata/ by Gradle)
│       └── res/{font,values}/
├── litert_npu_runtime_libraries/         (human-provided, gitignored; only if the NPU sample layout needs it)
├── testdata/testaudio/                   (human-recorded WAVs + labels.json; tracked, small)
├── tools/
│   ├── asr_prescreen/                    (Python laptop pre-screen)
│   └── packet-cli/                       (Node.js grievance packet)
├── models/                               (ASR models; gitignored; MANIFEST.md tracked)
├── scripts/                              (install, push models, manifest check, logcat filters)
└── evidence/                             (screenshots, CSVs, logs — gate evidence; tracked)
```

`.gitignore` must include: `models/**` (except `models/MANIFEST.md`), `app/libs/*.aar`, `litert_npu_runtime_libraries/`, `**/build/`, `.gradle/`, `local.properties`, `*.apks`.

**Windows note:** the laptop is Windows. Claude Code runs in Git Bash, so `./gradlew` works there. Write helper scripts as `.sh` for Git Bash. `adb` must be on PATH; if it is not, use the full path from `local.properties` `sdk.dir` + `/platform-tools/adb.exe`.

---

## 5. THE DOMAIN MODULE (pure JVM) — specification

All product logic lives here, so it can be unit-tested on the laptop in seconds without the phone. `domain` must not import anything from `android.*`, `androidx.*`, ML Kit, sherpa or LiteRT.

### 5.1 Core types (Kotlin, suggested shapes; adjust names but keep semantics)
```kotlin
enum class ClaimType { RETURN_RATE, GUARANTEE, LOCK_IN, LIQUIDITY, BUNDLING, CHARGES }
enum class Source { SPOKEN, WRITTEN }
enum class DeltaState { PENDING, UNCERTAIN, MATCHES, NOT_IN_DOCUMENT, DIFFERS }
enum class RateQualifier { ASSERTED, UP_TO, ILLUSTRATIVE, EXPECTED }

sealed interface ClaimValue {
  data class Rate(val percents: Set<BigDecimal>, val qualifier: RateQualifier) : ClaimValue
  data class Guarantee(val guaranteed: Boolean) : ClaimValue
  data class LockIn(val months: Int) : ClaimValue
  data class Liquidity(val withdrawableAfterMonths: Int?, val surrenderNilBeforeMonths: Int?) : ClaimValue
  data class Bundling(val requiredForLoan: Boolean) : ClaimValue
  data class Charges(val anyCharges: Boolean, val percent: BigDecimal?, val label: String?) : ClaimValue
}

sealed interface Provenance {
  data class Spoken(val span: String, val startMs: Long, val endMs: Long, val engine: String) : Provenance
  data class Written(val lineText: String, val box: Box, val frameId: String, val cropFile: String?) : Provenance
}
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class Observation(
  val id: String, val source: Source, val type: ClaimType, val value: ClaimValue,
  val hedged: Boolean, val negated: Boolean, val conditional: Boolean,
  val confidence: Double,            // INTERNAL ONLY. Never displayed.
  val provenance: Provenance, val tMs: Long)

data class LedgerEntry(
  val type: ClaimType, val spoken: Observation?, val written: List<Observation>,
  val state: DeltaState, val reason: ReasonCode, val mentionCount: Int, val dismissed: Boolean)

enum class ReasonCode { NO_SPOKEN, NO_DOC_YET, SPOKEN_LOW_CONF, WRITTEN_LOW_CONF, HEDGED, NEGATION_AMBIGUOUS,
  CONDITIONAL, NORMALIZATION_AMBIGUOUS, VALUE_EQUAL, VALUE_IN_SCENARIOS, TOLERANCE_EXCEEDED, DOC_SILENT, NEEDS_CORROBORATION }
```
`ReasonCode` is a debugging aid shown only in the debug overlay. It must never describe people.

### 5.2 Inputs the app gives the domain
```kotlin
data class AsrSegment(val text: String, val startMs: Long, val endMs: Long, val engine: String,
                      val segmentQuality: Double /* 0..1 from VAD speech prob + energy */)
data class OcrLine(val text: String, val box: Box, val confidence: Double /* 1.0 if unavailable */, val frameId: String)
```

### 5.3 Lexicon (data, not code)
File: `domain/src/main/resources/lexicon/lexicon_ta_en.json`. The human (a native Tamil speaker) must be able to add variants without touching code. Load at startup. The app can also load an override copy from app storage (debug "reload lexicon"). Groups, with starting content:

- **numbers_ta**: 0 பூஜ்ஜியம்/ஜீரோ · 1 ஒன்று/ஒண்ணு/ஒரு/ஓர் · 2 இரண்டு/ரெண்டு · 3 மூன்று/மூணு · 4 நான்கு/நாலு · 5 ஐந்து/அஞ்சு · 6 ஆறு · 7 ஏழு · 8 எட்டு · 9 ஒன்பது · 10 பத்து · 11 பதினொன்று/பதினொண்ணு · 12 பன்னிரண்டு/பன்னெண்டு · 15 பதினைந்து/பதினஞ்சு · 20 இருபது · 25 இருபத்தைந்து/இருபத்தஞ்சு · 30 முப்பது · 50 ஐம்பது · 100 நூறு · 1000 ஆயிரம் · lakh லட்சம்/லக்ஷம் · crore கோடி
- **fractions_ta**: அரை 0.5 · கால் 0.25 · முக்கால் 0.75 · suffix "-அரை" (எட்டரை 8.5, நாலரை 4.5)
- **digits_ta**: ௦௧௨௩௪௫௬௭௮௯ → 0–9
- **numbers_en**: zero…twenty, thirty, forty, fifty, hundred, thousand, lakh, crore, point, half, "and a half"
- **percent_markers**: % · percent · per cent · pc · பர்சன்ட் · பெர்சன்ட் · பெர்சென்ட் · பர்சென்ட் · சதவீதம் · சதவிகிதம் · சதம் · p.a. · per annum · வருஷத்துக்கு
- **year_units**: year · years · yr · வருஷம் · வருடம் · ஆண்டு · இயர் (plus inflected: வருஷத்துல, வருடத்தில், வருஷம்)
- **month_units**: month · months · மாசம் · மாதம் · மந்த்
- **after_markers**: after · கழிச்சு · அப்புறம் · பிறகு · பின்
- **guarantee_words**: guaranteed · guarantee · assured · sure · fixed · கேரண்டி · கியாரண்டி · காரண்டி · கேரன்டி · கியாரன்டீ · உறுதி · உறுதியான · நிச்சயம் · நிச்சயமா · கண்டிப்பா
- **negation**: not · no · non · without · never · இல்லை · இல்ல · கிடையாது · கிடைக்காது · இல்லாத · "non-guaranteed" · "not guaranteed"
- **hedge**: up to · upto · around · approx · approximately · expected · projected · assumed · illustrative · may · might · could · maybe · சுமார் · கிட்டத்தட்ட · வரைக்கும் · வரை · வரலாம் · எதிர்பார்க்கலாம்
- **conditional**: if · suppose · இருந்தா · இருந்தால் · அப்படின்னா
- **lockin_words**: lock-in · lock in · lockin · லாக் இன் · லாக்கின்
- **liquidity_words**: withdraw · withdrawal · எடுக்கலாம் · எடுத்துக்கலாம் · வித்ட்ரா · surrender · சரண்டர் · "எப்ப வேணாலும்"
- **bundling_words**: loan · லோன் · கடன் + required · compulsory · mandatory · must · கட்டாயம் · "எடுத்தா தான்" · "இல்லாம loan கிடைக்காது"
- **charges_words**: charge · charges · fee · commission · சார்ஜ் · கட்டணம் · கமிஷன் + negation → "no charges"

**Important insight for ASR output:** Tamil ASR models often write English words in **Tamil script** ("guaranteed" → "கேரண்டி", "percent" → "பர்சன்ட்"). The lexicon must include those spellings. Matching is fuzzy: NFC-normalize, remove ZWJ/ZWNJ, lower-case Latin, then allow Levenshtein distance ≤ 1 for tokens of ≥ 5 code points (configurable). Exact match quality = 0.95, distance-1 match quality = 0.70.

**The "ஒரு" trap:** "ஒரு" means both "one" and "a". "ஆறு" also means "river". **Rule: a number word counts only if a unit word (percent / year / month / lakh / crore / rupees) is within 2 tokens.**

### 5.4 Normalizer (`normalize/`)
Pure functions with exhaustive unit tests:
- `parseNumber(tokens): BigDecimal?` handles Tamil words, colloquial forms, Tamil digits, English words, digits, compounds ("ஒரு லட்சம் இருபதாயிரம்" → 120000), and fractions.
- `parsePercent(window)`, `parseDurationMonths(window)` ("ஒரு வருஷம்" → 12, "அஞ்சு வருஷம்" → 60, "18 months" → 18).
- Disambiguation: "one year lock-in" → `LOCK_IN`. "one year கழிச்சு எடுக்கலாம்" → `LIQUIDITY.withdrawableAfterMonths = 12`. "policy term பத்து வருஷம்" → **no claim** (term is not lock-in).
- Any ambiguity returns null plus `NORMALIZATION_AMBIGUOUS`. **Never guess.**

### 5.5 Spoken extractor (`extract/SpokenExtractor`)
`fun extract(seg: AsrSegment): List<Observation>`
1. Tokenize (split on whitespace and punctuation, keep % attached handling).
2. For each claim type, find anchor keywords; take a ±6-token window.
3. Detect hedge, negation (±3 tokens around the anchor) and conditional in the window.
4. Build the typed value via the normalizer.
5. `confidence = matchQuality × seg.segmentQuality` (min of all matched anchors).
6. **GUARANTEE special rule:** a guarantee word with a clear negation within ±3 tokens → `Guarantee(false)`. A negation that is only a fuzzy match → the observation is marked `NEGATION_AMBIGUOUS` (the reconciler turns it into `UNCERTAIN`).
7. "FD மாதிரி" ("like an FD") alone creates **no** guarantee claim. Only explicit guarantee words do.
8. Conditional ("guaranteed-ஆ இருந்தா") → the observation is marked conditional and can never lead to `DIFFERS`.

### 5.6 Written extractor (`extract/WrittenExtractor` + `RowAssembler`)
The prop document is English. ML Kit returns table cells as separate lines, so first assemble rows:
- `RowAssembler`: group `OcrLine`s whose vertical centres are within 0.6 × median line height. Order by x. Join label cells with value cells.
- Regex rules per type (case-insensitive):
  - RATE: row contains `rate of return|return|interest|assumed|illustrat` plus ≥ 1 `\d+(\.\d+)?\s*%` → `Rate(set of %, ILLUSTRATIVE if doc contains illustrat|assumed|projected|non-guaranteed, else ASSERTED)`.
  - GUARANTEE: `not guaranteed|non-guaranteed|guaranteed returns?\s*:?\s*no` → false; `guaranteed returns?\s*:?\s*(yes|\d)` → true.
  - LOCK_IN: `lock[- ]?in( period)?` + `(\d+)\s*(years?|months?)`.
  - LIQUIDITY: `surrender value` + `nil|zero|not payable` + `(before|until|during).{0,30}?(\d+)(st|nd|rd|th)?\s*(policy )?years?` → `surrenderNilBeforeMonths`.
  - BUNDLING: `voluntary|not (a )?(mandatory|condition)|independent of (any )?loan` → `Bundling(false)`. Absent → nothing.
  - CHARGES: `(premium allocation|policy administration|mortality|surrender) charge|commission` + `%` or `₹`/`Rs`.
- Written confidence = min(OCR line confidences in the row) × format plausibility (percent 0–30, years 0–40; otherwise 0).

### 5.7 Reconciler (`reconcile/Reconciler`) — the heart
Deterministic state machine: `(ledger, event) → ledger`. Events: `SpokenObserved`, `WrittenObserved`, `DocumentScanCompleted`, `UserRecheck(type)`, `Reset`. The same events always give the same result. Keep an append-only event list (it feeds the receipt hash chain in §7.4).

**Latest-wins:** for each type, the most recent valid spoken observation is used. `mentionCount` counts how many times the same value was heard.

**Thresholds** (a data class, loadable from JSON; defaults below are starting points **to be calibrated** in §11):
```
SPOKEN_MIN = 0.55      // below → UNCERTAIN
SPOKEN_STRONG = 0.80   // DIFFERS allowed on a single mention only at or above this
WRITTEN_MIN = 0.70     // below → UNCERTAIN (+ "scan again" hint)
RATE_TOL_PP = 0.10     // percentage points
```

**Decision table (apply in order):**
1. No spoken observation → `PENDING`.
2. Spoken is conditional → `UNCERTAIN (CONDITIONAL)`.
3. Spoken confidence < `SPOKEN_MIN` or `NEGATION_AMBIGUOUS` or `NORMALIZATION_AMBIGUOUS` → `UNCERTAIN`.
4. Document scan not completed → `PENDING (NO_DOC_YET)`.
5. Written observations exist but all are below `WRITTEN_MIN` → `UNCERTAIN (WRITTEN_LOW_CONF)`.
6. No written observation of this type (document scan completed) → `NOT_IN_DOCUMENT (DOC_SILENT)`.
7. Compare by type:
   - **RATE:** spoken p is in the written set (± tolerance) → `MATCHES (VALUE_IN_SCENARIOS)`. Otherwise, if spoken is hedged (UP_TO/EXPECTED) → `UNCERTAIN (HEDGED)`. Otherwise → candidate `DIFFERS`.
   - **GUARANTEE:** equal booleans → `MATCHES`; different → candidate `DIFFERS`. (Spoken "guaranteed" vs written "not guaranteed" is the headline demo case.)
   - **LOCK_IN:** equal months → `MATCHES`; different → candidate `DIFFERS`.
   - **LIQUIDITY:** spoken `withdrawableAfterMonths = w` vs written `surrenderNilBeforeMonths = n` or lock-in months: w < n → candidate `DIFFERS`; otherwise `MATCHES`.
   - **BUNDLING:** spoken required=true vs written voluntary (false) → candidate `DIFFERS`. Written absent → handled by rule 6.
   - **CHARGES:** spoken "no charges" vs written any charge → candidate `DIFFERS`.
8. A candidate `DIFFERS` becomes `DIFFERS` only if: spoken is not hedged, not negation-ambiguous, AND (spoken confidence ≥ `SPOKEN_STRONG` OR `mentionCount ≥ 2`). Otherwise → `UNCERTAIN (NEEDS_CORROBORATION)`.
9. `UserRecheck(type)` sets `dismissed = true` (hidden) until a new observation of that type arrives.

**Card selection (one fact per screen):** show the most recent non-dismissed `DIFFERS`. If there is none, show the most recent `NOT_IN_DOCUMENT`. `MATCHES`, `PENDING` and `UNCERTAIN` never take the card. If more than one card is pending, show a small counter ("1 / 3") with a "next" control in the thumb zone.

### 5.8 Copy builder (`copy/`)
Turns a `LedgerEntry` into card text using string templates. The domain returns template keys + arguments; the app resolves them with `strings.xml`. Follow-up question per type (§8.4). No banned words anywhere.

### 5.9 Schema guard (structural enforcement of "no verdict")
A unit test uses reflection over every class in `app.vaakku.domain` and fails if any class, property or enum name matches:
`/(risk|score|severity|fraud|verdict|likelihood|suspicious|mislead|missell|mis_sell|guilty|accus)/i`
Say this in the pitch: "If anyone adds a verdict field, the build fails."

### 5.10 Tests and fixtures
- Unit tests for normalizer (≥ 40 cases, incl. colloquial Tamil), lexicon loading, RowAssembler, each extractor rule, each reconciler rule.
- **Fixture files** in `domain/src/test/resources/fixtures/*.json`:
```json
{ "id": "F01_demo_guarantee",
  "note": "Agent claims guarantee; doc says not guaranteed",
  "spoken": [ { "text": "Sir, இது FD மாதிரி தான். Guaranteed எட்டு percent return.", "startMs": 1000, "endMs": 5200, "segmentQuality": 0.9 } ],
  "written": [ { "text": "Assumed rate of return (illustrative)", "box": [40,300,520,330], "confidence": 0.95 },
               { "text": "4% p.a. and 8% p.a.", "box": [560,300,820,330], "confidence": 0.95 },
               { "text": "Returns are NOT guaranteed", "box": [40,350,520,380], "confidence": 0.95 } ],
  "documentScanCompleted": true,
  "expect": { "GUARANTEE": "DIFFERS", "RETURN_RATE": "MATCHES" } }
```
- **Required fixture set** (minimum 26):
  - 10 adversarial (table below; this is the acceptance test)
  - 6 demo-path cases (T01–T04 scripts vs the prop doc; see §11.2)
  - 6 normalization cases (Tamil numerals, colloquial forms, lakh/crore, "எட்டரை")
  - 4 honest-agent scripts that **must produce zero `DIFFERS`**
- **Fixture report task** `./gradlew :domain:fixtureReport` writes `evidence/fixture_report.md` with a confusion matrix (expected {MATCHES, NOT_IN_DOCUMENT, DIFFERS, SILENT} × actual). It prints **precision on DIFFERS** (must be 1.00) and recall on DIFFERS (reported, not required).

**Adversarial acceptance table (refined from the locked spec for precision):**
| # | Scenario | Expected |
|---|---|---|
| A1 | Agent says "not guaranteed"; ASR drops "not" but a fuzzy negation token remains nearby | GUARANTEE: SILENT (UNCERTAIN) |
| A2 | Agent self-corrects "lock-in மூணு வருஷம்… இல்ல இல்ல, அஞ்சு வருஷம்" vs doc 5 years | LOCK_IN: MATCHES (latest wins) |
| A3 | Hypothetical "guaranteed-ஆ இருந்தா எட்டு percent கிடைக்கும்" | GUARANTEE: SILENT (conditional) |
| A4 | Doc has both 4% and 8% illustrative; agent "8% return" | RETURN_RATE: MATCHES (in scenarios); GUARANTEE judged separately |
| A5 | "எட்டு சதவீதம்" vs doc "8%" | RETURN_RATE: MATCHES |
| A6 | "policy term பத்து வருஷம்" vs doc "lock-in 5 years" | LOCK_IN: PENDING (term ≠ lock-in; no claim) |
| A7 | OCR reads "40%" at confidence 0.4 | RETURN_RATE: SILENT (+ scan-again hint) |
| A8 | "up to எட்டு percent" vs doc only "4%" | RETURN_RATE: SILENT (hedged) |
| A9 | Segment quality 0.3 (noisy hall) with "guaranteed" | GUARANTEE: SILENT |
| A10 | "இந்த policy எடுத்தா தான் loan" vs doc silent on loans (scan completed) | BUNDLING: NOT_IN_DOCUMENT |

---

## 6. THE APP MODULE — specification

### 6.1 Build configuration
- `minSdk 31` (LiteRT NPU requirement), `compileSdk`/`targetSdk` 36, `abiFilters += "arm64-v8a"` only.
- `packaging { jniLibs { useLegacyPackaging = true } }` (Qualcomm NPU runtime and sherpa `.so` files).
- `buildTypes.release`: `isMinifyEnabled = false` (R8 can break JNI/reflection in sherpa/LiteRT; there is no time to debug keep-rules). Sign release with the debug key for the demo.
- **No `applicationIdSuffix` on debug.** The package must stay `app.vaakku`, because models live under that package's folder.
- Dependencies (**versions from `docs/versions_from_scratch.txt`; never upgrade mid-event**): Compose BOM + material3, activity-compose, lifecycle, CameraX (core, camera2, lifecycle, view — same version), `com.google.mlkit:text-recognition:16.0.1` (**bundled**, never the `play-services-mlkit-...` variant), LiteRT `com.google.ai.edge.litert:litert` (**exact version that matches the NPU runtime libraries**, see §6.5), sherpa-onnx AAR from `app/libs/`, kotlinx-serialization-json, kotlinx-coroutines, and `project(":domain")`.
- **No networking libraries. Ever.**

### 6.2 Manifest and permissions
- Permissions: `RECORD_AUDIO`, `CAMERA`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`, `POST_NOTIFICATIONS`.
- **`INTERNET` must not be in the merged manifest.** Libraries (for example ML Kit's transport/logging dependencies) can add `INTERNET` and `ACCESS_NETWORK_STATE` through manifest merging. Remove them explicitly:
  `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` (same for `ACCESS_NETWORK_STATE`).
- `scripts/check_manifest.sh`: run `processDebugMainManifest`, then grep the merged manifest (`app/build/intermediates/merged_manifests/...` or `apkanalyzer manifest permissions app-debug.apk`). Fail loudly if `INTERNET` appears. Run it in every gate.
- Session service: `<service android:name=".session.SessionService" android:foregroundServiceType="microphone" android:exported="false"/>`.

### 6.3 ASR subsystem (`asr/`)
- `AudioSource` interface → `MicAudioSource` (AudioRecord, 16 kHz, mono, PCM16 → FloatArray) and `WavAssetAudioSource` (plays a WAV from assets through the same pipeline; used for bake-off and the rehearsal fallback).
- **Audio never touches disk.** Keep a bounded in-memory ring buffer; release after decode. No "save audio" option, not even in debug.
- VAD: sherpa-onnx Silero VAD → speech segments (min 0.25 s, max ~8 s) → offline recognizer per segment ("simulated streaming", same pattern as sherpa's `SherpaOnnxSimulateStreamingAsr` Android example). `segmentQuality` = mean VAD speech probability × a clipped energy factor.
- `AsrEngine` interface with implementations, selectable in debug settings (unload the previous engine before loading the next; never keep two big models loaded):
  1. `SherpaWhisperTa` — Tamil fine-tuned Whisper small (`whisper-small-ta`, int8, `language="ta"`)
  2. `SherpaDolphinSmall` — `sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02`
  3. `SherpaDolphinBase` — `sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02` (faster, lower quality)
  4. `SherpaOmni300M` — `sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12`
  5. `AndroidOnDevice` — `SpeechRecognizer.createOnDeviceSpeechRecognizer()` with `ta-IN` or `en-IN`. It owns the mic, so it cannot run at the same time as `MicAudioSource`. Use a restart loop on end-of-speech.
- **Do not guess the sherpa-onnx Kotlin API.** Read the Kotlin API files that ship with the exact sherpa-onnx version in `app/libs/` (for example `OfflineRecognizer.kt`, `Vad.kt`). Model config classes differ per model family (Dolphin, Whisper, Omnilingual).
- Model files are loaded from absolute paths in `context.getExternalFilesDir("models")` = `/sdcard/Android/data/app.vaakku/files/models/<model-dir>/`. The human pushes them with `scripts/push_models.sh` (adb push). **Uninstalling the app deletes these folders.** Always install with `-r` (replace).
- `numThreads` default 4; configurable.
- Each segment emits `AsrSegment` → `SpokenExtractor` → reconciler events.

**Rung-0 probe screen (debug):** shows `SpeechRecognizer.isOnDeviceRecognitionAvailable()`, the result of `checkRecognitionSupport()` for `ta-IN` and `en-IN` (supported / installed / pending languages; API 33+), a button for `triggerModelDownload()` for `ta-IN`, and the default recognizer package (`Settings.Secure.getString(cr, "voice_recognition_service")`, may be null). There is an "Export" button that writes a text file to `Download/Vaakku/evidence/`.

**Bake-off screen (debug):** for every installed engine, runs every WAV in `assets/testaudio/`. Computes RTF (decode time ÷ audio duration) and **slot accuracy** by running the transcript through `SpokenExtractor` and comparing with `labels.json`. Shows a table and exports CSV to `Download/Vaakku/evidence/asr_bakeoff_<timestamp>.csv`. It also has a "live mic" mode that just shows transcripts and extracted claims.

### 6.4 OCR subsystem (`ocr/`)
- CameraX `Preview` + `ImageAnalysis` (feeds the NPU masker at ~256 px) + `ImageCapture` (full resolution for OCR).
- User taps **Scan** (bottom third of screen) → capture → ML Kit Latin recognizer (bundled) → `OcrLine` list (use line/element confidence if the API exposes it; otherwise 1.0) → `RowAssembler` + `WrittenExtractor` → `WrittenObserved` events.
- Save evidence: the full page image (**after** the NPU privacy mask, §6.5) and one crop per line that produced an observation (JPEG q80, max 800 px wide) → `files/sessions/<sessionId>/crops/`.
- "Done scanning" chip → `DocumentScanCompleted` event (required before any `NOT_IN_DOCUMENT` can appear). Multiple pages are allowed.
- Show the detected clauses right after a scan, as a short list, so the human can verify them.

### 6.5 NPU subsystem (`npu/`) — the one honest NPU workload
**Role in the product (real work, not decoration):** the **privacy masker**. A person-segmentation model runs on the Hexagon NPU on every camera analysis frame. Any person pixels (face, skin, hair, clothes) are masked in the live preview overlay and **blanked in every image saved into the evidence receipt**. The receipt contains the document, never a person. Pitch line: "The NPU masks every person in the camera view in X ms, so your evidence never contains anyone's face."

**Why this model:** the final spec allows any genuine NPU workload with latency on screen. Google's official LiteRT sample `image_segmentation/kotlin_npu/android_jit` already runs a person-segmentation model (`selfie_multiclass_256x256`) on Qualcomm NPUs with on-device (JIT) compilation. Re-using that *model* and following that sample's *integration pattern* removes the biggest integration risk. A text-region detector (EAST TFLite) for auto-capture is the **stretch** (N2), timeboxed.

**Steps:**
- **N0 Canary (human-assisted, P0/P2 time):** install Google's unmodified sample (built by the human last night) on the iQOO 15 via bundletool local testing. Capture logcat. If it runs on NPU, the NPU path works on OriginOS. Record the result in STATUS.md.
- **N1 Integrate:** copy the sample's **Gradle/runtime-library structure** (not its UI code) into our app: LiteRT dependency at the version pinned in the sample's `gradle/libs.versions.toml`, the `litert_npu_runtime_libraries` modules (`qualcomm_runtime_v81` is the one for SM8850), `device_targeting_configuration.xml`, and `useLegacyPackaging`. Load with:
  `CompiledModel.create(context.assets, "npu/selfie_multiclass_256x256.tflite", CompiledModel.Options(Accelerator.NPU, Accelerator.GPU))`.
  **Install path changes after N1:** the app must be built as an AAB and installed with `bundletool build-apks --local-testing` + `bundletool install-apks` (write `scripts/install_bundle.sh`). Keep `installDebug` only if you verify NPU still loads that way.
- **N2 (stretch, 3 h max):** EAST text detector on NPU → auto-capture when a document is steady in frame.
- **Benchmark screen:** runs the model 50× each with NPU, GPU and CPU options; shows median/p90 ms; exports CSV to evidence.
- **Honesty:** the latency label shows the accelerator that **actually** ran. Verify through logcat (dispatch/QNN lines) and record the logcat excerpt in `evidence/`. If NPU fails, the label says GPU or CPU. Never print "NPU" unless verified.
- **Fallback ladder:** NPU (JIT) → GPU (label says GPU) → CPU. The masker must keep working in all three.

### 6.6 Session, UI and Carbon Copy design (`session/`, `ui/`)
Follow `docs/KAAVAL_UI_DESIGN_SPEC_v2.md` and `docs/kaaval-tokens.css` for visuals, with these mappings: CONSISTENT→`MATCHES` (ink grey, low emphasis), UNSUPPORTED→`NOT_IN_DOCUMENT` (open field, dotted border), CONTRADICTED→`DIFFERS` (two stacked statements separated by a violet perforation). If those files are missing, use: paper `#FAF7F0`, ink `#1F1B2E`, muted ink `#6B6577`, rule `#D9D3C7`, stamp violet `#5E35B1`. **No red, amber or green anywhere.**

**Session Mode constraints:** alert text ≥ 34 sp (target 40 sp), contrast ≥ 7:1, one fact per screen, **no sound ever**, all session controls in the bottom third, light surface.

Fonts: human puts the subsetted font bundle into `app/src/main/res/font/`. Rename files to Android resource rules (lowercase, digits, underscores only), e.g. `noto_sans_tamil_bold.ttf`. Tamil text uses Noto Sans Tamil / Noto Serif Tamil; English and numbers use IBM Plex Sans/Serif/Mono.

**Screens:**
1. **Setup:** disclosure text (Evidence Mode, §8.4), checklist rows: airplane mode ON (`Settings.Global.AIRPLANE_MODE_ON`), mic and camera permission, ASR engine loaded (name), NPU status (accelerator actually used). **Start session** is disabled until airplane mode is ON. Debug builds only: long-press to override for development.
2. **Session:** top ~60% = Delta Card area. Empty state: "கேட்கிறது…" + counters "பேச்சு N · ஆவணம் M". Bottom third: **Scan document**, **Details**, **End session**. New card → one short haptic pulse (content-neutral "look at screen"; never a pattern that implies danger).
3. **Scan sheet:** camera preview + NPU mask overlay (subtle hatching) + small monospace latency label ("mask 7.9 ms · NPU") + Scan + Done scanning + list of clauses found.
4. **Details (ledger):** all six types with their state. Tap → **Provenance sheet:** spoken span with timestamp ("00:42 — …") next to the document crop image; the follow-up question; **மறுபரிசீலனை** (re-check) button.
5. **End / Receipt:** session ID, duration, entries, short hash head, **Export** (writes the receipt folder, §7), a static free-look reminder (§8.4), and a line telling the user where the folder is so Office Kit can pick it up.
6. **Debug overlay** (debug builds; toggle by three-finger long-press): engine, last segment RTF, decode ms, OCR ms, NPU ms + accelerator, thermal status (`PowerManager.getCurrentThermalStatus()`, `getThermalHeadroom(10)`), app memory, thresholds, last ReasonCode.
7. **Dev menu** (debug builds): Rung-0 probe, ASR bake-off, NPU benchmark, reload lexicon/thresholds from `Download/Vaakku/config/`, export transcripts (**text only**).

### 6.7 Foreground service and OriginOS survival
- `SessionService` (type microphone) starts when a session starts (the app must be in the foreground at that moment). It owns the ASR pipeline; the UI observes a `StateFlow<SessionState>`.
- Notification text: "அமர்வு நடைபெறுகிறது — ஒலி சேமிக்கப்படாது / Session active — audio is not stored".
- OriginOS may still kill background work. The human sets battery exemptions (human tasks list). The app shows a one-time hint on the Setup screen.

---

## 7. RECEIPT, HASH CHAIN AND GRIEVANCE PACKET

### 7.1 Hash chain (domain, [R-OK])
- Canonical JSON (sorted keys, no whitespace, UTF-8) for every reconciler event.
- `h0 = SHA-256(sessionId | startEpochMs | appVersion)`; `h_i = SHA-256(h_{i-1} || canonical(event_i))`.
- `ReceiptBuilder` produces the receipt object: `{ sessionId, appVersion, deviceModel, startedAt, endedAt, events[], hashes[], head, entries[] (final ledger with provenance, crop file names) }`.
- Tests: tamper any byte → verification fails; reorder events → fails.

### 7.2 Signing (app, [G])
- Android Keystore EC P-256 key, alias `vaakku_receipt`. Try `setIsStrongBoxBacked(true)`; on `StrongBoxUnavailableException`, retry without it (TEE). Set `setAttestationChallenge(h0 bytes)`.
- Sign `head` with `SHA256withECDSA`. Add `signature` (base64 DER), `certChain` (base64 DER list from `keyStore.getCertificateChain`), `strongBox: true/false`.

### 7.3 Export (app, [G])
- Write via MediaStore Downloads (no storage permission needed on API 29+): `Download/Vaakku/<sessionId>/receipt.json`, `crops/*.jpg`, `page_*.jpg` (masked), and `Download/Vaakku/<sessionId>.zip`.
- **No audio, ever.**

### 7.4 Packet CLI (Node.js, `tools/packet-cli/`, [R-OK])
- `node tools/packet-cli/index.js --in <receipt folder or zip> --out <packet.pdf>`
- Step 1: verify the hash chain and ECDSA signature (Node `crypto`, public key from the first cert). Print `INTEGRITY: PASSED` or `INTEGRITY: FAILED (<reason>)`. The packet is still rendered on FAILED, but with the failure printed at the top.
- Step 2: render HTML (Tamil + English; embed Noto Sans Tamil via `@font-face` from `app/src/main/res/font/`) → PDF with the laptop's own headless Edge/Chrome (`msedge --headless=new --print-to-pdf=... file.html`; auto-detect `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` or Chrome). Headless browsers shape Tamil correctly; most pure-JS PDF libraries do not.
- Packet sections: cover ("A record of statements. Not a finding about any person."), session details, one row per entry (spoken quote + timestamp | document crop + extracted clause | state label), integrity section (hash head, signature status, StrongBox yes/no), where to take this (neutral facts: the insurer's grievance officer, IRDAI's Bima Bharosa portal, Insurance Ombudsman; the human verifies URLs), and the free-look note.
- Minimal dependencies (ideally none beyond Node built-ins plus `yauzl`/`adm-zip` for zip). The human runs `npm install` at event time on the laptop.

---

## 8. COPY (all user-visible text)

### 8.1 Rules
All Tamil strings live in `app/src/main/res/values/strings.xml`, grouped and commented `<!-- TAMIL-REVIEW -->`. A native Tamil speaker (the human) reviews every one before the demo. English secondary lines are small, for judges.

### 8.2 Card templates
- DIFFERS: line 1 `பேச்சில்: {spoken}` · line 2 `ஆவணத்தில்: {written}` · footer `சரிபார்க்கவும்` · small English: `Said: {spokenEn} · Document: {writtenEn}`
- NOT_IN_DOCUMENT: line 1 `அவர் சொன்னது: {spoken}` · line 2 `ஆவணத்தில் இது இல்லை — கேளுங்கள்`
- State labels: MATCHES `பொருந்துகிறது` · NOT_IN_DOCUMENT `ஆவணத்தில் இல்லை` · DIFFERS `வேறுபடுகிறது`

### 8.3 Value phrases (Tamil draft → English)
| Key | Tamil draft | English |
|---|---|---|
| `v_guaranteed_pct` | உறுதியான {x}% | guaranteed {x}% |
| `v_not_guaranteed` | உறுதி இல்லை | not guaranteed |
| `v_illustrative_pcts` | {list}% (உதாரணம் மட்டும்) | {list}% (illustration only) |
| `v_years` | {n} வருடம் | {n} years |
| `v_months` | {n} மாதம் | {n} months |
| `v_withdraw_after` | {n} வருடத்துக்குப் பிறகு எடுக்கலாம் | can withdraw after {n} years |
| `v_surrender_nil_before` | {n} வருடத்துக்கு முன் சரண்டர் மதிப்பு இல்லை | no surrender value before {n} years |
| `v_required_for_loan` | கடனுக்கு இது கட்டாயம் | required for the loan |
| `v_voluntary` | விருப்பத்தின் பேரில் மட்டும் | voluntary |
| `v_no_charges` | கட்டணம் இல்லை | no charges |
| `v_charge_pct` | {x}% கட்டணம் | {x}% charge |

### 8.4 Other strings
- Follow-up (GUARANTEE): `"உறுதி" என்று ஆவணத்தில் எங்கே உள்ளது என்று கேளுங்கள்` ("Ask: where does the document say guaranteed?")
- Follow-up (LOCK_IN / LIQUIDITY): `இந்தக் காலம் ஆவணத்தில் எங்கே உள்ளது என்று கேளுங்கள்`
- Follow-up (BUNDLING): `கடனுக்கு இது கட்டாயம் என்பது எழுத்தில் உள்ளதா என்று கேளுங்கள்`
- Follow-up (RATE / CHARGES): `இந்த எண் ஆவணத்தில் எங்கே உள்ளது என்று கேளுங்கள்`
- Disclosure (Evidence Mode): `இந்த உரையாடலில் சொல்லப்படும் தகவல்கள் குறிக்கப்படுகின்றன. ஒலி சேமிக்கப்படாது.` ("Statements in this conversation are being noted. Audio is not stored.")
- Free-look: `பாலிசி ஆவணம் கிடைத்த நாளிலிருந்து 30 நாட்கள் ஃப்ரீ-லுக் காலம் உள்ளது.` ("You have a 30-day free-look period from the day you receive the policy document.")
- Listening: `கேட்கிறது…` · Re-check: `மறுபரிசீலனை` · Next: `அடுத்து` · Scan: `ஆவணத்தை ஸ்கேன் செய்` · Done scanning: `ஸ்கேன் முடிந்தது`

---

## 9. AUTOMATED GUARDS (run in every gate)
1. `./gradlew :domain:test` — all green.
2. `./gradlew :domain:fixtureReport` — precision on DIFFERS = 1.00.
3. Schema guard test (§5.9) — inside `:domain:test`.
4. `./gradlew checkBannedWords` — custom task scanning `app/src/main/res/**/strings*.xml`, `app/src/main/java/**`, `domain/src/main/**` and `tools/packet-cli/**` for the §2.4 list as whole words, case-insensitive. The allow-list is only the file that defines the list itself and test files.
5. `scripts/check_manifest.sh` — no `INTERNET`.
6. `./gradlew :app:assembleDebug` (or the bundle build after N1) — succeeds, with the log line saved in the gate evidence.

---

## 10. RED LIGHT PLAYBOOK
The human records the organizers' ruling in `STATUS.md` under "Red Light ruling". Three possible rulings:
- **Ruling A — laptop may keep running if operated only from the phone** (Office Kit Remote PC, or Claude Code Remote Control from the Claude mobile app): continue only **[R-OK]** tasks (domain, fixtures, packet CLI, docs). **No Android Gradle builds** unless the ruling explicitly allows building.
- **Ruling B — laptop must be fully idle:** no Claude Code on the laptop. If the human pre-tested it, [R-OK] domain work may continue in Claude Code on the web (cloud) against the GitHub repo, and is merged during Green Light.
- **Always (both rulings):** the human uses Red Light for phone testing: bake-off live mic, OCR scans, end-to-end runs, soak test, Tamil copy review, demo rehearsal, footage. All measurements go into the phone's notes or the app's evidence exports and are pasted into `STATUS.md` at the next Green Light.

**Before each Red Light:** build, install and smoke-test the newest APK; export a checklist of what the human should test in that window (write it into STATUS.md "Next Red Light test list").

---

## 11. TESTING, CALIBRATION AND EVIDENCE

### 11.1 Test audio (recorded by the human last night)
`testdata/testaudio/*.wav` (16 kHz, mono, 16-bit PCM) and `testdata/testaudio/labels.json`. Gradle copies them into `app/src/main/assets/testaudio/` (via `sourceSets` or a copy task). The Python pre-screen reads them directly.

### 11.2 Labels (create `labels.json` from this table; the human may reword a script line but keeps these values)
| File | Script (as recorded) | Expected spoken claims |
|---|---|---|
| T01_guarantee_fd | Sir, இது FD மாதிரி தான். Guaranteed எட்டு percent return. | GUARANTEE=true; RATE={8} ASSERTED |
| T02_lockin_liquidity | Lock-in ஒரு வருஷம் தான் sir. One year கழிச்சு full-ஆ எடுக்கலாம். | LOCK_IN=12; LIQUIDITY.withdrawAfter=12 |
| T03_bundling | இந்த policy எடுத்தா தான் loan sanction ஆகும் sir, இது compulsory. | BUNDLING=true |
| T04_charges | Charges எதுவும் இல்லை sir, zero commission. | CHARGES none |
| T05_honest_rate | Return guarantee இல்லை sir. Illustration-ல நாலு percent, எட்டு percent ரெண்டு scenario இருக்கு. | GUARANTEE=false; RATE={4,8} ILLUSTRATIVE |
| T06_honest_lockin | Lock-in அஞ்சு வருஷம். அதுக்கு முன்னாடி surrender value கிடையாது. | LOCK_IN=60; LIQUIDITY.nilBefore=60 |
| T07_selfcorrect | Lock-in மூணு வருஷம்… இல்ல இல்ல, அஞ்சு வருஷம். | LOCK_IN=60 (latest) |
| T08_hedge | Up to எட்டு percent வரைக்கும் வரலாம் sir. | RATE={8} UP_TO |
| T09_conditional | Guaranteed-ஆ இருந்தா எட்டு percent கிடைக்கும், ஆனா இது guaranteed இல்லை. | GUARANTEE=false (conditional part ignored) |
| T10_english | It's like a fixed deposit, guaranteed eight percent, and you can withdraw after one year. | GUARANTEE=true; RATE={8}; LIQUIDITY.withdrawAfter=12 |
| T11_numbers | Premium வருஷத்துக்கு ஒரு லட்சம் இருபதாயிரம், term பத்து வருஷம். | no LOCK_IN; (number parse test: 120000) |
| T12_formal_tamil | இது உறுதியான எட்டு சதவீத வருமானம் தரும். | GUARANTEE=true; RATE={8} |
| T13_noisy_T01 | (T01 with background chatter/fan) | same as T01 |
| T14_noisy_T02 | (T02 with background chatter/fan) | same as T02 |
| R01_demo_pitch | T01 + T02 + T03 (+ T04) concatenated (create with a script) | union of those |

**Prop document** (printed English benefit illustration, fictional product): Assumed rate of return (illustrative) 4% p.a. and 8% p.a.; "Returns are NOT guaranteed"; "Guaranteed returns: No"; Lock-in period: 5 years; Surrender value: Nil before completion of 5th policy year; Premium allocation charge: 5% in year 1; Policy term 10 years; Annualised premium ₹1,20,000; **silent on loans**.
**Expected demo result (R01 vs prop):** GUARANTEE **DIFFERS** · RETURN_RATE **MATCHES** (8% is one of the illustrated scenarios) · LOCK_IN **DIFFERS** (1 vs 5 years) · LIQUIDITY **DIFFERS** (withdraw after 1 year vs nil before 5) · BUNDLING **NOT_IN_DOCUMENT** · CHARGES **DIFFERS** (none vs 5%).
**Honest run (T05 + T06 vs prop):** zero DIFFERS; RATE/GUARANTEE/LOCK_IN/LIQUIDITY MATCHES.

### 11.3 Calibration loop (numbers the human reads; you tune)
1. Laptop pre-screen transcripts → `:domain:evalTranscripts` → slot accuracy per engine.
2. Phone bake-off CSV (RTF + slot accuracy) + human live-mic scorecard → choose the engine (§13, G2).
3. Set `SPOKEN_STRONG` so that **every honest-script run gives zero DIFFERS** and the demo runs still produce their DIFFERS. If both cannot be true, prefer zero false DIFFERS and ask the agent actor to repeat key claims ("guaranteed… ஆமா sir, guaranteed").
4. Every real transcript the human exports becomes a new "field fixture" (with the human's expected labels) so regressions are caught.

### 11.4 Evidence conventions
- Screenshots: `adb exec-out screencap -p > evidence/<gate>_<what>.png`
- Logs: `adb logcat -d | grep -iE "litert|qnn|dispatch|vaakku" > evidence/<gate>_logcat.txt`
- Human measurements: pasted into `STATUS.md` as `MEASUREMENT <gate>: ...` lines.

### 11.5 Performance budgets (targets, record actuals)
ASR decode RTF ≤ 0.5 · end of speech → card ≤ 2.5 s · scan → clauses ≤ 1.5 s · NPU mask ≤ 10 ms/frame (whatever is real is shown) · app memory < 1.5 GB · thermal status ≤ MODERATE after 15 min.

---

## 12. WORKING AGREEMENTS FOR CLAUDE CODE

### 12.1 Process
- One phase per session. At the end of a phase: update STATUS.md (gate table, evidence paths, decisions, handoff notes, next steps), commit, then tell the human "Phase done — start a new session with the RESUME prompt."
- Commit after every green step: `git commit -m "P1.4 reconciler rules + tests"`.
- Before every commit: §9 guards 1–4. Before every install: guard 5 and 6.
- Never: `git reset --hard`, deleting `models/`, `adb uninstall`, upgrading dependencies, adding network libraries, removing tests to make a gate pass, marking a gate passed without evidence.
- If a task fails twice with the same error, stop and write a short diagnosis for the human to take to Claude chat.

### 12.2 Optional two-terminal mode (speeds up the build)
- **Terminal A (domain owner):** only edits `domain/**`, `tools/**`, `testdata/labels.json`. Runs `:domain:*` tasks. Does not commit; Terminal B commits.
- **Terminal B (app owner):** owns everything else, including Gradle root files, and commits for both.
- Never let both terminals edit the same file.

### 12.3 HUMAN ACTION block format
```
=== HUMAN ACTION NEEDED ===
WHAT:   <one line>
WHY:    <one line>
STEPS:  1. ...  2. ...  3. ...
REPORT: <exactly what to paste back, e.g. "PASS/FAIL + screenshot path" or a MEASUREMENT line>
===========================
```

### 12.4 STATUS.md template (create in P0)
```
# STATUS — VAAKKU
Current light: GREEN | RED      Current phase: P?      Hour: H+?
Red Light ruling: <A / B / unknown> — <organizer quote>
Name ruling: <VAAKKU allowed? / keep KAAVAL>
Originality ruling: <organizer quote>

## Gates
| Gate | Status (NOT STARTED / IN PROGRESS / PASSED / FAILED / CUT) | Evidence | Time |
|---|---|---|---|
| G0 Bootstrap | | | |
| G1 Domain | | | |
| G2 ASR decision | | | |
| G3 OCR | | | |
| G4 NPU | | | |
| G5 End-to-end | | | |
| G6 Go/No-Go | | | |
| G7 Receipt + Office Kit | | | |
| G8 Freeze | | | |

## Decisions log
## Measurements (from the human)
## Open issues
## Next Red Light test list
## Handoff notes for the next session
```

---

## 13. PHASES AND GATES (target timeline; H = hours from build start)
Shift build tasks into Green windows and testing into Red windows as the organizers' schedule dictates. If there are fewer hacking hours than planned, compress P6/P7, never P5, and freeze no later than 3 hours before the submission deadline.

### P0 — Bootstrap [G] (H0–H1)
Create the repo, `.gitignore`, Gradle multi-module (`domain` JVM, `app` Android), versions from `docs/versions_from_scratch.txt`, `local.properties` with the human-provided SDK path, manifest per §6.2, fonts copied and renamed, a blank Compose Setup screen using Tamil fonts, `checkBannedWords` task, `check_manifest.sh`, `push_models.sh`, the Rung-0 probe screen, and `STATUS.md`. Build, install, launch.
**G0 evidence:** assembleDebug log tail; install success; `evidence/G0_setup.png`; manifest check output (no INTERNET); Rung-0 probe export.

### P1 — Domain core [R-OK] (H1–H5) — Terminal A
§5 in full: types, lexicon JSON, normalizer, extractors, RowAssembler, reconciler, thresholds, copy keys, schema guard, fixtures (≥ 26), `fixtureReport`, `evalTranscripts` (reads `evidence/asr_prescreen/**` transcripts + `labels.json` → slot accuracy per engine).
**G1 evidence:** `:domain:test` output; `evidence/fixture_report.md` with DIFFERS precision 1.00.

### P2 — ASR [G] (H0.5–H6) — Terminal B
H0.5–H1.5: `tools/asr_prescreen/prescreen.py` on the laptop (`pip` sherpa-onnx is pre-installed): run every model over every WAV, write transcripts + laptop RTF to `evidence/asr_prescreen/<engine>/<file>.txt`.
Then the app: AudioSource, VAD, AsrEngine implementations, bake-off screen, live-mic screen, push_models flow.
**G2 (H+6) decision:** STATUS.md records the chosen primary engine, the fallback engine, and the demo audio mode (live mic primary + rehearsal WAV backup, or rehearsal primary). Evidence: prescreen slot-accuracy table, phone bake-off CSV, human live-mic scorecard.
**Decision rule:** pick the engine with the highest slot accuracy on T01–T14 whose phone RTF ≤ 0.5. Tie → the smaller model. If no engine gets ≥ 70% slot accuracy on clean clips, the demo uses rehearsal WAV as primary and live mic as a "try it" moment.

### P3 — OCR [G] (H6–H9)
§6.4 in full. Human scans the prop 5 times.
**G3 evidence:** all expected clauses (RATE {4,8} ILLUSTRATIVE, GUARANTEE false, LOCK_IN 60, LIQUIDITY nil-before 60, CHARGES 5%) extracted in ≥ 4 of 5 scans; screenshot of the clause list; CSV.

### P4 — NPU [G] (H7–H12, timebox 5 h total)
N0 result must already be in STATUS.md. N1 integration, masker on preview + saved images, benchmark screen.
**G4 evidence:** benchmark CSV (NPU/GPU/CPU); logcat excerpt proving NPU dispatch; screenshot of the latency label. **If NPU is not achieved by the timebox:** ship GPU with an honest label, record the failure, move on.

### P5 — Integration [G] (H9–H16)
SessionService, Setup (airplane gate), Session screen, Delta Card, Details, Provenance sheet, re-check, follow-up questions, haptic, debug overlay, counters, card queue.
**G5 evidence:** (a) R01 rehearsal WAV + prop doc → expected results from §11.2 in 5 of 5 runs; (b) T05+T06 honest run → **zero DIFFERS** in 3 of 3 runs; (c) the same with live speech from the human, results recorded; screenshots of the DIFFERS card, the NOT_IN_DOCUMENT card and the provenance sheet.

### G6 — GO / NO-GO (H+17)
Checklist: G0–G5 passed? False DIFFERS observed anywhere? Live ASR usable? NPU state? Decide cuts: anything not green is cut or pitch-only. Write the decision in STATUS.md. The human brings STATUS.md to Claude chat.

### P6 — Receipt + Office Kit [domain/CLI R-OK; app G] (H12–H21)
Terminal A may build §7.1 and §7.4 early (from H5). Terminal B builds §7.2–7.3 after G6. Human rehearses the Office Kit beat.
**G7 evidence:** `packet.pdf` produced from a real phone session; CLI prints `INTEGRITY: PASSED`; tamper test prints `FAILED`; Office Kit round trip timed (target ≤ 60 s).

### P7 — Hardening [G] (H21–H24)
15-minute soak test (human), thermal/battery readings, OriginOS survival check, crash review from logcat, release build (minify off) installed through the final install path, three full airplane-mode demo runs.
**G8 = FEATURE FREEZE (H+24):** after this, bug fixes only, and only with the human's approval.

### P8 — Demo and submission (H24–end)
No features. Fix only demo-breaking bugs. Help the human with real numbers for the deck (from evidence), screen recordings via `adb shell screenrecord` if needed, and a clean README describing how it works and what runs where.

---

## 14. SCOPE (from the locked spec)
- **MUST:** P0, P1, P2, P3, P4 (with honest fallback), P5, P7; visible privacy (airplane gate + no INTERNET).
- **SHOULD:** hash-chained signed receipt + packet CLI (P6), follow-up question, haptic, free-look note, debug overlay thermal.
- **PITCH-ONLY:** Gemma/LiteRT-LM enrichment, full verbatim Tamil transcription (if the G2 gate says so), family handoff, generalisation to other domains.
- **CUT:** custom NPU model export, NLI/entailment models, dual-pass simultaneous ASR, any LLM tier, any verdict/score/risk feature.

---

## 15. RISK REGISTER (quick reference)
| Risk | Early signal | Fallback |
|---|---|---|
| Tamil ASR poor in the hall | G2 scorecard < 70% | Rehearsal WAV primary; live mic as a "try it" moment; English engine for T10-style lines |
| NPU blocked on OriginOS | N0 canary fails | GPU with honest label; show benchmark CPU vs GPU |
| OriginOS kills the session | Soak test stops | Battery exemptions; keep screen on during session; foreground service |
| OCR misses table rows | G3 < 4/5 | Better lighting, phone stand, bigger prop font; tune RowAssembler tolerance |
| False DIFFERS on honest speech | Any honest-run DIFFERS | Raise `SPOKEN_STRONG`; require 2 mentions; add the phrase as a fixture |
| Models wiped | Engine fails to load after install | Never uninstall; re-run `push_models.sh` |
| Wi-Fi down at venue | Gradle sync fails | Dependencies pre-cached last night (scratch project); `--offline` flag |
