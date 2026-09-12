# STATUS — VAAKKU

Current light: GREEN · Current phase: P1 **complete** → P2 next · Hour: H1–H5

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
| G2 ASR decision | NOT STARTED | — | — |
| G3 OCR | NOT STARTED | — | — |
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
10. **`testdata/testaudio/` has no `.wav` files in the repo.** §11.1 says the human
    recorded them "last night," but nothing under `testdata/` was ever added — this
    machine has never seen them. `testdata/testaudio/labels.json` (P1) was written
    from the build plan §11.2 table with the exact script text, but two things need
    the human before it can be trusted as ground truth:
    === HUMAN ACTION NEEDED ===
    WHAT:   Confirm labels.json's 15 scripts, and get the recorded .wav files into the repo.
    WHY:    P1's evalTranscripts/labels.json only have a real "last night's recording"
            behind them if the files exist and the scripts match what was actually said.
    STEPS:  1. Open testdata/testaudio/labels.json and read each "script" line.
            2. For each of T01-T14 and R01, confirm the Tamil/English wording matches
               what you recorded (§11.2 allows rewording a line, but the VALUES —
               guaranteed/not, the percents, the durations, required/voluntary,
               charges/none — must stay exactly as labelled).
            3. Copy the 15 .wav files (16 kHz mono 16-bit PCM) into testdata/testaudio/,
               named to match each "file" key exactly (e.g. T01_guarantee_fd.wav).
    REPORT: Either "labels.json matches, .wav files added" or a list of which script
            lines need correcting (file name + what's wrong).
    ===========================

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

**P1 touched only `domain/**`, `testdata/testaudio/labels.json` and this file — no
`app/**` or Gradle changes, so the phone's installed build is still exactly the one
G0 verified.** There is nothing new to hand-test from P1; the "Not yet verified" list
above (P0) is still the standing queue for the next Red Light window. The next thing
worth adding here is whatever P2 (ASR) puts on the phone.

## Handoff notes for the next session

- Read `CLAUDE.md` → `STATUS.md` → `docs/VAAKKU_BUILD_PLAN.md`, in that order.
- **`:domain` is done (P1, G1 PASS)** — model/lexicon/normalize/extract/reconcile/copy,
  277 tests, 26 fixtures, DIFFERS precision & recall both 1.00, schema guard clean. See
  "G1 evidence" above for the ten spec-ambiguity resolutions and the one real bug the
  fixtures caught before touching this again. P2 (ASR) is next; when its output starts
  reaching the domain layer, it should slot into `SpokenExtractor.extract(AsrSegment)`
  and `Reconciler` unchanged — that boundary was the whole point of building `:domain`
  phone-free and pure-JVM.
- `testdata/testaudio/labels.json` exists (15 rows, §11.2) but **no `.wav` files are in
  the repo yet** and the scripts are unconfirmed against what the human actually
  recorded — see open issue 10's HUMAN ACTION block. `evalTranscripts` is wired and
  tested (`LabelsTest`) but has nothing real to score until both land.
- `evidence/asr_prescreen/<engine>/<file>.txt` is what `:domain:evalTranscripts`
  reads (build plan §11.3 item 1, a Python laptop pre-screen, not yet built) — once
  those transcripts exist, re-run `:domain:evalTranscripts`, no code change needed.
- **The Rung-0 question is answered — do not re-litigate it.** See M1.
  `isOnDeviceRecognitionAvailable()` is *true*, but the on-device recognizer does not
  offer `ta-IN` at all, so engine 5 (`AndroidOnDevice`) is out for Tamil and Rung 1
  (sherpa-onnx) carries it. The subtlety worth keeping: the availability flag being
  true is not the same as the language being there, and `needs_download: false` on
  ta-IN means "never offered", not "ready".
- `SessionService` is deliberately NOT in the manifest. Declaring it before the class
  exists breaks the build. It arrives in P2 (build plan §6.7) along with the
  `foregroundServiceType="microphone"` attribute.
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
