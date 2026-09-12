# VAAKKU — Everything YOU Have to Do (the human task list)

Claude chat plans. Claude Code writes and builds the code. **You are the hands, eyes, ears and voice.** Claude Code cannot hold the phone, speak, point the camera, tap "Allow", move files with Office Kit, talk to organizers, or tell whether Tamil sounds natural. This file lists every one of those jobs, in order, in plain words, with the reason for each.

**How to read this:** each task has *Why*, *Steps*, and *Done when*. Times are rough.

---

## PART A — TONIGHT (before check-in)

### A0. The one rule for tonight (2 min)
**Allowed tonight:** installing software, downloading models and SDKs, building *Google's* sample app, recording test audio, printing the prop document, writing notes and collecting documents.
**Not allowed tonight:** writing any VAAKKU app code, including the "domain state machine" that one research report suggested writing tonight. Do not open your old prototype folder in Claude Code.
*Why:* the rules say app code must be written during the event. Assets and tools are fine; code is not. Staying clean means nobody can question your originality.

### A1. Laptop software (45–60 min)
*Why:* if anything is missing tomorrow, you lose an hour on venue Wi-Fi.
**Steps:**
1. **Android Studio** (latest stable). Open *Tools → SDK Manager*:
   - *SDK Platforms* tab: tick **Android 16 (API 36)**.
   - *SDK Tools* tab: tick **Android SDK Platform-Tools** and **Android SDK Build-Tools** (latest). NDK is **not** needed.
   - At the top of that window is the **Android SDK Location** (usually `C:\Users\clash\AppData\Local\Android\Sdk`). Copy it into a new text file `C:\Users\clash\Documents\VAAKKU\handoff\notes_for_claude.txt`. (Last time a wrong SDK path silently broke the build.)
2. **Put `adb` on PATH:** Windows search → "Edit environment variables for your account" → select **Path** → **Edit** → **New** → paste `<your SDK path>\platform-tools` → OK. Open a **new** terminal and type `adb version`. You should see a version number.
3. **Java for command-line tools:** in the same environment-variables window, click **New** (user variable): name `JAVA_HOME`, value `C:\Program Files\Android\Android Studio\jbr`. New terminal → `java -version` should work.
4. **Git for Windows** (this includes *Git Bash*, which Claude Code uses). Test: `git --version`.
5. **Node.js LTS**. Test: `node -v`.
6. **Python 3.11+** (on the first installer screen tick "Add python.exe to PATH"). Then run `pip install sherpa-onnx numpy soundfile`. Test: `python -c "import sherpa_onnx; print(sherpa_onnx.__version__)"`.
7. **Claude Code**: install and log in. Test: `claude --version`.
8. **Office Kit PC app** from `pc.vivoglobal.com` (needs version 6.0.0 or newer). Install and open it once.
9. **bundletool**: go to `github.com/google/bundletool/releases`, download `bundletool-all-<version>.jar`, save as `C:\tools\bundletool-all.jar`. (Installing the NPU build needs it.)
10. Microsoft Edge is already on Windows; the grievance-packet tool uses it to make PDFs. Nothing to do.

**Done when:** `adb version`, `java -version`, `git --version`, `node -v`, the Python import line, and `claude --version` all print versions in a fresh terminal.

### A2. Pre-download Gradle libraries with a throw-away project (30 min)
*Why:* the first Android build downloads hundreds of MB of libraries. Doing it tonight means a bad venue Wi-Fi can't block you. The throw-away project is just the Android Studio wizard. It is not your app.
**Steps:**
1. Android Studio → *New Project* → **Empty Activity** (the Compose one) → name `scratch` → Minimum SDK **API 31**.
2. Open `app/build.gradle.kts`. Inside `dependencies { }` add these lines (Android Studio will offer to move them to the version catalog; accept or ignore):
   ```
   implementation("androidx.camera:camera-core:<latest>")
   implementation("androidx.camera:camera-camera2:<same>")
   implementation("androidx.camera:camera-lifecycle:<same>")
   implementation("androidx.camera:camera-view:<same>")
   implementation("com.google.mlkit:text-recognition:16.0.1")
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<latest>")
   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<latest>")
   implementation("com.google.ai.edge.litert:litert:<the version from task A4 step 5>")
   ```
   Use the latest stable version numbers Android Studio suggests (it underlines outdated ones).
3. Click **Sync Now**, then *Build → Make Project*. Wait until it succeeds.
4. Create `handoff\versions_from_scratch.txt` and paste in: the full contents of the scratch project's `gradle/libs.versions.toml`, the exact versions you used for the lines above, and the Gradle version from `gradle/wrapper/gradle-wrapper.properties`.
5. Close the scratch project. You can delete its folder; the downloads stay cached.

**Done when:** the scratch project builds, and `versions_from_scratch.txt` exists.

### A3. Download the speech-recognition models (30–60 min, about 1.5 GB)
*Why:* these are the "ears" of the app. We will test several and pick the one that hears Tamil–English best. They must never be downloaded at the venue.
**Steps:**
1. Make the folder `C:\Users\clash\Documents\VAAKKU\handoff\models\`.
2. Download these files into it (paste each link into the browser):
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02.tar.bz2`
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2`
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12.tar.bz2`
   - **Silero VAD** (detects when someone is speaking): on `https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models` find **`silero_vad.onnx`** and download it.
   - **Tamil-tuned Whisper small**: open `https://huggingface.co/ippocode/indic-asr-onnx`, go to *Files*, open the folder **`whisper-small-ta`**, and download `encoder.int8.onnx`, `decoder.int8.onnx`, and `tokens.txt` into `models\whisper-small-ta\`. Open the source model's page (`vasista22/whisper-tamil-small`) and note its license.
3. Extract each `.tar.bz2`: open a terminal in `handoff\models\` and run `tar -xf <filename>.tar.bz2` (works on Windows 10/11). Then delete the `.tar.bz2` files.
4. **sherpa-onnx Android library:** on `https://github.com/k2-fsa/sherpa-onnx/releases` open the **latest** release. Download the file named like **`sherpa-onnx-<version>.aar`** into `handoff\`. Also download that release's **Source code (zip)**, because Claude Code needs its Kotlin API files from the same version. Write the version number in `notes_for_claude.txt`.
5. Write `handoff\models\MANIFEST.md`: for each folder, the folder name, size, where you downloaded it, the license, and today's date.

**Done when:** every model folder contains at least one `.onnx` file and a `tokens.txt` (the VAD is a single `.onnx`), and MANIFEST.md is written.

### A4. Prepare the NPU "canary" test (45–60 min)
*Why:* the biggest technical unknown is whether the phone's NPU (the AI chip) can be used on OriginOS. Google has an official sample app that runs a model on the Qualcomm NPU. If you build it tonight, you can install it on the loaner phone in the first 30 minutes tomorrow and know the answer before we write a single line of NPU code. It is Google's app, not yours, like installing a test tool.
**Steps:**
1. Open `https://github.com/google-ai-edge/litert-samples` → green **Code** button → **Download ZIP**. Unzip to `handoff\litert-samples\`.
2. Find the folder **`android_jit`** inside `image_segmentation/kotlin_npu/` (the repo was reorganised, so use File Explorer's search for `android_jit`).
3. Open its **README**. Follow the section on **setting up the NPU runtime libraries** exactly. The runtime library version must match the LiteRT version in that project's `gradle/libs.versions.toml`.
4. Open that `android_jit` folder in Android Studio and build the app bundle the way the README says (it produces `app-release.aab`). If the build fails, write down the exact error and bring it to Claude chat. **Do not spend more than 1 hour on this.**
5. Copy the LiteRT version number from `gradle/libs.versions.toml` into `versions_from_scratch.txt` (and use it in task A2 if you haven't finished that yet).
6. Copy the model file from the sample's assets (named like `selfie_multiclass_256x256.tflite`) to `handoff\npu\`.
7. Keep the downloaded runtime-libraries folder; Claude Code will need it in phase P4. Note its path in `notes_for_claude.txt`.

**Done when:** you have `app-release.aab` from the sample, the `.tflite` model, and the runtime libraries folder.

### A5. Record the test audio (60 min, with your teammate)
*Why:* every speech model will be tested on exactly the same recordings, so the comparison is fair. The recordings are also the demo's safety net if the hall is too noisy.
**Setup:** your teammate plays the bank agent and speaks naturally, the way a real relationship manager talks. Put the phone recorder on a table about 1 metre away (like the real demo). Record **one file per line**, with 1 second of silence before and after.

| File name | Line to say | Where |
|---|---|---|
| T01_guarantee_fd | Sir, இது FD மாதிரி தான். Guaranteed எட்டு percent return. | quiet |
| T02_lockin_liquidity | Lock-in ஒரு வருஷம் தான் sir. One year கழிச்சு full-ஆ எடுக்கலாம். | quiet |
| T03_bundling | இந்த policy எடுத்தா தான் loan sanction ஆகும் sir, இது compulsory. | quiet |
| T04_charges | Charges எதுவும் இல்லை sir, zero commission. | quiet |
| T05_honest_rate | Return guarantee இல்லை sir. Illustration-ல நாலு percent, எட்டு percent ரெண்டு scenario இருக்கு. | quiet |
| T06_honest_lockin | Lock-in அஞ்சு வருஷம். அதுக்கு முன்னாடி surrender value கிடையாது. | quiet |
| T07_selfcorrect | Lock-in மூணு வருஷம்… இல்ல இல்ல, அஞ்சு வருஷம். | quiet |
| T08_hedge | Up to எட்டு percent வரைக்கும் வரலாம் sir. | quiet |
| T09_conditional | Guaranteed-ஆ இருந்தா எட்டு percent கிடைக்கும், ஆனா இது guaranteed இல்லை. | quiet |
| T10_english | It's like a fixed deposit, guaranteed eight percent, and you can withdraw after one year. | quiet |
| T11_numbers | Premium வருஷத்துக்கு ஒரு லட்சம் இருபதாயிரம், term பத்து வருஷம். | quiet |
| T12_formal_tamil | இது உறுதியான எட்டு சதவீத வருமானம் தரும். | quiet |
| T13_noisy_T01 | Same as T01 | with a fan/TV/people chatting |
| T14_noisy_T02 | Same as T02 | with a fan/TV/people chatting |

You may reword a line so it sounds natural, **as long as the numbers and meaning stay the same.** If you change a value, tell Claude Code in phase P1 so the labels match.

**Convert to the right format** (16 kHz, mono, 16-bit WAV):
- Easy way, **Audacity** (free): *File → Import → Audio* → *Tracks → Mix → Mix Stereo Down to Mono* → set **Project Rate** (bottom left) to **16000** → *File → Export → Export as WAV* → encoding **Signed 16-bit PCM**.
- Or with ffmpeg: `ffmpeg -i T01.m4a -ar 16000 -ac 1 -sample_fmt s16 T01_guarantee_fd.wav`

Save them all into `handoff\testaudio\`. Play two of them back to check.
**Done when:** 14 WAV files exist with the exact names above.

### A6. Make and print the prop document (30–45 min + printing)
*Why:* the camera reads this paper in the demo. A clean, well-designed prop makes the OCR (text reading) reliable.
**Content** (English, one page, a **made-up** product; never a real insurer's name or logo):
- Title: "Benefit Illustration — Suraksha Savings Plan (Non-Linked, Participating)" (fictional)
- Policy term: 10 years · Annualised premium: ₹1,20,000
- Assumed rate of return (illustrative): **4% p.a. and 8% p.a.**
- "**Returns are NOT guaranteed.** The 4% and 8% rates are illustrative only."
- Guaranteed returns: **No**
- Lock-in period: **5 years**
- Surrender value: **Nil before completion of 5th policy year**
- Premium allocation charge: **5% in year 1**
- **Nothing about loans** (on purpose)

**Design rules for the camera:** white **matte** paper (not glossy), black text, labels at least 12 pt and values at least 14 pt, a simple two-column table with clear spacing, no watermark or background image. Print **3 copies**.
*Tip:* ask Claude chat to generate this PDF for you.
**Done when:** 3 clean printed copies are in your bag.

### A7. Review the Tamil text (20 min)
*Why:* judges in Chennai will read the Tamil. One unnatural phrase undercuts the whole "for the buyer" story.
**Steps:** open `VAAKKU_BUILD_PLAN.md` sections **§8.2–§8.4** (screen text) and **§5.3** (the word list the app listens for). Fix anything that sounds unnatural. Add words real agents actually say (other ways of saying "guaranteed", "withdraw", "compulsory", colloquial number forms). Write corrections in `notes_for_claude.txt` as `key: corrected Tamil`.
**Done when:** you are happy that every Tamil line sounds like something a real person would read or say.

### A8. Collect the handoff folder (15 min)
Put everything in `C:\Users\clash\Documents\VAAKKU\handoff\` with these names, so the prompts work without editing:
- `CLAUDE.md`
- `VAAKKU_BUILD_PLAN.md`, `VAAKKU_CLAUDE_CODE_PROMPTS.md`, `VAAKKU_HUMAN_TASKS.md`
- `VAAKKU_FINAL_LOCKED_SPEC.md` (rename the final reconciliation research file to this)
- `VAAKKU_NOVELTY_RESEARCH.md` (the first research report) and `FABLE_HARDENING_PLAYBOOK.md` (Fable's report)
- `KAAVAL_UI_DESIGN_SPEC_v2.md`, `kaaval-tokens.css`, `kaaval-fonts.zip`
- `versions_from_scratch.txt`, `notes_for_claude.txt`
- `models\` (with MANIFEST.md), `testaudio\`, `npu\`, `sherpa-onnx-<ver>.aar`, the sherpa source zip, the litert-samples folder
Copy the whole folder to a USB stick as a backup.

### A9. (Optional) Test phone-side control of Claude Code (15 min)
*Why:* during Red Light the laptop may be off-limits as a build machine. If organizers allow the laptop to keep running while you control it from the phone, **Remote Control** lets you keep Claude Code going from the Claude app.
**Steps:** in a throw-away folder run `claude`, type `/remote-control`, scan the QR code with the Claude app on your phone, send one message from the phone. Note: the laptop must stay awake.
**Done when:** you know whether it works for you. If it's awkward, skip it; Red Light will be your testing time anyway.

### A10. Laptop power settings (2 min)
Control Panel → Power Options → "Choose what closing the lid does" → **Plugged in: Do nothing**. Sleep when plugged in: **Never**.
*Why:* a sleeping laptop drops Office Kit and Remote Control connections.

### A11. Pack (10 min)
Laptop + charger · **USB-C data cable** (test it: the phone must offer "File transfer" when plugged in; charge-only cables won't work) + a spare · extension board · 3 printed props · **a phone stand or small tripod** (steady camera for scanning and filming) · a second phone to film the demo · earphones · a printed copy of the organizer questions (B1) · water and snacks.

### A12. Sleep at least 6 hours.
A tired human is the biggest single risk in a 30-hour build.

---

## PART B — AT CHECK-IN AND THE FIRST HOUR

### B1. Ask the organizers (write their answers into STATUS.md, "rulings")
Claude Code reads these answers and changes its behaviour.
1. **Red Light:** can the laptop stay on if we control it only from the phone (Office Kit Remote PC or the Claude app)? Can it compile during Red Light, or must it be completely idle?
2. Is a cloud coding assistant used **from the phone** allowed during Red Light?
3. **Originality:** before the event we prepared designs, documents, test audio, printed props, and downloaded models/SDKs. All app code will be written here in a fresh repository. Is that OK? (Mention that you submitted a Phase-1 video, and confirm that rebuilding from scratch here is fine.)
4. **Name:** we registered as KAAVAL. May we present as VAAKKU?
5. **HackTracker:** what do we install, and what exactly does it measure?
6. Exact **Red/Green schedule** and the **submission deadline**.
7. **Demo format:** length, live or recorded, can we mirror the phone screen, how noisy is the room, is there a mic?
8. **Submission contents:** APK? repo link? video? deck?
9. Can we sideload apps with USB debugging on the loaner phone? Any restrictions?
10. Is Wi-Fi available for laptops?

### B2. Set up the loaner iQOO 15 (30 min)
Menu names on OriginOS can differ slightly; use Settings search if you can't find something.
1. **Do not install system updates** during the event.
2. Connect to the venue Wi-Fi (needed only for setup).
3. Check that Google Play services are present. Sign in to a Google account if allowed (needed for Google's offline speech packs).
4. **Developer options:** Settings → About phone → tap **Software version** 7 times → enter the PIN.
5. In **Developer options:** **USB debugging ON**; **Install via USB ON** (if present); **Stay awake ON**; turn on any "USB debugging (security settings)" option if present.
6. Plug the USB cable into the laptop → on the phone choose **File transfer** → tap **Allow USB debugging** and tick **Always allow from this computer**. On the laptop, `adb devices` must show the phone as `device`.
7. **Every time Claude Code installs the app, watch the phone:** iQOO/vivo phones may pop up "Install via USB?". Tap **Install** quickly, before it times out.
8. **Offline speech packs (for the "Rung 0" test):** Settings → search "offline speech" (or Google app → Settings → Voice → Offline speech recognition) → download **Tamil (India)** and **English (India)** if offered. If they're not offered, just note that.
9. **Office Kit on the phone:** connect it to your laptop's Office Kit app. Test four things: send a file phone→laptop, send a file laptop→phone, screen mirroring, and Remote PC.
10. Write the Android version and OriginOS version (Settings → About phone) in STATUS.md.
11. **After the app is installed** (phase P0): Settings → Battery → background power management → allow **high background power use** for VAAKKU. Turn off power-saving modes. *Why:* OriginOS kills apps that run long in the background, which would stop a listening session.

### B3. Run the NPU canary (20 min, Claude Code will guide you)
Install Google's sample (from A4) on the loaner phone. Claude Code runs the bundletool commands; you tap "Install" on the phone. Open the app and point the camera at a person. Claude Code captures the log. **Result = PASS (NPU works) or FAIL (we will use the GPU and say so honestly).** Record it in STATUS.md.

---

## PART C — DURING THE BUILD: YOUR JOB IN EACH PHASE

### P0 — Bootstrap (first hour)
- Put `CLAUDE.md` in the repo root and the other files in `docs/`, as listed at the top of the prompts file. Copy `models\` to `repo\models\`, WAVs to `repo\testdata\testaudio\`, the `.aar` to `repo\app\libs\`.
- Paste **PROMPT 0**. Fill in your SDK path where it asks.
- Tap "Allow"/"Install" on the phone when asked.
- **Check the Tamil text renders properly** (no empty boxes, no broken letters). Tell Claude Code.
- Run the **Rung-0 probe** screen: Dev menu → Probe → Export. It tells us whether Google's offline Tamil recognizer works on this phone. If it says *on-device available: true* and *ta-IN installed*, we have one more speech engine to try.

### P1 — Domain (runs on the laptop only)
- Claude Code will show you the fixture scripts and labels. **Check that the Tamil phrases sound like real speech and the expected values match what you recorded.**
- That's all for this phase. Use the time for P2 testing or phone setup.

### P2 — Speech recognition (decision at hour 6)
1. When asked, keep the phone plugged in and unlocked while Claude Code pushes the models (can take a few minutes).
2. **Laptop pre-screen results:** Claude Code shows a table or transcripts for each engine. For T01–T04, check: did it get the **numbers** (8, one year) and the **key words** (guaranteed/கேரண்டி, loan, compulsory)?
3. **Phone bake-off:** Dev menu → Bake-off → *Run all* → wait → *Export*. Look at two columns: **slot accuracy** (higher is better) and **RTF** (lower is better; explained in Part E).
4. **Live-mic test** (the one that matters): put the phone face-up on a table; your teammate stands 1 metre away and speaks T01–T04 naturally. Watch the Live ASR screen for the top two engines. Do it once somewhere quiet and once in the noisiest realistic spot at the venue.
5. Report each result as one line, using this template:
   `MEASUREMENT G2: engine=<name> place=<quiet|hall> line=T01 heard="<what the screen showed>" values_ok=<2/2> delay_s=<seconds>`
6. If the choice isn't obvious, paste the numbers into Claude chat.

### P3 — Reading the document
1. Lighting: bright and even, **no reflection** on the paper. Lay the prop flat and put the phone on the stand about 30 cm above it.
2. Scan 5 times. After each scan, check the list shows all five: **4% & 8% (illustrative)**, **not guaranteed**, **lock-in 5 years**, **surrender nil before 5 years**, **5% charge**. Report ✓ or ✗ for each.

### P4 — NPU
1. The canary result (B3) should already be in STATUS.md.
2. On the scan screen, read the small label, e.g. "mask 7.9 ms · NPU". Run the benchmark (Dev menu → NPU benchmark) and report the three numbers (NPU / GPU / CPU).
3. If it says GPU instead of NPU, that's an **honest fallback**, not a failure. Tell Claude chat so the pitch wording gets adjusted.

### P5 — Putting it together (the most important testing)
Run the three tests Claude Code asks for:
- **(a)** the recorded demo pitch + prop, 5 times: the right cards must appear every time;
- **(b)** the honest recordings, 3 times: **no "differs" card at all**;
- **(c)** live with your teammate: demo lines 3 times, honest lines 2 times.

What to watch: the right cards appear; **nothing appears on honest speech**; the card is readable when you stand 1.5 m away; count seconds from the end of the sentence to the card; the app never makes a sound.
**If a "differs" card ever appears on honest speech, stop and report it with the BUG prompt. That is the single bug judges would punish hardest.**
Also review `evidence/tamil_strings_for_review.md` and send corrections.

### G6 — Go / No-Go (around hour 17)
Paste the G6 prompt, then take the report plus STATUS.md to Claude chat. Together you decide what to cut. After this point, no new big features.

### P6 — Receipt and the Office Kit moment
Practise this sequence 3 times and time it (target: 60 seconds or less):
End session → **Export** → on the laptop, open Office Kit → phone files → `Download/Vaakku/<session>` → copy to the `incoming` folder → run the packet command (Claude Code will tell you the exact line) → send the PDF back to the phone with Office Kit → open it.
*Why:* Office Kit use is 10% of the score and is measured from the device, so real use counts. Also use Office Kit during the day to move evidence files and screenshots.

### P7 — Hardening (hours 21–24)
**15-minute soak test:** start around 80% battery; phone face-up; start a session; your teammate says a script line every ~30 seconds. At 0, 5, 10 and 15 minutes, open the debug overlay and note **thermal status** and **battery %**. Check the session is still running at the end. Then do **3 full demo runs in airplane mode** on the final build. Report everything as MEASUREMENT lines.

### P8 — Demo and submission
Record the demo footage (a second phone on a tripod filming the table and screen). Ask Claude Code for real numbers for the deck from `evidence/`. Submit what the organizers asked for.

---

## PART D — RED LIGHT: WHAT YOU DO
During phone-only windows the laptop does little or nothing (depending on the ruling). That's your testing time:
1. Before Red Light starts, ask Claude Code to install the newest build and write the "Next Red Light test list".
2. Work through that list on the phone. Write results in the phone's notes as MEASUREMENT lines.
3. Rehearse the pitch and the Tamil lines with your teammate.
4. Film practice footage.
5. Use Office Kit to move evidence files from the phone to the laptop (real Office Kit usage).
6. When Green Light returns, paste your MEASUREMENT lines with the MEASURE prompt.

---

## PART E — MONITORING CHEAT-SHEET (what the numbers mean)
| Number | Plain meaning | Good | Bad → what to do |
|---|---|---|---|
| **RTF** (real-time factor) | Time to process speech ÷ length of the speech. 0.3 = 3 s of speech processed in 0.9 s | ≤ 0.5 | > 1.0 means it falls behind → smaller model or different thread count |
| **Slot accuracy** | % of key facts (numbers, guaranteed, loan-required…) the engine got right | ≥ 70% on clean clips | < 70% → recorded pitch becomes the main demo, live speech is a bonus |
| **Speech → card delay** | Seconds from the end of the sentence to the card appearing | ≤ 2.5 s | > 4 s feels broken → tell Claude chat |
| **OCR clauses** | How many of the 5 expected clauses a scan found | 5/5 | Missing ones → better light, steadier phone, bigger prop font |
| **OCR ms** | Time to read the page | < 1500 ms | Slower is OK but note it |
| **NPU mask ms + label** | Time to mask people in one camera frame, and which chip did it | NPU single-digit ms | Label must match the log proof; GPU is an honest fallback |
| **Thermal status** | 0 none, 1 light, 2 moderate, 3 severe, 4 critical | ≤ 2 after 15 min | ≥ 3 → tell Claude chat (fewer threads, lower frame rate) |
| **Thermal headroom** | 1.0 = the point where the phone starts slowing down | < 0.9 | ≥ 0.9 → same as above |
| **Battery drop / 15 min** | Energy use | < 8% | Higher → mention it; reduce frame rate |
| **Precision on DIFFERS** | Of all "differs" cards shown in tests, how many were truly different | **1.00, always** | Anything less → top-priority bug |
| **Recall on DIFFERS** | Of all real differences, how many we caught | 0.7+ is fine | We accept missing some to never falsely flag |

---

## PART F — WHEN TO COME BACK TO CLAUDE CHAT
- The ASR decision at hour 6 isn't obvious.
- Any gate fails twice.
- The NPU canary fails.
- A false "differs" card that Claude Code can't fix.
- An organizer ruling changes the plan (for example, a stricter Red Light).
- The G6 go/no-go.
- Before the freeze, for a final demo-script and Q&A rehearsal.
- Tamil wording doubts, the prop PDF, deck updates.
**How:** paste STATUS.md (or the relevant part) plus the numbers.

---

## PART G — NEVER DO THESE
- **Never uninstall the app from the phone.** It deletes the ~1 GB of pushed models; re-pushing takes time.
- Never update the phone OS, Android Studio, or accept "upgrade Gradle/AGP" prompts during the event.
- Never let Claude Code add internet permission or network libraries.
- Never point Claude Code at your old prototype folders or copy code from them.
- Never say these on stage: fraud, scam, risk score, verdict, "catches lying agents", "NPU saturated". Say: "it shows where what was said and what is written differ".
- Never record real strangers without telling them. The demo uses your teammate.
- Never let the phone lock during a demo session (Stay awake ON, screen kept on).

---

## PART H — DEMO-DAY CHECKLIST
1. Phone ≥ 80% charged. **Do Not Disturb ON** (no popups). Brightness high.
2. Final release build installed; open the app → the Setup checklist shows everything ready (models loaded, NPU/GPU label).
3. **Airplane mode ON — say it out loud.**
4. **Order of the demo:** scan the document **first** (the paper is on the table), **then** the agent speaks. The cards appear live, while he is still talking.
5. **Backup:** if the hall is too noisy, switch on the rehearsal toggle and say "here is the same pitch, recorded earlier, through the exact same on-device pipeline". Practise this switch twice.
6. **Office Kit beat:** after ending the session, turn **Wi-Fi on** (airplane mode can stay on) for the local transfer, and say: "The analysis happened offline. The app does not even have internet permission. Now I hand the receipt to my laptop, locally." Keep a slide with the app's permission list as proof (Claude Code can print it with `apkanalyzer` or `aapt`).
7. If you need screen mirroring for the audience, the same Wi-Fi rule applies. Show the permission-proof slide.
8. Props on the table, phone stand ready, laptop with Office Kit connected, teammate knows his lines.
