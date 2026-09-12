# STATUS — VAAKKU

Current light: GREEN · Current phase: P0 · Hour: H0–H1

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
| G0 Bootstrap | IN PROGRESS | see below | H0–H1 |
| G1 Domain | NOT STARTED | — | — |
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
| Install success | pending (needs phone) | — |
| Setup screen screenshot | pending (needs phone) | `evidence/G0_setup.png` |
| Rung-0 probe export | pending (needs phone) | `evidence/` (exported from the app) |

Build was green on a clean `:app:assembleDebug` (40 tasks) with Gradle 9.3.1 / AGP
8.13.1 / Kotlin 2.3.0 on a JDK 21 daemon at Java 11 target. `checkBannedWords` is
clean over 20 files, with zero Kotlin warnings. The manifest guard passes against both
the merged manifest and the APK, and `evidence/G0_manifest_check.txt` records the
manifest-merger REJECTED lines proving the two `tools:node="remove"` declarations are
load-bearing rather than passing vacuously. Debug APK is 51 MB.

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

## Measurements (from the human)

None yet. The Rung-0 probe result will be the first entry and should be pasted here
verbatim from the exported file.

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
5. **The phone was not attached at build time** (`adb devices` empty). Install,
   launch, screenshot and the Rung-0 probe run are all human actions. `scripts/install.sh`
   and `scripts/push_models.sh` are written and ready for it.
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

## Next Red Light test list

Not yet applicable — no Red Light ruling has been issued, and no build has been
installed. Once the phone is attached and `scripts/install.sh` has run:

1. Launch the app. Confirm the Tamil renders in Noto Sans Tamil, **not tofu boxes** —
   check the title, the six domain labels, and the long offline help line.
2. Confirm nothing clips. Tamil runs ~30% longer than English; the domain cells are
   60dp and the labels are the most likely thing to overflow.
3. Tap through all six domain cells. The selected cell inverts to ink fill; the source
   line beneath the grid should change with each.
4. Type in the counterparty field. Confirm the Tamil hint disappears and the underline
   is visible.
5. Grant mic and camera. Confirm the row flips to அனுமதிக்கப்பட்டது without a restart.
6. Toggle airplane mode ON. Confirm the amber offline chip appears and **Start enables**;
   toggle it off and confirm Start disables again (this is the offline proof).
7. Tap the offline row. Confirm it opens the real airplane-mode settings panel.
8. Long-press the debug override line with airplane mode off. Confirm Start enables and
   the "override armed" line appears — then confirm this line is absent when the radio
   is on.
9. Read the accelerator row. It must say அளக்கப்படவில்லை (not measured). If it ever
   shows an accelerator name before G4, that is a CLAUDE.md #8 violation.
10. Long-press the screen title → Dev menu → run the Rung-0 probe → export it.
11. Screenshot for `evidence/G0_setup.png`.

## Handoff notes for the next session

- Read `CLAUDE.md` → `STATUS.md` → `docs/VAAKKU_BUILD_PLAN.md`, in that order.
- `:domain` has no source yet. P1 writes the domain core and the fixtures; the
  `fixtureReport` task is already wired and will start printing real numbers once
  fixtures exist.
- The Rung-0 result decides the ASR plan. If `isOnDeviceRecognitionAvailable()` is
  false, engine 5 (`AndroidOnDevice`) is out and Rung 1 (sherpa-onnx) carries
  everything — which is the expected outcome and is why the models are already
  downloaded.
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
- **`checkBannedWords` scans comments too.** Two of its first findings were prose in a
  doc comment ("risk ramp", "never a verdict"), not product strings. The guard was
  right both times. Reword the comment; never touch the list.
