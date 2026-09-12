# ASR slot accuracy — VAAKKU domain

**Status: P1 placeholder. No ASR pre-screen transcripts exist yet.**

`evidence/asr_prescreen/<engine>/<file>.txt` does not exist or has no
engine subdirectory with any `.txt` files — the laptop pre-screen
(build plan §11.3 item 1) is a P2 task. This is deliberately honest
about being empty rather than printing an invented accuracy number
(CLAUDE.md #7, #8).

The computation itself is real and already exercised: `LabelsTest`
(`:domain:test`) runs every one of `testdata/testaudio/labels.json`'s
15 scripts through the exact same SpokenExtractor + SlotChecker path
this task uses, and all of them pass today, text-only. Once P2 drops
real transcripts under `evidence/asr_prescreen/<engine>/`, this task
starts reporting real per-engine, per-file slot accuracy — no code
change needed.
