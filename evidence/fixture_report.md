# Fixture report — VAAKKU domain

**Status: P0 placeholder. No fixtures exist yet, so there is nothing to report.**

This task is deliberately honest about being empty. It exists in P0 only so
that the pre-commit guard list (`:domain:test :domain:fixtureReport
checkBannedWords`, CLAUDE.md "Always do") is runnable from the first commit.

P1 builds the real thing: >= 26 fixtures (10 adversarial, 6 demo-path,
6 normalization, 4 honest-agent) and this file becomes the confusion
matrix of expected {MATCHES, NOT_IN_DOCUMENT, DIFFERS, SILENT} against
actual, with **precision on DIFFERS printed (must be 1.00)**.

Expected shape once P1 lands:

| expected \ actual | MATCHES | NOT_IN_DOCUMENT | DIFFERS | SILENT |
|---|---|---|---|---|
| MATCHES | | | | |
| NOT_IN_DOCUMENT | | | | |
| DIFFERS | | | | |
| SILENT | | | | |

**Precision on DIFFERS: not yet measured.**
