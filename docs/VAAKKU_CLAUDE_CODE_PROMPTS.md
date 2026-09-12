# VAAKKU — Claude Code Prompt Queue

## How to use this file
1. Before the first prompt, create the repo folder (for example `C:/Users/clash/Documents/VAAKKU/vaakku`). Put these files in it:
   - `CLAUDE.md` → repo root
   - `docs/VAAKKU_BUILD_PLAN.md`
   - `docs/VAAKKU_FINAL_LOCKED_SPEC.md` (the final reconciliation research file, renamed)
   - `docs/VAAKKU_NOVELTY_RESEARCH.md` and `docs/FABLE_HARDENING_PLAYBOOK.md` (the two research reports; background only)
   - `docs/KAAVAL_UI_DESIGN_SPEC_v2.md`, `docs/kaaval-tokens.css`, `docs/kaaval-fonts.zip`
   - `docs/versions_from_scratch.txt` (versions you noted from last night's scratch project)
   - `testdata/testaudio/*.wav` (your recordings)
   - Models extracted into `models/` (see the human task list)
   - `app/libs/sherpa-onnx-<version>.aar`
2. Open a terminal in the repo folder, run `claude`, and paste **PROMPT 0**.
3. After each phase ends, **start a fresh session** (type `/clear` or open a new terminal), paste the **RESUME** prompt, then the next phase prompt.
4. Anything in `<angle brackets>` is for you to fill in.
5. If a phase fails twice, copy the relevant part of STATUS.md into Claude chat and ask for a fix prompt.

Optional speed-up: two terminals. **Terminal A** runs P1 → P6-A (domain + tools only). **Terminal B** runs P0 → P2 → P3 → P4 → P5 → P6-B → P7. Start Terminal A only after P0 is committed.

---

## PROMPT 0 — Kickoff + Phase P0 (Bootstrap) [GREEN]

```
You are the implementation engineer for VAAKKU, a hackathon Android app. You have no memory of earlier work, so everything you need is in files.

READ FIRST, fully and in this order:
1. CLAUDE.md
2. docs/VAAKKU_BUILD_PLAN.md (the specification; it overrides every other doc)
3. docs/VAAKKU_FINAL_LOCKED_SPEC.md (skim; background)
4. docs/versions_from_scratch.txt
Do not read or copy any code from outside this repository.

Then do Phase P0 exactly as described in §13 "P0 — Bootstrap" and the sections it depends on (§4 layout, §6.1 build config, §6.2 manifest, §6.6 fonts, §9 guards, §12.4 STATUS.md template, §6.3 Rung-0 probe screen).

Specifically:
1. git init. Create .gitignore per §4.
2. Gradle multi-module project: :domain (Kotlin JVM library, JUnit 5) and :app (Android application, Compose). Use EXACTLY the versions in docs/versions_from_scratch.txt. minSdk 31, compileSdk/targetSdk 36, arm64-v8a only, package app.vaakku, no debug applicationIdSuffix, release minify OFF.
3. local.properties with sdk.dir = <PASTE YOUR ANDROID SDK PATH, e.g. C:\\Users\\clash\\AppData\\Local\\Android\\Sdk>.
4. Manifest per §6.2, including tools:node="remove" for INTERNET and ACCESS_NETWORK_STATE.
5. Extract docs/kaaval-fonts.zip into app/src/main/res/font/, renaming files to valid resource names (lowercase, digits, underscores). Create FontFamily objects for Noto Sans Tamil, Noto Serif Tamil, IBM Plex Sans/Serif/Mono.
6. A minimal Setup screen (Compose) in the Carbon Copy style showing: app name from R.string.app_name, the Tamil disclosure string from §8.4, and the checklist rows from §6.6 (airplane mode status must be live; the others can say "pending" for now).
7. Debug-only Dev menu with the Rung-0 probe screen from §6.3 (on-device recognizer availability, checkRecognitionSupport for ta-IN and en-IN, triggerModelDownload button for ta-IN, default recognizer package, Export to Download/Vaakku/evidence/).
8. Gradle task checkBannedWords (§9 item 4) and scripts/check_manifest.sh (§6.2). Also scripts/push_models.sh (adb push models/<dir> to /sdcard/Android/data/app.vaakku/files/models/<dir>, skipping files that already exist with the same size) and scripts/install.sh (assembleDebug + adb install -r + launch).
9. STATUS.md from the §12.4 template. Fill in what you know. Leave rulings as "unknown".
10. Build, run the guards, install on the phone, launch, take a screenshot to evidence/G0_setup.png.

When you need the phone (USB debugging prompt, install confirmation popup, running the probe), print a HUMAN ACTION block and wait.

Exit criteria (G0), all with evidence recorded in STATUS.md:
- assembleDebug succeeds (paste the last lines of the log into evidence/G0_build.txt)
- check_manifest.sh shows no INTERNET
- app installed and launched; evidence/G0_setup.png exists and shows Tamil text rendering correctly (ask me to confirm the Tamil renders)
- Rung-0 probe export file copied into evidence/ (ask me to run it)
Commit with message "P0 bootstrap". Then tell me "P0 done" and summarise the Rung-0 probe result in 3 lines.
```

---

## RESUME — paste at the start of every new session

```
New session. You have no memory. Read CLAUDE.md, then STATUS.md, then docs/VAAKKU_BUILD_PLAN.md (at least §0–§2, §9, §12 and the section for the phase named in STATUS.md "Handoff notes"). Then tell me in 5 lines or fewer:
1. current phase and hour,
2. which gates are passed (with their evidence paths),
3. the last decision made,
4. what you will do next,
5. anything you need from me.
Do not write code until I reply "go".
```

---

## PROMPT P1 — Domain core [RED-OK] (Terminal A, or Terminal B after P0)

```
Phase P1: build the pure-JVM :domain module exactly as specified in docs/VAAKKU_BUILD_PLAN.md §5 (all subsections) and §11.2 (labels and expected demo results). Read those sections fully before starting.

Rules:
- Only edit domain/**, testdata/testaudio/labels.json, and the P1 part of STATUS.md. Do not touch app/** or root Gradle files (if you must, ask me first).
- No android.* or third-party ML imports in domain.
- Work test-first: for every rule in §5.4–§5.7 write the failing test, then the code.

Deliverables in this order (commit after each):
1. Types (§5.1, §5.2) + Thresholds data class with the §5.7 defaults, loadable from JSON.
2. lexicon_ta_en.json with ALL groups from §5.3 + LexiconLoader + fuzzy matcher (NFC, strip ZWJ/ZWNJ, Levenshtein ≤1 for tokens ≥5 code points). Tests.
3. Normalizer (§5.4) with at least 40 test cases including: எட்டு சதவீதம்→8, "eight percent"→8, "8%"→8, எட்டரை→8.5, ஒரு லட்சம் இருபதாயிரம்→120000, அஞ்சு வருஷம்→60 months, "18 months"→18, the "ஒரு" and "ஆறு" traps (no unit nearby → no number), "policy term பத்து வருஷம்"→no LOCK_IN claim.
4. SpokenExtractor (§5.5) incl. hedge, negation (±3 tokens), conditional, GUARANTEE special rule, "FD மாதிரி" produces nothing by itself.
5. RowAssembler + WrittenExtractor (§5.6).
6. Reconciler state machine (§5.7) with the decision table implemented rule by rule, latest-wins, mentionCount, UserRecheck, card selection, and an append-only event log.
7. Copy keys (§5.8): entry → template key + args, and follow-up question key per type.
8. Schema guard test (§5.9).
9. Fixtures (§5.10): at least 26 JSON fixtures incl. the 10 adversarial rows A1–A10, demo-path cases from §11.2 (R01 vs prop → exact expected states), 6 normalization cases, 4 honest-agent scripts with zero DIFFERS.
10. Gradle tasks: fixtureReport → evidence/fixture_report.md (confusion matrix, precision and recall on DIFFERS); evalTranscripts → reads evidence/asr_prescreen/<engine>/<file>.txt + testdata/testaudio/labels.json and writes evidence/asr_slot_accuracy.md (slot accuracy per engine per file).
11. testdata/testaudio/labels.json generated from the §11.2 table (ask me to confirm the scripts match what I recorded).

Exit criteria (G1): ./gradlew :domain:test all green; fixture_report.md shows DIFFERS precision = 1.00; schema guard test present and passing. Record evidence paths in STATUS.md. Report: number of tests, precision/recall numbers, and any spec ambiguity you resolved (list each decision).
```

---

## PROMPT P2 — ASR [GREEN] (Terminal B)

```
Phase P2: speech recognition. Read docs/VAAKKU_BUILD_PLAN.md §6.3, §11.1–§11.3 and §13 "P2" fully first.

Step 1 — laptop pre-screen (do this first, it needs no phone):
- Write tools/asr_prescreen/prescreen.py using the pip package sherpa-onnx (already installed on this laptop; check with `python -c "import sherpa_onnx; print(sherpa_onnx.__version__)"`).
- For every model directory in models/ (whisper-small-ta, dolphin small, dolphin base, omnilingual 300M) and every WAV in testdata/testaudio/, decode and write the transcript to evidence/asr_prescreen/<engine>/<file>.txt plus a CSV with laptop RTF.
- Read the sherpa-onnx Python examples/API for the exact config of each model family; do not guess parameter names.
- If :domain evalTranscripts exists (P1 may still be running in Terminal A), run it. Otherwise print a table of transcripts for me to eyeball.

Step 2 — the app:
- Add the sherpa-onnx AAR from app/libs/. Use the Kotlin API files that match that exact version (read them; do not guess class names).
- Implement AudioSource (Mic + WavAsset), Silero VAD segmentation, segmentQuality, AsrEngine interface + the five engines from §6.3, engine switching with unload, numThreads setting.
- Wire AsrSegment → domain SpokenExtractor → show the extracted claims on a debug "Live ASR" screen.
- Bake-off screen (§6.3) with CSV export to Download/Vaakku/evidence/.
- Make sure scripts/push_models.sh has pushed the models (ask me to run it if the phone isn't connected).

Human steps you will need (print HUMAN ACTION blocks): pushing models, running the bake-off on the phone, a live-mic test with my teammate speaking T01–T04 lines from 1 metre, and exporting the CSV (I will copy it into evidence/ via Office Kit or adb pull).

Exit criteria (G2): STATUS.md "Decisions log" contains the ASR decision using the §13 P2 decision rule, with evidence: prescreen accuracy table, phone bake-off CSV, my live-mic scorecard (I'll paste it as MEASUREMENT lines). Set the default engine in code accordingly. Commit.
```

---

## PROMPT P3 — OCR [GREEN]

```
Phase P3: document reading. Read docs/VAAKKU_BUILD_PLAN.md §6.4, §5.6 and §11.2 (prop document) first.

Build:
1. Scan sheet with CameraX Preview + ImageCapture (+ ImageAnalysis stub for the NPU masker, feeding nothing yet).
2. ML Kit bundled Latin recognizer (com.google.mlkit:text-recognition:16.0.1 — NOT the play-services variant). Map results to domain OcrLine (use line/element confidence if the API exposes it in this version, else 1.0; tell me which).
3. RowAssembler + WrittenExtractor from :domain → WrittenObserved events → show a clause list right after each scan.
4. "Done scanning" → DocumentScanCompleted.
5. Save evidence images per §6.4 (page image + crops) under files/sessions/<sessionId>/ (masking comes in P4; leave a clear hook).
6. Debug: OCR time in ms.

Human steps: I will scan the printed prop document 5 times under the venue lighting, using a phone stand if available, and tell you what each scan found.

Exit criteria (G3): expected clauses (RATE {4,8} ILLUSTRATIVE, GUARANTEE false, LOCK_IN 60 months, LIQUIDITY nil-before 60, CHARGES 5%) extracted in ≥4 of 5 scans; screenshot evidence/G3_clauses.png; results table in STATUS.md. If a clause keeps failing, add the real OCR lines as a new domain fixture and fix the extractor, not the fixture. Commit.
```

---

## PROMPT P4 — NPU privacy masker [GREEN] (timebox: 5 hours total)

```
Phase P4: the one honest NPU workload. Read docs/VAAKKU_BUILD_PLAN.md §6.5 fully first, then read the Google sample I built last night at <PATH TO litert-samples>/.../image_segmentation/kotlin_npu/android_jit (README, gradle/libs.versions.toml, app/build.gradle.kts, settings.gradle.kts, device_targeting_configuration.xml, and the code that creates the CompiledModel). Use the sample only as a reference for build structure and API usage; write our own code.

N0 first: check STATUS.md for the canary result (Google's unmodified sample on this phone). If it is not recorded, help me run it: bundletool build-apks --local-testing + install-apks with the right device group, then capture logcat to evidence/G4_canary_logcat.txt. Tell me clearly whether logcat shows NPU dispatch.

N1:
1. Bring in the LiteRT dependency at the EXACT version pinned by the sample, and the litert_npu_runtime_libraries modules (qualcomm_runtime_v81 is for this phone's SM8850) from <PATH TO the runtime libraries folder I downloaded>, plus device targeting config and useLegacyPackaging.
2. Copy the selfie_multiclass_256x256.tflite model from the sample into app/src/main/assets/npu/.
3. NpuPrivacyMasker: CompiledModel with Options(Accelerator.NPU, Accelerator.GPU); run on ImageAnalysis frames (downscaled to the model input), produce a person mask (all non-background classes), draw a subtle hatched overlay on the preview, and blank person pixels in every saved page image and crop (hook from P3).
4. Show a small monospace label on the scan sheet: "mask <ms> ms · <ACCELERATOR>". Only print NPU if verified via logcat.
5. NPU benchmark screen (debug): 50 runs each with NPU, GPU, CPU options; median and p90; CSV export.
6. Update scripts: scripts/install_bundle.sh (bundleDebug or bundleRelease → bundletool build-apks --local-testing → install-apks). Tell me whether plain installDebug still loads NPU; if not, the bundle script becomes the default install path, and update CLAUDE.md "Always do".

Stop and switch to fallback if, by 4 hours in, NPU dispatch is not proven: ship the masker on GPU with an honest label, record the exact failure in STATUS.md, and move on. Stretch N2 (EAST text detector) only if P5 is already green.

Exit criteria (G4): benchmark CSV in evidence/; logcat excerpt proving which accelerator ran; evidence/G4_mask_label.png. Commit.
```

---

## PROMPT P5 — Integration [GREEN]

```
Phase P5: make it a product. Read docs/VAAKKU_BUILD_PLAN.md §5.7 (card selection), §6.6, §6.7, §8 (all copy) and §11.2 (expected demo results) first. Also read docs/KAAVAL_UI_DESIGN_SPEC_v2.md and docs/kaaval-tokens.css for visuals, applying the state-name mapping in §6.6.

Build:
1. SessionService (foreground, type microphone) owning ASR + reconciler; StateFlow<SessionState> to the UI.
2. Setup screen complete: live checklist; Start disabled until airplane mode is ON (debug long-press override).
3. Session screen: Delta Card (DIFFERS = two stacked statements + violet perforation; NOT_IN_DOCUMENT = open dotted field), empty state with counters, card queue "1 / N" + next, bottom-third controls only, one short haptic pulse per new card, NO sound.
4. Details (ledger of six types) and Provenance sheet (spoken span with mm:ss timestamp beside the document crop image; follow-up question; re-check).
5. All strings from §8 into strings.xml with <!-- TAMIL-REVIEW --> comments. Export a file evidence/tamil_strings_for_review.md listing key, Tamil, English so I can review them.
6. Debug overlay (§6.6 item 6) including thermal status and headroom.
7. A "Rehearsal" toggle in the dev menu that feeds R01_demo_pitch.wav (create it by concatenating T01+T02+T03+T04 with a small script in tools/) through the exact same pipeline as the mic.

Test protocol (print HUMAN ACTION blocks for me):
(a) Rehearsal R01 + prop document, 5 runs → must match §11.2 expected demo result every time.
(b) Honest T05+T06 rehearsal, 3 runs → zero DIFFERS.
(c) Live: my teammate reads the demo lines from 1 metre, 3 runs; and the honest lines, 2 runs. I report results.
If any honest run shows a DIFFERS, that is the top-priority bug: capture the transcript (text export), add it as a domain fixture, fix, re-run.

Exit criteria (G5): (a) 5/5, (b) 3/3 zero DIFFERS, (c) results recorded; screenshots evidence/G5_differs_card.png, evidence/G5_not_in_doc_card.png, evidence/G5_provenance.png. Commit. Then write a "Next Red Light test list" in STATUS.md.
```

---

## PROMPT G6 — Go / No-Go report (paste around H+17)

```
Produce a Go/No-Go report for Claude chat. Do not change code. Read STATUS.md and evidence/. Output exactly:
1. Gate table G0–G5 with PASSED/FAILED and the evidence path for each.
2. Any false DIFFERS ever observed (fixture, rehearsal or live) and whether it is fixed.
3. ASR: chosen engine, slot accuracy, RTF, live scorecard summary.
4. NPU: which accelerator actually runs the masker, median ms, proof file.
5. Known bugs, ranked by demo impact.
6. Your recommendation: what to cut, what to keep, and the P6/P7 plan for the remaining hours.
Keep it under 40 lines. Save it as evidence/G6_report.md too.
```

---

## PROMPT P6-A — Hash chain + packet CLI [RED-OK] (Terminal A; can start once G1 passes)

```
Phase P6-A. Read docs/VAAKKU_BUILD_PLAN.md §7.1 and §7.4 first. Only edit domain/** and tools/packet-cli/**.
1. domain: canonical JSON for reconciler events, hash chain (h0, h_i), ReceiptBuilder, verifier; tests incl. tamper-one-byte and reorder → fail.
2. tools/packet-cli (Node.js, minimal deps): --in <folder|zip> --out <pdf>; verify chain and ECDSA signature (public key from certChain[0]); print INTEGRITY: PASSED/FAILED; render HTML with Tamil fonts via @font-face from app/src/main/res/font/; print to PDF with headless Edge or Chrome (auto-detect paths on Windows; clear error if neither is found).
3. Packet content per §7.4. Run checkBannedWords over tools/packet-cli too.
4. A sample receipt generated from a domain fixture (unsigned, marked SAMPLE) so the CLI can be demoed before the app exports real receipts.
Exit: tests green; evidence/sample_packet.pdf; README in tools/packet-cli explaining the one command. Commit.
```

---

## PROMPT P6-B — Signing + export + Office Kit beat [GREEN]

```
Phase P6-B. Read docs/VAAKKU_BUILD_PLAN.md §7.2, §7.3 first.
1. Keystore EC P-256 signing with StrongBox attempt → TEE fallback, attestation challenge = h0.
2. End-session → Receipt screen → Export to Download/Vaakku/<sessionId>/ (receipt.json, crops, masked page images) + <sessionId>.zip via MediaStore. No audio.
3. Static free-look note (§8.4) on the Receipt screen.
Human steps: I run a real session, export, move the folder to the laptop with Office Kit into <C:/Users/clash/Documents/VAAKKU/incoming>, you run the CLI into <.../outgoing>, I move the PDF back with Office Kit and open it on the phone. I will time it.
Tamper test: change one character in receipt.json → CLI must print FAILED.
Exit criteria (G7): evidence/G7_packet.pdf from a real session; CLI output PASSED; tamper output FAILED; my timing as a MEASUREMENT line. Commit.
```

---

## PROMPT P7 — Hardening + freeze [GREEN]

```
Phase P7. Read docs/VAAKKU_BUILD_PLAN.md §6.7, §11.5, §13 "P7" first.
1. Review logcat from the last test sessions for crashes/ANRs; fix them.
2. Help me run a 15-minute soak test (phone on the table, session running, teammate talking at intervals). I'll report: thermal status at start/5/10/15 min, battery % start/end, whether the session survived, any lag. Fix what breaks.
3. Release build (minify off, debug signing) installed through the final install path; verify NPU label and models still work.
4. Three full airplane-mode demo runs with the final build; I report results.
5. Final guard run (§9 all six). README.md at the repo root: what the app does, what runs where (ASR engine, OCR, NPU masker, receipt), how to build and install, and the honest limits.
Exit (G8 FREEZE): all guards green; 3/3 demo runs OK; STATUS.md says "FROZEN at <time>". From now on, only fix bugs I explicitly approve.
```

---

## PROMPT P8 — Freeze mode (demo + submission)

```
We are FROZEN. Do not add features or refactor. For any change, first tell me: the bug, the smallest fix, the files touched, and the risk. Wait for my "approve". After an approved fix: run all guards, install through the final path, and ask me to run one full demo.
Also available on request: pull real numbers from evidence/ for the pitch deck (ASR RTF, slot accuracy, NPU vs GPU vs CPU ms, DIFFERS precision on fixtures, soak-test thermals), and `adb shell screenrecord` help for demo footage.
```

---

## PROMPT RED — Red Light mode

```
RED LIGHT started at <time>. Read the "Red Light ruling" in STATUS.md. Under that ruling:
- Do NOT run Android Gradle builds or adb unless the ruling explicitly allows it.
- If the ruling allows laptop use from the phone: continue only [R-OK] tasks (domain/**, tools/**, fixtures, docs). Suggested queue: <what's next from STATUS.md>.
- If the ruling forbids laptop use: reply "Pausing for Red Light" and stop.
When I say GREEN LIGHT, first ask me for the MEASUREMENT lines I collected during Red Light and record them in STATUS.md.
```

---

## PROMPT BUG — report a bug (fill in)

```
BUG REPORT
What I did: <steps on the phone>
What I expected: <e.g. LOCK_IN DIFFERS card>
What happened: <e.g. no card; or wrong value "15 years">
Screenshot / file: <path in evidence/>
Transcript export (text) if speech-related: <path>
Please: 1) reproduce with a domain fixture if it's logic, 2) fix, 3) run all guards, 4) tell me what to re-test on the phone.
```

---

## PROMPT MEASURE — hand over measurements (fill in)

```
MEASUREMENTS for STATUS.md (record them verbatim, then tell me if any number breaks a budget in §11.5 or a gate criterion):
MEASUREMENT <gate>: <engine / test> — <values>
MEASUREMENT <gate>: ...
```
