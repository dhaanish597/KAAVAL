# VAAKKU (formerly KAAVAL) — FINAL LOCKED BUILD SPECIFICATION
## iQOO Hackathon 2026 · Chennai City Battle (Sept 12–13, 2026) · Reconciliation of Claude vs Fable 5.1

## TL;DR
- **BUILD IT — as VAAKKU, with three non-negotiable fixes.** The buyer-side, offline, spoken-vs-written "quote-don't-accuse" delta engine is genuinely novel and rubric-aligned, but it is rejectable unless you (1) put one real model on the Hexagon NPU with latency on screen, (2) kill "the delta cannot be wrong" and make silence (NOT IN DOCUMENT) the default failure, and (3) resolve Tamil ASR in the first 6 hours with a hard go/no-go.
- **Fable was right on the two plan-breaking mechanics; the team's belief was wrong.** Red Light IS a dev-workflow restriction (laptop closed as a build machine for 55% of the event; Office Kit is the only bridge), NOT a runtime acceptance test. But Fable's RUBRIC was wrong — the official Reskilll rubric is the team-brief one (End product 30 / Novelty 20 / Creative phone use 15 / Tech depth 15 / Office Kit 10 / Demo 10), with 25% from HackTracker device telemetry.
- **The idea's only fatal risk is offline code-switched Tamil ASR.** Everything else (OCR, NPU, ledger, receipt) is de-risked. Ship the number-spotting + English-carrying ASR ladder, treat full Tamil transcription as a gated stretch, and the concept wins on novelty + phone-first creative use + a working NPU demo.

## Key Findings

1. **Official rubric (V1) — team brief is correct, Fable is wrong.** Reskilll's own "How to Win iQOO City Battles" page (17 Aug 2026) lists: End product quality 30% (jury), Novelty & impact 20% (jury), Creative phone use / camera-voice-on-device-AI 15% (HackTracker device data), Technical depth 15% (jury), Office Kit usage 10% (HackTracker device data), Demo & presentation 10% (jury). Fable's "Phone-First 25 / AI-Native 20 / Problem Fit 20 / Craft 10 / HackTracker 25" is the RETIRED Delhi-NCR 8-hour edition rubric, still visible on the older Reskilll prep page. Use the team-brief rubric. 25% is machine-measured and unfakeable ("25% is measured from DEVICE DATA, not your pitch… You can't fake it").

2. **Red/Green (V2) — Fable is correct; this breaks the old plan.** Multiple Reskilll pages state "55% of build time is Red Light – phone only, no laptop… During Red Light, your laptop is CLOSED as a build machine – Office Kit is the only bridge." This is a development-workflow restriction, not a runtime test. The team's prior belief ("laptop stays open and tethered for all 30 hours") is FALSE and must be discarded. Total event is 30 hours with ~25h hacking (Reskilll comparison table).

3. **NPU path (V5) — do it, via the vision/OCR leg with LiteRT QNN.** Google's LiteRT Qualcomm AI Engine Direct (QNN) accelerator (shipped 24 Nov 2025) delegates to the Hexagon NPU on the Snapdragon 8 Elite Gen 5 (SoC SM8850). Per the Google Developers Blog: the accelerator delivers "up to a 100x speedup over CPU and a 10x speedup over GPU," and on the 8 Elite Gen 5 "over 56 models run in under 5ms with the NPU, while only 13 models achieve that on the CPU" (FastVLM TTFT 0.12s on 1024×1024 images). Integration = `CompiledModel.create(..., Options(Accelerator.NPU, Accelerator.GPU))` with automatic CPU/GPU fallback. Subagent verification: NPU access on OriginOS is **mechanism-level LOW risk** (it depends on Qualcomm's unsigned-PD fastrpc path, not an OEM whitelist) but UNVERIFIED on vivo specifically — must smoke-test on the loaner iQOO 15 in hour 1 with the `libQnnHtpV81Skel.so` library and the latest QAIRT SDK.

4. **ASR (V6) — the one real risk.** Offline ta-IN on the iQOO 15 via Android SpeechRecognizer is unverified (default recognizer may be vivo Jovi, not Google; offline packs are unreliable and app-dependent). sherpa-onnx offers real offline Tamil options: **Dolphin small CTC multilingual** (DataoceanAI/Tsinghua, trained on "over 200,000 hours," WER ~23–24 on FLEURS/CommonVoice, beating Whisper Large-v3, with 40–70% relative improvement on languages including Tamil) and the new **Meta Omnilingual ASR** (released 10 Nov 2025, "more than 1,600 languages," 300M variant for low-power devices, "78% have a character error rate below 10%," Apache 2.0). Raw Whisper-small is unusable for verbatim Tamil — 93.3% WER on FLEURS Tamil (arXiv 2409.02449) — confirming full transcription must be a gated stretch, not the core path.

5. **Prior art (V9) — concept remains defensible.** VoiceClaim Auditor (lablab.ai) is confirmed as the closest, but it "verifies them in real time using Speechmatics, Bright Data, and AI/ML API with evidence-backed verdicts and confidence scores" — cloud-dependent, open-web, verdict-and-score oriented — the opposite of your buyer-side, document-anchored, no-verdict, offline design. No 2025–2026 product does offline spoken-vs-written comparison for the buyer.

6. **Legal (V8) — posture is sound.** DPDP Act 2023 §3(c)(i) exempts data "processed by an individual for any personal or domestic purpose" (narrow: individuals only, genuinely personal use). Vibhor Garg v. Neha (SC, 14 Jul 2025) admits covertly recorded conversations subject to a three-fold test (relevance, voice identification, proof of accuracy/no tampering). BSA 2023 §63 governs electronic-record certificates (replaced Evidence Act §65B from 1 Jul 2024; dual certificate). Final disclosure posture = Evidence Mode (phone face-up, disclosed).

## Details

### 1. FINAL LOCKED CONCEPT

- **Name: VAAKKU (வாக்கு, "a word given / a promise") — switch only if organizers permit a rename at check-in; otherwise keep whatever is registered.** Rationale: "Kaaval" (guard/police) connotes accusation and surveillance — the exact framing your load-bearing constraint forbids — and collides with team SENTINEL7's prior DSU DevHack 3.0 "KAAVAL." "Vaakku" means the promise the agent gave verbally, which is precisely what the product checks against the document; it reinforces "It quotes, it never accuses." If a registration lock prevents a rename, keep KAAVAL and strip all guard/police imagery from UI and pitch.
- **One-line description:** A fully offline Android app that listens to an insurance/loan sales pitch (code-switched Tamil–English) and reads the benefit illustration, then shows — in one large Tamil line, before you sign — exactly where what was *said* differs from what is *written*.
- **Positioning sentence:** "There are a dozen well-funded companies recording sales conversations. Every one of them works for the person doing the selling. This one works for the buyer."
- **No-verdict rule (unchanged, load-bearing):** Never a verdict, fraud score, risk rating, severity, or "this looks like mis-selling." Only a typed delta between two values. Enforced at schema level — no `riskScore` / `severity` / `fraudLikelihood` fields exist. "It never accuses. It quotes." Value test passes: the product is still useful if everyone in the room is honest, because it protects the buyer from *ambiguity* and *forgetting*, not just from fraud.
- **Final state labels: ADOPT LESS-ACCUSATORY LABELS.** Replace CONSISTENT / UNSUPPORTED / CONTRADICTED with **MATCHES / NOT IN DOCUMENT / DIFFERS**. The internal state machine keeps the precise enum, but every user-facing string uses the softer labels. "DIFFERS" states a fact about two numbers; "CONTRADICTED" implies someone lied. This is the cheapest possible reinforcement of the no-verdict thesis and pre-empts the sharpest judge question.
- **Confirmation this is the best idea to build:** YES. The verified rubric rewards exactly what this project is strong at — creative camera+mic+on-device-AI use (15%), novelty (20%), technical depth (15%), and a phone-native demo (part of end-product 30%). The verified rubric exposes NO fatal weakness in the *concept*; the only fatal risks are execution risks (NPU must be real, delta must not false-accuse, Tamil ASR must degrade gracefully), all addressed below. Switching ideas <24h out is neither realistic nor warranted.

### 2. RECONCILIATION TABLE (Claude vs Fable 5.1)

| # | Disagreement | Who was right | Evidence | Final decision |
|---|---|---|---|---|
| V1 | Scoring rubric | **Claude / team brief** | Reskilll "How to Win" page: 30/20/15/15/10/10. Fable used the retired Delhi-NCR rubric. | Optimize to 30/20/15/15/10/10. |
| V2 | Red/Green meaning | **Fable** | Reskilll: "During Red Light, your laptop is CLOSED as a build machine – Office Kit is the only bridge." | Plan a phone-primary workflow; laptop only in Green Light. |
| V3 | Originality / pre-staging | Both partial; Fable self-contradicted | Official terms page JS-rendered/unreadable; Reskilll guidance + norms apply | Pre-stage only assets (models, SDKs, fonts, docs), NOT app logic. |
| NPU | Is anything on the NPU? | **Fable** (fatal gap flagged) | Deck claimed "NPU saturated" with nothing on NPU | Put OCR/vision leg on NPU via LiteRT QNN; show latency on screen. |
| Delta | "Delta cannot be wrong" | **Fable** | ASR/OCR/normalization errors create false DIFFERS against honest agents | Precision-first; default failure = NOT IN DOCUMENT (silence). |
| ASR | Feasibility of Tamil | **Fable** (more cautious) | ta-IN offline unverified on OriginOS; raw Whisper-small 93.3% WER on FLEURS Tamil | Number/keyword spotting + English-carrying; full Tamil = gated stretch. |
| Prior art | Closeness of VoiceClaim Auditor | Both cited; **Claude** more precise | Cloud, open-web, verdicts+scores | Differentiator holds: offline, doc-anchored, no verdict. |
| MediaPipe | Deprecated? | **Both correct** | Google: MediaPipe LLM Inference in maintenance-only; migrate to LiteRT-LM | Cut Gemma tier for the demo; pitch-only if kept. |
| SecondSense | Real prior winner? | **Fable flagged as unverified** | Could not verify | Do not cite on stage. |

### 3. VERIFIED EVENT MECHANICS

- **Rubric (Reskilll "How to Win iQOO City Battles", 17 Aug 2026):** End product 30 / Novelty & impact 20 / Creative phone use 15 / Technical depth 15 / Office Kit 10 / Demo 10. Creative phone use (15%) and Office Kit (10%) are measured by HackTracker device telemetry. **Build implication:** the app must genuinely exercise camera + microphone + on-device NPU during the build and demo, and you must actively use Office Kit (screen mirror, file transfer, remote control) throughout — logged and scored.
- **Red/Green (Reskilll "vs Regular Hackathons" + "How to Win"):** 55% Red Light (phone only, laptop closed as build machine, Office Kit the only bridge), 45% Green Light (both devices; do heavy compute, large builds, dependency installs here). **Build implication:** all Gradle builds, model downloads, and NDK-heavy work happen in Green Light; during Red Light make progress via Office Kit Remote PC driving the laptop IDE, or phone-side work (on-device testing, UI tuning, fixture/prompt editing, demo rehearsal).
- **Originality rule (V3) — official terms page could NOT be read (JavaScript-rendered; flagged UNVERIFIED).** Apply the safe, standard rule Reskilll's guidance implies ("Pre-plan your Red Light work"; build "during phone-only time"): **app logic must be written inside the event window.** MAY prepare tonight: downloaded models/SDKs/dependencies, Office Kit install + practice, the Tamil font bundle, source-of-truth doc, UI spec/CSS tokens, Claude Code prompt queues, PPTX, architecture/plan, the adversarial fixture *as a spec*. May NOT pre-write: the `:domain` state machine, extraction code, or app modules. **This overrides Fable's internally contradictory checklist**, which correctly said "pre-staging is limited to downloading models/SDKs… not writing app logic" but then wrongly scheduled "H6–8: Write the :domain claim-ledger state machine" *before* check-in. Do NOT pre-write it. Verify the exact rule with organizers at check-in.
- **HackTracker (V4):** measures actual phone usage (camera/mic/on-device AI) and Office Kit usage from device telemetry. Legitimate ways to score: run real camera OCR and real mic ASR on the phone during the build; keep the NPU busy with the vision model; use Office Kit continuously; demo entirely on the phone.
- **Office Kit (V10):** vivo/iQOO Office Kit on OriginOS 6 provides Free Transfer (phone files on PC), Remote PC (control Windows/Mac from the phone — virtual mouse/touchpad, Privacy Mode with the PC screen off), screen mirroring, Super Clipboard, Task Handoff, and DocMaster (PDF convert/annotate/sign). PC app from pc.vivoglobal.com; needs Windows 10+ (x86) or macOS 10.14.6+. **Best 10% demo beat:** during a Green-Light segment, generate the grievance-packet PDF on the laptop via the Node CLI, then use Office Kit to pull it to the phone (or Remote PC to trigger it from the phone) and open it in DocMaster — a visible, timed ~45-second Office Kit beat.

### 4. FINAL FEATURE SET

**MUST BUILD (core, demo-critical):**
- Mic → streaming capture → number/keyword-spotting extraction (Tier-1 deterministic regex/lexicon) into the six claim types (rate, guarantee-vs-illustrative, lock-in, liquidity/surrender, mandatory bundling, charges/fees).
- Camera → CameraX → ML Kit Text Recognition v2 (bundled Latin model) OCR of an English benefit illustration → clause extraction into the same schema. (ML Kit v2 supports Latin/Chinese/Devanagari/Japanese/Korean only — **no Tamil script**, confirmed — so documents must be English; a stated scope boundary, not a bug.)
- Shared claim-ledger reconciler (pure-JVM `:domain`, unit-testable <2s) comparing spoken vs written values → MATCHES / NOT IN DOCUMENT / DIFFERS.
- One large Tamil delta line before signing (e.g., "பேச்சில் 8%, ஆவணத்தில் 4% — சரிபார்க்கவும்").
- **One real NPU workload with on-screen latency** (the vision leg — see architecture).
- Provenance display: transcript span + timestamp beside the ML Kit bounding-box crop of the document line.
- Visible privacy: airplane mode on; no INTERNET permission in the manifest.
- Foreground service with `FOREGROUND_SERVICE_TYPE_MICROPHONE` (required Android 14+).

**SHOULD BUILD (if gates pass):**
- Full offline Tamil transcription via sherpa-onnx (Dolphin CTC or Omnilingual 300M) — only if the Rung-1 gate passes.
- Hash-chained, signed session receipt (Android Keystore/StrongBox) tied to BSA 2023 §63 framing.
- Thermal/latency overlay (`PowerManager.getCurrentThermalStatus()`, ADPF hints, per-stage latency).
- Neutral follow-up question generator ("Ask him: where in the document does it say guaranteed?").
- 30-day free-look countdown.

**PITCH-ONLY (slides, not built in 30h):**
- Laptop "advocate tier" grievance-packet PDF via Node CLI + Office Kit (build a thin version if time permits; otherwise show the flow once for the Office Kit beat).
- Gemma 3n E2B Tier-2 semantic extraction (MediaPipe deprecated; would use LiteRT-LM).
- Family handoff of claims; generalization to rentals/job offers/loans/medical consent/used vehicles.
- Tamper-evident routing to Bima Bharosa / Ombudsman.

**CUT (do not attempt):**
- Any custom NPU model export/compilation during the event.
- Full NLI (DeBERTa/MNLI) semantic entailment.
- Dual-pass live Tamil+English ASR.
- Gemma on the NPU (no supported selection path).
- Any verdict/score/risk/severity feature.

Every feature above respects the no-verdict constraint.

### 5. FINAL TECHNICAL ARCHITECTURE

- **Language/structure:** Kotlin; pure-JVM `:domain` module (all product logic, unit-testable <2s); Jetpack Compose UI; CameraX.
- **ASR primary path:** Number/keyword-spotting on a **single multilingual offline model via sherpa-onnx** — first choice **Dolphin small CTC multilingual (int8 ~239 MB, Tamil included; ~23–24 WER)**; if it underperforms in the hall, fall back to **Meta Omnilingual ASR 300M CTC (int8 ~348 MB, 1,600+ languages)**. Post-process with an English financial-number lexicon. **ASR fallback:** Android on-device SpeechRecognizer English pass (`createOnDeviceSpeechRecognizer`, `EXTRA_PREFER_OFFLINE`) carrying English numerals + terms, with Tamil numerals normalized deterministically in `:domain`. **Rehearsal-audio fallback** for the demo if the live hall is too noisy.
- **ASR go/no-go tree (hours 0–6):** Rung 0 (0–1h) probe `SpeechRecognizer.isOnDeviceRecognitionAvailable()`, `checkRecognitionSupport()` for ta-IN, `triggerModelDownload()`, and identify whether the default recognizer is Google or vivo Jovi. Rung 1 (1–3h) test Dolphin/Omnilingual in sherpa-onnx on the phone. Rung 2 prefer one multilingual model over dual-pass. Rung 3 Silero VAD, phone face-up. **Hard gate at H+6:** if verbatim Tamil is not reliably better than number-spotting, freeze on number-spotting + English-carrying and make full transcription pitch-only.
- **OCR path:** ML Kit Text Recognition v2 bundled Latin model (English documents only). Per-element confidence used for gating.
- **THE ONE HONEST NPU WORKLOAD:** a pre-exported **vision/text-region detection model from Qualcomm AI Hub**, deployed via **LiteRT `CompiledModel` with `Accelerator.NPU` (QNN) and `Accelerator.GPU` fallback**, targeting **SoC SM8850 (Snapdragon 8 Elite Gen 5, Hexagon v81)**. It does real work: it locates the document text region / line crops fed to ML Kit, and its per-frame latency is shown on screen ("Region detect: NPU 8 ms / CPU 240 ms"). **Integration:** (1) in Green Light, download the AI-Hub `.tflite`/precompiled asset and the LiteRT NPU runtime libs (`fetch_qualcomm_library.sh`), bundle `libQnnHtp.so`, `libQnnHtpPrepare.so`, and the arch-matched `libQnnHtpV81Skel.so` in the APK's native lib dir; (2) add the AI Pack / dynamic feature to Gradle per the LiteRT tutorial; (3) load via `CompiledModel.create(assets, "model.tflite", Options(Accelerator.NPU, Accelerator.GPU))`; (4) **hour-1 smoke test on the loaner iQOO 15** — check logcat for a successful user-PD on domain 3 and `libQnnHtpV81Skel.so` load (success) vs "untrusted app trying to offload to signed remote process" / "Permission denied" on `/dev/fastrpc-cdsp` (block). If blocked, fall back to keeping OCR on ML Kit and running a small model on the NPU purely for the on-screen debug overlay — still a genuine NPU workload. Use the latest QAIRT/QNN SDK (must recognize SM8850; older SDKs fail with "No Snapdragon SOC detected"). **Do NOT attempt custom export.** Lower-tier honest alternative: Qualcomm AI Hub ships a **precompiled QNN-ONNX Whisper-Small for "Snapdragon 8 Elite Gen 5 Mobile" (QAIRT 2.42, ONNX Runtime 1.24.1)** — usable to put ASR on the NPU, but its Tamil is only multilingual-by-architecture and not certified, so prefer the vision leg for the NPU demo and keep ASR on sherpa-onnx.
- **Claim-ledger reconciler state machine (`:domain`):** two producers (OBSERVED_SPOKEN, OBSERVED_WRITTEN) feed one deterministic reconciler; lifecycle → MATCHES / NOT IN DOCUMENT / DIFFERS. Latest-wins self-correction. Only frame as CRDT if actually implemented.
- **Precision-first gating (default failure = NOT IN DOCUMENT / silence):** a DIFFERS state is emitted ONLY if ALL hold: (a) both sources pass confidence gates (SpeechRecognizer `CONFIDENCE_SCORES`; sherpa/Whisper `avg_logprob`, `no_speech_prob`; ML Kit per-element confidence), (b) same claim type, (c) values differ beyond a per-type tolerance, (d) no hedge/negation token present, (e) two-source corroboration. Otherwise → NOT IN DOCUMENT. Low-confidence numerics are quarantined, never compared.
- **Normalization:** எட்டு / "eight" / 8 → 8; சதவீதம் / percent / % unified; lakh/crore scaling; ranges preserved; "one year" (duration) vs "first year" (period) disambiguated; any ambiguity → NOT IN DOCUMENT.
- **Negation/hedge lexicon:** "not guaranteed", "illustrative", "up to", "expected", "projected", "assumed" suppress DIFFERS.
- **Hash-chained receipt:** claims-only (audio discarded by default), hash-chained, signed with an Android Keystore/StrongBox key; framed against BSA 2023 §63.
- **LLM tier decision: CUT for the demo.** MediaPipe LLM Inference is in maintenance-only mode (Google recommends LiteRT-LM); Gemma has no clean NPU selection path in-window. Keep deterministic Tier-1 only; mention LiteRT-LM/Gemma 3n as a roadmap slide.

**Acceptance test (adversarial fixture — headline metric = precision on DIFFERS):**

| # | Scenario | Correct output |
|---|---|---|
| 1 | Agent says "guaranteed", ASR mishears negation | NOT IN DOCUMENT (hedge/low-conf) |
| 2 | Agent self-corrects 8%→6% | DIFFERS only vs doc, latest-wins |
| 3 | Hypothetical "if it were 8%" | NOT IN DOCUMENT |
| 4 | Document contains BOTH 4% and 8% | NOT IN DOCUMENT (ambiguous) |
| 5 | Tamil numeral "எட்டு சதவீதம்" | normalize → 8% |
| 6 | "one year lock-in" vs "first year charge" | no false DIFFERS |
| 7 | OCR reads 4%→40% at low confidence | quarantine → NOT IN DOCUMENT |
| 8 | "up to 8%" (hedge) vs doc 4% | NOT IN DOCUMENT |
| 9 | Noisy hall, low ASR confidence | NOT IN DOCUMENT |
| 10 | Bundling claim spoken, silent doc | NOT IN DOCUMENT |

### 6. FINAL WORDING

- **Banned words (schema + UI + pitch):** verdict, judge, score, risk, risk rating, severity, fraud, fraud score, mis-selling, mis-selling detector, scam, guilty, accuse, flag-as-accusation, confidence % shown to user.
- **Alert copy templates (a native Tamil speaker MUST review before demo):**
  - DIFFERS: "பேச்சில் 8%, ஆவணத்தில் 4% — சரிபார்க்கவும்" ("In speech 8%, in document 4% — please verify") + "மறுபரிசீலனை" (re-check) button.
  - NOT IN DOCUMENT: "அவர் சொன்னது ஆவணத்தில் இல்லை — கேளுங்கள்" ("What he said is not in the document — ask").
  - MATCHES: "பேச்சும் ஆவணமும் ஒன்றே" ("Speech and document agree").
- **Pitch lines:** "It never accuses. It quotes." · "Every other tool in this space works for the seller. This one works for the buyer." · "Does it still have value if everyone in the room is honest? Yes — because ambiguity isn't the same as honesty."

### 7. FINAL DEMO SCRIPT (3–5 min)

1. **(0:00–0:30) Airplane-mode proof.** Hold up the phone: airplane mode on, then show the manifest has no INTERNET permission. "Everything you're about to see happens on this device. Nothing leaves it." → Novelty (20%) + End product (30%).
2. **(0:30–1:30) Live capture.** Play a scripted Tamil–English pitch ("guaranteed 8% returns, required for the loan"). Camera scans the English benefit illustration. On-screen: NPU region-detect latency ("NPU 8 ms / CPU 240 ms"). → Creative phone use (15%) + Technical depth (15%).
3. **(1:30–2:30) The delta.** The one big Tamil line appears — "பேச்சில் 8%, ஆவணத்தில் 4% — சரிபார்க்கவும்" — with the transcript span beside the bounding-box crop of the document line. Show a NOT-IN-DOCUMENT case too (bundling claim absent from the doc). "It never says he lied. It shows you the two numbers." → End product (30%) + Novelty (20%).
4. **(2:30–3:15) Office Kit beat.** Green-Light segment: generate the grievance-packet PDF via the Node CLI, pull it to the phone with Office Kit / open in DocMaster. → Office Kit (10%).
5. **(3:15–4:00) Close.** Positioning line + the honesty test. → Demo (10%).

### 8. TOP 10 JUDGE QUESTIONS

1. *"Is this a fraud detector?"* — No. It never renders a verdict or score. It quotes two values and shows the difference. No risk/severity/fraud fields exist — enforced at schema level.
2. *"What if the agent is honest?"* — Still valuable: it protects against ambiguity, mishearing, and forgetting, and creates a shared record before signing.
3. *"What actually runs on the NPU?"* — A vision/text-region model via LiteRT QNN on the Hexagon NPU (SM8850), with latency shown on screen; it feeds line crops to OCR.
4. *"Can the delta be wrong?"* — It can be silent. Our default failure is NOT IN DOCUMENT. We show DIFFERS only when both sources pass confidence gates, types match, values differ beyond tolerance, and there's no hedge — precision-first, so we never falsely accuse an honest agent.
5. *"Why offline?"* — Buyer trust and privacy: airplane mode, no INTERNET permission, audio discarded by default. DPDP §3(c)(i) personal-purpose exemption applies.
6. *"Is recording legal?"* — Evidence Mode (disclosed, phone face-up). Vibhor Garg v. Neha (SC 2025) admits such recordings under a three-fold test; we store only claims, not audio, by default.
7. *"How is this different from VoiceClaim Auditor / Gong?"* — Those are seller-side or open-web, cloud, verdict-and-score tools. Ours is buyer-side, document-anchored, offline, no-verdict.
8. *"Tamil ASR — does it really work offline?"* — For the six financial claim types we do number/keyword spotting via sherpa-onnx (Dolphin/Omnilingual) with English-carrying fallback; full verbatim Tamil is a roadmap item, gated in the first 6 hours.
9. *"What's the business model?"* — Consumer-free; distribution via consumer groups, unions, and regulator literacy programs; possible white-label to the Ombudsman.
10. *"Why should we trust the numbers on screen?"* — Provenance: every claim shows its transcript span/timestamp and the document bounding-box crop, and the session yields a hash-chained, Keystore-signed receipt (BSA §63 framing).

### 9. TONIGHT'S CHECKLIST (~12 h, originality-compliant) + 30-HOUR PLAN

**Tonight (assets only — NO app logic):**
- Download & stage: sherpa-onnx AAR + Dolphin CTC and Omnilingual 300M models; ML Kit Latin model; the Qualcomm AI Hub vision model + LiteRT NPU runtime libs (`libQnnHtpV81Skel.so` etc.); latest QAIRT SDK.
- Install Office Kit on the laptop (pc.vivoglobal.com); practice screen mirror, file transfer, Remote PC (Privacy Mode).
- Finalize: Tamil font bundle, UI spec + CSS tokens, source-of-truth doc, Claude Code prompt queues, PPTX, adversarial fixture table (as a spec, not code), architecture diagram.
- Write nothing that is app logic (`:domain`, extraction, modules). Confirm the exact originality rule with organizers at check-in.
- Sleep before a 30-hour build.

**30-hour plan (aligned to Red/Green):**
- **H0–1 (Green):** Gradle skeleton, NPU smoke test on the iQOO 15 (logcat), ASR Rung-0 probe.
- **H1–6 (mixed):** ASR go/no-go ladder; `:domain` reconciler + JUnit fixtures (written now, in-window); CameraX + ML Kit OCR wired.
- **H+6 GATE:** Tamil verbatim vs number-spotting decision; freeze ASR scope.
- **H6–14 (mixed):** NPU vision leg + on-screen latency; precision-first gating; normalization/hedge lexicon; the one big Tamil delta line; provenance display.
- **H14–17 (Green):** Integration, adversarial fixture green, thermal/latency overlay.
- **H+17 GO/NO-GO:** lock scope; anything not green is cut or pitch-only.
- **H17–24 (mixed):** Hash-chained receipt; Office Kit grievance-PDF beat; airplane-mode hardening.
- **H+24 HARD FEATURE FREEZE.** Demo build; rehearse the 3–5 min script 3×; native-Tamil review of all copy.
- **H24–28:** Polish and rehearsal.
- **H28–30:** Buffer; backup rehearsal-audio demo path; final device check.

### 10. CORRECTED FACT SHEET

**Safe to say on stage (with source):**
- IRDAI Annual Report 2024-25: "Mis-selling in the Indian insurance sector is a significant concern that involves the sale of insurance products to consumers without proper disclosure of terms, conditions, or suitability." Unfair Business Practices grievances against life insurers rose from 23,335 (FY24) to 26,667 (FY25), a 14.3% YoY rise; share of complaints rose 19.33% → 22.14%.
- Total life-insurer grievances ~1,20,429 in FY25; the Bima Bharosa portal logged 2,57,790 grievances (IRDAI FY25).
- 30-day free-look period and mandatory Customer Information Sheet (IRDAI Master Circulars, 29 May & 5 Sept 2024); free-look extended from 15 to 30 days.
- Covert/one-party recording admissible under a three-fold test — relevance, voice identification, proof of accuracy/no tampering (Vibhor Garg v. Neha, SC 14 Jul 2025; also R.M. Malkani 1973).
- DPDP Act 2023 §3(c)(i) personal/domestic-purpose exemption; Rules notified 14 Nov 2025, substantive obligations from ~14 May 2027.
- BSA 2023 §63 governs electronic-record admissibility (replaced Evidence Act §65B from 1 Jul 2024; dual certificate, hash values).
- LiteRT QNN NPU accelerator shipped 24 Nov 2025; on Snapdragon 8 Elite Gen 5, "over 56 models run in under 5ms with the NPU" and up to 100× CPU / 10× GPU speedups.
- MediaPipe LLM Inference is in maintenance-only mode; Google recommends LiteRT-LM.
- Financial literacy = 27% of adults nationally (urban 33%, rural 24%, women 21%; NCFE-FLIS 2019, 75,000+ respondents).
- Meta Omnilingual ASR (10 Nov 2025): 1,600+ languages, "78% have a character error rate below 10%," Apache 2.0.

**Do NOT claim:**
- "The NPU is saturated" without a number on screen.
- "The delta cannot be wrong."
- Any verdict / score / risk / fraud / mis-selling label.
- Guaranteed Tamil ASR accuracy (raw Whisper-small is 93.3% WER on FLEURS Tamil).
- That recording is unconditionally legal.
- That Gemma runs on the NPU.
- That the Sabka Bima / Insurance (Amendment) Act 2025 mandates need-analysis or signed benefit illustrations (it does not — that's the 2024 master circular; the 2025 Act is mainly 100% FDI + §40(2A) commission-disclosure power).
- "RBI 2026 mis-selling = RBC violation" (RBI only said it is working on guidelines — UNVERIFIED).
- The "SecondSense" Bengaluru winner (could not be verified).

## Recommendations

1. **Lock the concept now; do not revisit.** Build VAAKKU (or the registered name) exactly as specified. The verified rubric confirms it is a strong fit — do not spend a minute reconsidering the idea.
2. **Treat three things as pass/fail gates, in order:** (a) NPU smoke test by H+1 — if blocked on OriginOS, fall back to the debug-overlay NPU model; (b) ASR ladder by H+6 — if verbatim Tamil isn't clearly better, freeze on number-spotting; (c) precision on the adversarial fixture by H+17 — if you can't keep DIFFERS false positives near zero, widen tolerances toward NOT IN DOCUMENT. Each gate has a defined fallback, so no gate can sink the demo.
3. **Spend effort proportional to the rubric:** end product (30%) and novelty (20%) dominate — a flawless small demo beats a broken big one. Protect the one big Tamil delta line and the NPU latency display above all else.
4. **Make the 25% telemetry real:** camera + mic + NPU genuinely active during the build; Office Kit used continuously, not just in the demo — HackTracker logs it.
5. **Rename only if permitted; otherwise drop "guard" imagery** and keep KAAVAL as a registered label with neutral framing.
6. **Benchmarks that would change the plan:** if the NPU is truly inaccessible on the iQOO 15 after the H+1 smoke test AND the debug-overlay fallback also fails, pivot the NPU story to the precompiled AI-Hub Whisper-Small on QNN for the demo latency beat; if Dolphin/Omnilingual both fail on-device by H+6, ship English-only capture with Tamil-numeral normalization and reposition Tamil as roadmap.

## Caveats
- The official iQOO/Reskilll **terms & originality page is JavaScript-rendered and could not be read directly**; the originality rule (V3) is inferred from Reskilll's public guidance and standard hackathon practice — verify the exact wording with organizers at check-in.
- **NPU on OriginOS is unverified on vivo hardware specifically** — the mechanism-level risk is low (Qualcomm unsigned-PD fastrpc path, not an OEM whitelist), but the hour-1 smoke test is mandatory and a CPU/GPU fallback must be ready. The brand-new SM8850 (Hexagon v81) also introduces SoC-recognition risk if the QAIRT/QNN SDK is not current.
- **Offline Tamil ASR quality is the single largest execution risk** and is explicitly gated; raw Whisper-small is unusable for verbatim Tamil, so the design deliberately relies on number/keyword spotting.
- Whether the default recognizer on the iQOO 15 is Google or vivo Jovi is unverified until you have the loaner device.
- No public results from earlier 2026 City Battles (Bengaluru 29–30 Aug, Pune 5–6 Sep) were found to calibrate winning patterns — plan to the rubric, not to precedent.