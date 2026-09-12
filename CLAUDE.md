# CLAUDE.md — VAAKKU (read automatically at the start of every session)

## Who you are working with
- **The human (Dhaanish)** holds the phone, speaks, points the camera, taps "Allow", moves files with Office Kit, talks to organizers, and reviews Tamil. You cannot do these things. When you need them, print a HUMAN ACTION block (format below) and wait.
- **Claude chat** is the planner. If you are blocked twice on the same error, stop and write a short diagnosis the human can take to Claude chat.

## Your memory
You have no memory between sessions. At the start of **every** session, read in this order:
1. `CLAUDE.md` (this file)
2. `STATUS.md` (where we are, what's decided, what's next)
3. `docs/VAAKKU_BUILD_PLAN.md` (the full specification; it wins over every other document)

## The product in one breath
Offline Android app for the buyer. Mic (Tamil–English speech) + camera (English benefit illustration) → a claim ledger of 6 types (RETURN_RATE, GUARANTEE, LOCK_IN, LIQUIDITY, BUNDLING, CHARGES) → one large Tamil line showing where what was **said** differs from what is **written**.

## Non-negotiable rules
1. **No verdicts. Ever.** No field, class, string or UI element may express risk, score, severity, fraud, suspicion or judgement of a person. User-facing states are only MATCHES / NOT_IN_DOCUMENT / DIFFERS. PENDING and UNCERTAIN are **silent**. The schema-guard test and `checkBannedWords` enforce this; never weaken them.
2. **Default failure is silence.** When in doubt (low confidence, hedge, negation ambiguity, conditional, normalization ambiguity, OCR low confidence), the state is UNCERTAIN, not DIFFERS and not NOT_IN_DOCUMENT.
3. **Offline.** No INTERNET permission in the merged manifest (`scripts/check_manifest.sh`). No networking libraries.
4. **Audio never touches disk.** No "save audio", not even in debug. Text transcripts may be exported in debug.
5. **`domain/` is pure JVM.** No `android.*`, `androidx.*`, ML Kit, sherpa or LiteRT imports there.
6. **Originality.** Fresh repo, code written in-window. Never copy code from pre-event prototypes. Libraries, models and official samples (as reference) are fine.
7. **Evidence or it didn't happen.** A gate passes only with real command output, a file in `evidence/`, an adb screenshot, or a human MEASUREMENT line in STATUS.md.
8. **Honest labels.** Never display "NPU" unless logcat proves NPU dispatch. Never claim accuracy numbers you did not measure.
9. **No colour coding of states.** No red/amber/green. Violet stamp ink only marks a DIFFERS card. No sound, ever.

## Never do
`git reset --hard` · delete `models/` · `adb uninstall` (it wipes the pushed models) · upgrade dependency versions · remove or skip tests to pass a gate · add network libraries · introduce old names (KAAVAL internals, CONSISTENT, UNSUPPORTED, CONTRADICTED, Judge, verdict).

## Always do
- Before every commit: `./gradlew :domain:test :domain:fixtureReport checkBannedWords`.
- Before every install: `scripts/check_manifest.sh` and a successful app build.
- Install with replace (`adb install -r` or the bundletool script once NPU is integrated).
- Commit after every green step with a message like `P3.2 row assembler + OCR mapping`.
- Update `STATUS.md` after every task: gate table, evidence paths, decisions, open issues, handoff notes.
- Before any Red Light window: make sure the newest working build is installed on the phone and write a "Next Red Light test list" in STATUS.md.

## Red Light
If the human says "RED LIGHT", check the "Red Light ruling" in STATUS.md. Under any ruling: no Android Gradle builds unless the ruling explicitly allows them. Only [R-OK] tasks (domain, fixtures, packet CLI, docs), and only if the ruling allows the laptop to be used at all.

## Environment
- Windows laptop, Git Bash shell. `./gradlew` works in Git Bash. `adb` should be on PATH; otherwise use `<sdk.dir>/platform-tools/adb.exe`.
- Phone: iQOO 15 (Android 16 / OriginOS 6, Snapdragon 8 Elite Gen 5 / SM8850 / Hexagon v81), connected by USB.
- Models on phone: `/sdcard/Android/data/app.vaakku/files/models/<model-dir>/`.
- Package: `app.vaakku` (no debug suffix). minSdk 31, target/compile 36, arm64-v8a only.

## Useful commands
- Screenshot: `adb exec-out screencap -p > evidence/<name>.png`
- NPU logcat: `adb logcat -d | grep -iE "litert|qnn|dispatch|htp" > evidence/<gate>_npu_logcat.txt`
- App logcat: `adb logcat -d --pid=$(adb shell pidof -s app.vaakku) > evidence/<name>.txt`

## HUMAN ACTION block format
```
=== HUMAN ACTION NEEDED ===
WHAT:   <one line>
WHY:    <one line>
STEPS:  1. ...  2. ...  3. ...
REPORT: <exactly what to paste back>
===========================
```
