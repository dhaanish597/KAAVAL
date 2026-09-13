# P9 — three strings to insert into `app/src/main/res/values/strings.xml`

Written by the domain terminal. **The domain terminal did not edit `strings.xml`** —
the app terminal owns that file. Insert these three verbatim, each with a
`<!-- TAMIL-REVIEW -->` comment, then regenerate
`evidence/tamil_strings_for_review.md`.

No other new strings are needed for the screen: every row's value phrase,
written phrase and follow-up question already exists in `strings.xml` (§8.3
`v_*` keys and the four §8.4 `followup_*` keys). `DecisionGate` emits only
those existing keys.

```xml
<!-- TAMIL-REVIEW -->
<string name="gate_title">கையெழுத்திடும் முன்</string>
<!-- TAMIL-REVIEW -->
<string name="gate_sub">இவற்றைக் கேளுங்கள். பதிலை எழுத்தில் கேளுங்கள்.</string>
<!-- TAMIL-REVIEW -->
<string name="gate_empty">இந்த ஆறு விஷயங்களில் வேறுபாடு எதுவும் காணப்படவில்லை</string>
```

| key | Tamil | English gloss (not a string resource) |
|---|---|---|
| `gate_title` | கையெழுத்திடும் முன் | Before you sign |
| `gate_sub` | இவற்றைக் கேளுங்கள். பதிலை எழுத்தில் கேளுங்கள். | Ask these. Ask for the answer in writing. |
| `gate_empty` | இந்த ஆறு விஷயங்களில் வேறுபாடு எதுவும் காணப்படவில்லை | No difference was found in these six items |

## Why `gate_empty` is worded this way

It states a scoped fact about six items and stops there. It must **not** be
softened into "nothing to check", "all clear", "looks fine" or anything that
reads as a judgement that the product is safe. The app compared six things and
found no difference in those six; it knows nothing about the rest of the
document and has no opinion about the policy or the person selling it
(CLAUDE.md #1, #2).

The empty state is also the common case in a short session — most sessions will
end with fewer than six topics observed at all — so this is the line a judge is
most likely to read on screen.
