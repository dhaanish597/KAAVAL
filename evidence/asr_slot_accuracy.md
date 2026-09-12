# ASR slot accuracy — VAAKKU domain

Transcripts scored: 60, across 4 engine(s).

Each transcript in `evidence/asr_prescreen/<engine>/<file>.txt` is run through the
real `SpokenExtractor` and compared with `testdata/testaudio/labels.json`. A slot
counts as correct only if the extracted VALUE matches the label exactly.

## Summary — engines ranked by slot accuracy

| engine | correct / total | slot accuracy |
|---|---|---|
| whisper_small_ta | 11 / 28 | 39% |
| omnilingual_300m | 5 / 28 | 18% |
| dolphin_base | 3 / 28 | 11% |
| dolphin_small | 0 / 28 | 0% |

## dolphin_base

| file | correct / total | accuracy | missed slots (expected -> actual) |
|---|---|---|---|
| R01_demo_pitch | 1 / 6 | 17% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)`<br>BUNDLING: expected `true`, got `(nothing extracted)` |
| T01_guarantee_fd | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T02_lockin_liquidity | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T03_bundling | 0 / 1 | 0% | BUNDLING: expected `true`, got `(nothing extracted)` |
| T04_charges | 0 / 1 | 0% | CHARGES: expected `false`, got `(nothing extracted)` |
| T05_honest_rate | 0 / 2 | 0% | GUARANTEE: expected `false`, got `(nothing extracted)`<br>RETURN_RATE: expected `4,8`, got `(nothing extracted)` |
| T06_honest_lockin | 2 / 2 | 100% | — |
| T07_selfcorrect | 0 / 1 | 0% | LOCK_IN: expected `60`, got `(nothing extracted)` |
| T08_hedge | 0 / 1 | 0% | RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T09_conditional | 0 / 1 | 0% | GUARANTEE: expected `false`, got `(nothing extracted)` |
| T10_english | 0 / 3 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T11_numbers | 0 / 0 | n/a (no expected slots) | — |
| T12_formal_tamil | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T13_noisy_T01 | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T14_noisy_T02 | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |

**dolphin_base overall: 3 / 28 (11%)**

## dolphin_small

| file | correct / total | accuracy | missed slots (expected -> actual) |
|---|---|---|---|
| R01_demo_pitch | 0 / 6 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)`<br>BUNDLING: expected `true`, got `(nothing extracted)`<br>CHARGES: expected `false`, got `true` |
| T01_guarantee_fd | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T02_lockin_liquidity | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T03_bundling | 0 / 1 | 0% | BUNDLING: expected `true`, got `(nothing extracted)` |
| T04_charges | 0 / 1 | 0% | CHARGES: expected `false`, got `(nothing extracted)` |
| T05_honest_rate | 0 / 2 | 0% | GUARANTEE: expected `false`, got `(nothing extracted)`<br>RETURN_RATE: expected `4,8`, got `(nothing extracted)` |
| T06_honest_lockin | 0 / 2 | 0% | LOCK_IN: expected `60`, got `(nothing extracted)`<br>LIQUIDITY: expected `nilBefore:60`, got `(nothing extracted)` |
| T07_selfcorrect | 0 / 1 | 0% | LOCK_IN: expected `60`, got `36` |
| T08_hedge | 0 / 1 | 0% | RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T09_conditional | 0 / 1 | 0% | GUARANTEE: expected `false`, got `(nothing extracted)` |
| T10_english | 0 / 3 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T11_numbers | 0 / 0 | n/a (no expected slots) | — |
| T12_formal_tamil | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T13_noisy_T01 | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T14_noisy_T02 | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |

**dolphin_small overall: 0 / 28 (0%)**

## omnilingual_300m

| file | correct / total | accuracy | missed slots (expected -> actual) |
|---|---|---|---|
| R01_demo_pitch | 1 / 6 | 17% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)`<br>BUNDLING: expected `true`, got `(nothing extracted)` |
| T01_guarantee_fd | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T02_lockin_liquidity | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T03_bundling | 0 / 1 | 0% | BUNDLING: expected `true`, got `(nothing extracted)` |
| T04_charges | 0 / 1 | 0% | CHARGES: expected `false`, got `(nothing extracted)` |
| T05_honest_rate | 1 / 2 | 50% | RETURN_RATE: expected `4,8`, got `(nothing extracted)` |
| T06_honest_lockin | 0 / 2 | 0% | LOCK_IN: expected `60`, got `(nothing extracted)`<br>LIQUIDITY: expected `nilBefore:60`, got `(nothing extracted)` |
| T07_selfcorrect | 0 / 1 | 0% | LOCK_IN: expected `60`, got `(nothing extracted)` |
| T08_hedge | 0 / 1 | 0% | RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T09_conditional | 1 / 1 | 100% | — |
| T10_english | 1 / 3 | 33% | RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T11_numbers | 0 / 0 | n/a (no expected slots) | — |
| T12_formal_tamil | 0 / 2 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T13_noisy_T01 | 1 / 2 | 50% | RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T14_noisy_T02 | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |

**omnilingual_300m overall: 5 / 28 (18%)**

## whisper_small_ta

| file | correct / total | accuracy | missed slots (expected -> actual) |
|---|---|---|---|
| R01_demo_pitch | 2 / 6 | 33% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)`<br>BUNDLING: expected `true`, got `(nothing extracted)`<br>CHARGES: expected `false`, got `(nothing extracted)` |
| T01_guarantee_fd | 1 / 2 | 50% | RETURN_RATE: expected `8`, got `(nothing extracted)` |
| T02_lockin_liquidity | 0 / 2 | 0% | LOCK_IN: expected `12`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T03_bundling | 0 / 1 | 0% | BUNDLING: expected `true`, got `(nothing extracted)` |
| T04_charges | 1 / 1 | 100% | — |
| T05_honest_rate | 1 / 2 | 50% | RETURN_RATE: expected `4,8`, got `(nothing extracted)` |
| T06_honest_lockin | 0 / 2 | 0% | LOCK_IN: expected `60`, got `(nothing extracted)`<br>LIQUIDITY: expected `nilBefore:60`, got `(nothing extracted)` |
| T07_selfcorrect | 0 / 1 | 0% | LOCK_IN: expected `60`, got `36` |
| T08_hedge | 1 / 1 | 100% | — |
| T09_conditional | 0 / 1 | 0% | GUARANTEE: expected `false`, got `true` |
| T10_english | 0 / 3 | 0% | GUARANTEE: expected `true`, got `(nothing extracted)`<br>RETURN_RATE: expected `8`, got `(nothing extracted)`<br>LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |
| T11_numbers | 0 / 0 | n/a (no expected slots) | — |
| T12_formal_tamil | 2 / 2 | 100% | — |
| T13_noisy_T01 | 2 / 2 | 100% | — |
| T14_noisy_T02 | 1 / 2 | 50% | LIQUIDITY: expected `withdrawAfter:12`, got `(nothing extracted)` |

**whisper_small_ta overall: 11 / 28 (39%)**

