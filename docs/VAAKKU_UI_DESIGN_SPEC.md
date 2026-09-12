# VAAKKU — Mobile UI Design Specification

**Platform:** Android (iQOO 15, 6.85", design canvas 412 × 916 dp)
**Purpose:** Complete visual + interaction spec. Build frames from this, then HTML.
**Version:** v1 — pre-build, September 2026

> **Naming note:** this document says VAAKKU throughout because that is what the project brief says. Task A5 is still open. Build the wordmark as a single text layer on one frame so a rename costs you thirty seconds, not an afternoon.

---

## 1. The design thesis

Before any colour or type decision, one question has to be settled: **what category does this app look like it belongs to?**

The obvious move is the security aesthetic — near-black background, neon accent, pulsing threat indicators, a shield icon. Every fraud app on the Play Store looks like that. If VAAKKU looks like that, the jury classifies it as a scam detector in the first two seconds, and §3 of your brief is dead on arrival. The visual system loses the argument before you open your mouth.

So the visual system has to do the same job the schema does.

**VAAKKU looks like a record, not like a warning.**

The material language is **duplicate-form paper**. Carbonless copy sets — the white original, the canary second copy, the violet stamp. The thing every Indian adult has held at a bank counter, an RTO, a registrar's office. That is exactly what this product is: *the buyer finally gets a copy of what was said.*

Nobody has ever handed you a copy of the conversation. VAAKKU is that copy.

**Design concept: THE CARBON COPY.**

Everything follows from that. Square-ish corners, because forms have square corners. Hairline rules, because forms are ruled. Values sitting on a baseline under a label, because that is what a field is. A perforation between the spoken copy and the written copy, because that is where two copies separate. Violet stamp ink for the one thing that matters, because a stamp means *recorded*, not *accused*.

### 1.1 The colour rule that enforces §3

**Colour is used for exactly one thing in this app: marking a delta. Nothing else is chromatic.**

This is not restraint for its own sake. Look at what a traffic-light palette would do:

| If you used | It would mean | Which is |
|---|---|---|
| Green for `CONSISTENT` | "this deal is fine" | an endorsement — a verdict |
| Amber for `UNSUPPORTED` | "be careful" | a risk level — a score |
| Red for `CONTRADICTED` | "this is fraud" | an accusation |

A three-colour ramp **is** a risk score, drawn instead of computed. It fails the hard line in §3 without a single line of scoring code. So:

- `CONSISTENT` renders in **ink grey, at low emphasis.** Not green. It matches; there is nothing to look at.
- `UNSUPPORTED` renders as an **open field** — a label with an empty rule under it and a dotted border. Absence rendered as absence.
- `CONTRADICTED` renders as **two stacked statements separated by a violet perforation.** The signal is structural, not chromatic. Blow the frame up to full screen and the composition itself is the finding.

Say this out loud in the pitch. "We didn't colour-code it, because a colour code is a score."

### 1.2 Where the boldness goes

One memorable element, everything else quiet. The memorable element is **the Delta Card** (§6.1) — full-bleed, 40sp Tamil, a perforated seam. That is the screenshot that goes in the deck. Every other screen is deliberately plain so that card lands.

---

## 2. Two modes, one system

The app has two contexts with genuinely different constraints. Same tokens, different density.

### Session Mode (live, in the room)

The phone is **face-up on a table**, three feet from someone who can read the screen, while a conversation is happening. Constraints that follow:

- **The 1.5-metre test.** Alert text must be legible at arm's length plus table width, in branch-office fluorescent light. Minimum 34sp, target 40sp, contrast ≥ 7:1.
- **One fact per screen.** No scrolling, no tabs, no reading.
- **Legible to the holder, unremarkable to the room.** No red flash, no full-screen colour wash, no sound, ever. A screaming alert creates a confrontation the user is not equipped to have. The alert should look like a notification, not an accusation.
- **Thumb-only.** Every control in the bottom third. Nothing above 60% screen height is tappable during a session.
- **Light surface.** Bright ambient light is the norm in a branch. Paper beats a dark screen for glanceability; a Night theme exists for home visits and basements.

### Record Mode (after, or on the way home)

Reviewing, expanding, exporting. Here you can scroll, read, tap into detail, compare ledgers side by side. Denser, calmer, more type.

The transition between them is the app's one orchestrated motion moment (§8).

---

## 3. Design tokens

### 3.1 Colour — Day (default)

```
--paper            #EDEFE9   duplicate-form stock — app background
--paper-white      #FBFBF7   the original copy — cards, document side
--paper-canary     #F5EDCB   the buyer's copy — spoken-claim side only
--ink              #17242E   blue-black document ink — primary text
--ink-soft         #4E6070   secondary text, labels
--ink-faint        #8A98A2   placeholder, disabled, metadata
--rule             #C9CFC7   1dp hairline rules and card borders
--rule-strong      #A8B1A9   emphasised rule, active field underline
--stamp            #6B4E9E   violet stamp ink — THE DELTA. Nothing else.
--stamp-wash       #EDE6F5   violet tint fill
--destructive      #A03A2B   delete actions ONLY — never a verdict
--on-ink           #F7F8F5   text on ink surfaces
```

Contrast check: `--ink` on `--paper` = 12.6:1 (AAA). `--ink-soft` on `--paper` = 5.1:1 (AA). `--stamp` on `--paper` = 6.4:1 (AA, and AAA at 24sp+). `--ink-faint` is metadata only, never body text.

### 3.2 Colour — Night

Not an inversion. Retonalised, per Material dark-mode guidance.

```
--paper            #141A1D
--paper-white      #1D262B
--paper-canary     #2A2620   dim amber-grey, keeps the copy metaphor
--ink              #EEF1EE
--ink-soft         #A3B0B6
--ink-faint        #6A777E
--rule             #2C3840
--rule-strong      #445059
--stamp            #B79BE0   lightened, desaturated — 7.1:1 on --paper
--stamp-wash       #2A2338
--destructive      #E1806F
--on-ink           #141A1D
```

Night mode is **not** the demo default. Demo in Day. It looks more like paper and photographs better under stage lighting.

### 3.3 Typography

Four families, three voices. Every one is on Google Fonts, so Claude Design and HTML both work with no local install.

| Voice | Tamil | Latin | Used for |
|---|---|---|---|
| **Speech** | Noto Sans Tamil | IBM Plex Sans | UI, spoken claims, buttons, everything by default |
| **Document** | Noto Serif Tamil | IBM Plex Serif | Clause text, quoted document language, grievance packet |
| **Record** | — | IBM Plex Mono | Rates, timestamps, clause references, ledger values |

Why not Inter: it is the default reach and carries no meaning here. IBM Plex has a typewriter lineage and a matching mono and serif, which gives you three voices from one design intent.

Why the serif split matters: **spoken claims are set in sans, written clauses in serif.** The material metaphor runs all the way into the type. On the Delta Card you can tell which half is which without reading a label. That is the whole product, encoded typographically.

Mono is restricted to values that are genuinely tabular — `8.00%`, `00:04:12`, `p.3 §4.2`. It is not a decorative label font. Never set a heading or a nav label in mono.

**Scale (sp):**

```
display-xl   52 / 1.15   700   alert value, Tamil, single line only
display      40 / 1.25   700   Delta Card statements
headline     30 / 1.30   600   screen titles in Record Mode
title        24 / 1.35   600   section headers, session summary counts
body-lg      20 / 1.60   400   readable claim text in Record Mode
body         17 / 1.60   400   default body
body-tamil   17 / 1.75   400   Tamil body — extra leading, see 3.4
label        15 / 1.40   500   field labels, buttons
caption      13 / 1.40   400   metadata, timestamps
micro        12 / 1.35   500   status chips only
```

Minimum body size is 17sp, not 16. The user segment skews older, lower-literacy, and reading under stress.

### 3.4 Tamil typesetting rules — non-negotiable

Get these wrong and the app looks broken to the only people who matter.

1. **Line-height 1.7–1.8 for Tamil body, 1.25 minimum for Tamil display.** Tamil has tall superscript marks and deep descenders. At 1.5 the lines collide. Test with `ஆ`, `ொ`, `ூ`, `ஞ` stacked.
2. **Never apply letter-spacing to Tamil.** It breaks ligature rendering and looks illiterate.
3. **Never uppercase.** Tamil has no case. This also kills the ALL-CAPS eyebrow label habit across the whole app — good.
4. **Wrap, never ellipsize.** Tamil words are long. A truncated Tamil claim is a useless claim. Alert cards grow vertically; they never cut.
5. **Bilingual pairing is fixed:** Tamil first at full size, English gloss underneath at `caption` in `--ink-soft`. Never side by side. Never English first.
6. **Numerals stay Western Arabic** (8%, not ௮%). Everyone reads them, including the agent, and the document uses them.
7. **Reserve 30% vertical overflow** in every text box against the English mock. Tamil renders taller.

### 3.5 Space, radius, elevation

```
space   4  8  12  16  20  24  32  40  56  72
gutter  20dp both edges
safe    24dp top (status)  ·  48dp bottom (gesture bar)

radius  field      2dp     form fields are square
        card       4dp
        button     6dp
        sheet     16dp top corners only
        chip      999dp    status chips ONLY — the one pill in the app
        Delta Card 4dp     it is a document, not a bubble

elevation  0   everything by default — separation is by rule + tint
           1   0 2 8 rgba(23,36,46,.06)   raised card in Record Mode
           2   0 8 24 rgba(23,36,46,.12)  bottom sheet, Delta Card
           3   0 16 40 rgba(23,36,46,.18) full-screen alert takeover
```

The near-square radius is deliberate and load-bearing. Uniform 12–16dp rounding on everything is the SaaS-card default and reads as generic. Paper has corners.

**Rules over shadows.** A 1dp `--rule` hairline is the primary separator throughout. Shadow is reserved for things that genuinely float above the page.

### 3.6 Iconography

- **Lucide**, 1.75px stroke, 24dp default (`icon-sm` 20, `icon-md` 24, `icon-lg` 32).
- Outline only. No filled variants anywhere — mixing them at the same level is the fastest way to look unfinished.
- Icons are `--ink-soft` at rest, `--ink` when active. Only two icons in the whole app are ever `--stamp`: the delta seam mark and the delta count badge.
- **No emoji anywhere**, including in Tamil strings.
- Every icon-only control gets a `contentDescription` and a 48dp hit area regardless of visual size.

Icons in use: `mic`, `camera`, `file-text`, `scan-line`, `plane` (offline), `chevron-right`, `x`, `share-2`, `settings`, `clock`, `trash-2`, `eye-off` (discreet mode), `check` (used only for "document read", never for a verdict).

---

## 4. Navigation architecture

Two top-level destinations is too few for a bottom nav bar, and bottom nav in Session Mode would be actively harmful. So:

```
Home (Records)
 ├── New Session ────▶ Session Setup ──▶ [SESSION MODE — no chrome at all]
 │                                          ├── Document Capture (full screen)
 │                                          ├── Delta Card (overlay)
 │                                          └── Ledger Sheet (swipe up)
 │                                                    │
 │                                                 End Session
 │                                                    ▼
 ├── Session Detail ◀───────────────────────── Session Summary
 │     ├── Claim Detail
 │     └── Export / Office Kit ──▶ Grievance Packet Preview
 └── Settings
       └── Privacy & Storage
```

Rules:
- **Session Mode has no navigation chrome.** No app bar, no back arrow, no tabs. The only exits are "End session" and the system back gesture, which raises a confirm sheet. Predictive back is supported everywhere else.
- Home uses a **top app bar**, left-aligned title, single overflow action. No bottom bar in the app at all.
- Back always restores scroll position and filter state.
- Deep links: `vaakku://session/{id}`, `vaakku://session/{id}/claim/{n}` — needed for the notification tap-through.

---

## 5. Frame list

26 frames. Dimensions 412 × 916 dp unless noted. Build in this order — 07, 09 and 12 are the ones the demo lives or dies on.

| # | Frame | Mode | Priority |
|---|---|---|---|
| 01 | Cold open / wordmark | — | P2 |
| 02 | Onboarding 1 — what this does | — | P2 |
| 03 | Onboarding 2 — what this does not do | — | P1 |
| 04 | Onboarding 3 — audio is discarded | — | P2 |
| 05 | Permission primer | — | P2 |
| 06 | Home — Records (populated) | Record | P1 |
| 06b | Home — empty state | Record | P2 |
| 07 | Session setup | Record | P1 |
| 08 | Session live — listening, no claims | Session | P1 |
| 09 | Session live — ledger building | Session | P1 |
| 10 | Document capture — viewfinder | Session | P1 |
| 11 | Document capture — reading | Session | P2 |
| 12 | **Delta Card — CONTRADICTED** | Session | **P0** |
| 13 | Delta Card — UNSUPPORTED | Session | P1 |
| 14 | Delta stack — multiple deltas | Session | P2 |
| 15 | Ledger sheet — two-column | Session | P1 |
| 16 | Discreet mode | Session | P2 |
| 17 | End session confirm | Session | P2 |
| 18 | Session summary | Record | P1 |
| 19 | Session detail — timeline | Record | P1 |
| 20 | Claim detail | Record | P1 |
| 21 | Export / Office Kit | Record | P1 |
| 22 | Grievance packet preview | Record | P2 |
| 23 | Settings | Record | P2 |
| 24 | Privacy & storage | Record | P2 |
| 25 | Error states (4-up) | both | P2 |
| 26 | Night theme — frames 09 + 12 | Session | P3 |

---

## 6. Component library

### 6.1 Delta Card — the signature component

Everything else in this design exists to stay out of this card's way.

```
┌────────────────────────────────────────────────────┐  ← --paper-canary
│  அவர் சொன்னது                          00:04:12    │  label 15sp ink-soft
│                                            (mono)   │
│                                                     │
│  உறுதியான 8% வருமானம்                              │  ← display 40sp
│                                                     │     Noto Sans Tamil 700
│  guaranteed 8% return                               │  ← caption, ink-soft
│                                                     │     IBM Plex Sans
│ ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  · │  ← PERFORATION, --stamp
│                                                     │     2dp dots / 6dp gaps
│  ஆவணத்தில்                            பக்கம் 3 §4.2 │  ← label + mono ref
│                                                     │
│  உறுதி இல்லை                                        │  ← display 40sp
│                                                     │     Noto SERIF Tamil 700
│  non-guaranteed, illustrative only                  │
│                                                     │  ← --paper-white
├─────────────────────────────────────────────────────┤
│         மறை                    விவரம்   ›           │  ← 56dp control row
└─────────────────────────────────────────────────────┘
```

Specification:

- Width `100% − 32dp`. Height intrinsic, minimum 420dp, no maximum — it grows for long Tamil and never truncates.
- **Top half `--paper-canary`, bottom half `--paper-white`.** The tint shift is the primary carrier of "spoken vs written". Works in greyscale, works for colourblind users, works at 1.5m.
- **The perforation is the only `--stamp` element on screen.** 2dp dots, 6dp gaps, full card width, inset 0. It reads as a tear line between two copies of the same form.
- Radius 4dp. Elevation 2. Sits on a `rgba(23,36,46,.45)` scrim over the live session screen.
- Enters from the bottom, 240ms, spring damping 0.8. Exits upward-fading in 150ms (~62% of enter, per Material).
- Persists until dismissed. **No auto-dismiss timer.** The user is being watched and may not be able to look immediately.
- Two controls, 56dp tall, split 50/50, divided by a 1dp rule. `மறை` (Hide) is text-only. `விவரம்` (Detail) carries a chevron.
- Both statements are selectable long-press → copy. Free, and it matters for someone building a complaint.

**Accessibility:** the card announces as a single node — *"Difference found. Spoken: guaranteed eight percent return. Document, page three, section four point two: non-guaranteed, illustrative only."* Not three separate nodes. The relationship is the content.

**Dynamic type at 200%:** display drops to 34sp, the English gloss hides, the card scrolls internally. Statements never truncate.

### 6.2 Claim Chip

The unit of the live ledger. Proves you are extracting, not transcribing.

```
┌──────────────────────┐
│ வருமானம்              │  micro 12sp, ink-soft
│ 8.00%                │  label 15sp, IBM Plex Mono 500, ink
└──────────────────────┘
   height 52dp · radius 4dp · 1dp --rule · --paper-white
```

- States: **new** (1dp `--rule-strong` border, fades to `--rule` over 800ms), **matched** (unchanged, ink), **delta** (2dp `--stamp` bottom border, value in `--stamp`), **unsupported** (dashed `--rule` border, value in `--ink-faint`).
- Enters by sliding in from the right, 200ms ease-out, no bounce.
- Minimum 48dp hit area; tapping opens Claim Detail.

### 6.3 Field Row

The recurring structural device. Used in every ledger, summary, and detail view. A claim *is* a form field, so it is drawn as one.

```
   பூட்டுக் காலம்                              ← label 13sp ink-soft
   1 year                                     ← value 20sp, mono if numeric
  ─────────────────────────────────────       ← 1dp --rule, full width
```

- Height 64dp, label baseline 12dp above value.
- The rule under the value is the field line. Present even when empty — an empty field with no value is exactly how `UNSUPPORTED` should look.
- Delta variant: rule becomes 2dp `--stamp`, and a second value stacks below in IBM Plex Serif.

### 6.4 Listening Indicator

```
        ╭─────────────╮
       (      ●        )      ← ink dot 10dp, breathing ring
        ╰─────────────╯          ring 72dp → 84dp, 2.4s cycle
```

- **No colour.** An ink dot with a slow ring. Not a jumpy waveform visualiser — that is decoration, and decoration on a status indicator is a lie about what the app knows.
- Ring amplitude is driven by **real input level**, so the user can verify it is actually hearing the room. Diagnostic, not ornamental.
- `prefers-reduced-motion`: the ring freezes and a static text label `கேட்டுக்கொண்டிருக்கிறது` appears below.

### 6.5 Status Strip

Fixed top of Session Mode, 44dp, below the status bar.

```
  ✈ இணைப்பு இல்லை        00:04:12        📄 14 விதிகள்
    micro, ink-soft        mono 15sp        micro, ink-soft
```

The airplane chip is the only pill-shaped element in the app (999dp radius, `--stamp-wash` fill, `--stamp` text). It earns that exception: it is the proof of the on-device claim, and in the demo the judge's eye needs to find it instantly.

### 6.6 Primary Action Bar

Bottom of Session Mode, fixed, 48dp above the gesture bar.

```
┌───────────────────────────────┬─────────────────┐
│   ▣  ஆவணத்தைப் படி            │   அமர்வை முடி    │
└───────────────────────────────┴─────────────────┘
    64dp tall · 62/38 split · 8dp gap
```

- Primary (Scan document): `--ink` fill, `--on-ink` text, radius 6dp.
- Secondary (End session): transparent, 1dp `--rule` border, `--ink` text.
- One primary CTA per screen, always. Press feedback: scale 0.97 + 8% opacity drop, 120ms.

### 6.7 Bottom Sheet

16dp top radius, elevation 2, 24dp drag handle (4dp × 36dp, `--rule-strong`). Scrim `rgba(23,36,46,.50)` with an 8dp backdrop blur — blur used to signal dismissible background, not as decoration. Swipe-down dismiss plus a visible close control. Used for: ledger, end-session confirm, export.

### 6.8 Other components

- **Session Card** (Home list): 96dp, `--paper-white`, 1dp rule, radius 4dp. Left: type icon in a 40dp square (2dp radius, `--stamp-wash`). Centre: counterparty label + date. Right: delta count in `--stamp`, 20sp mono, then chevron.
- **Delta Count Badge:** 24dp square, 2dp radius, `--stamp` fill, `--on-ink` numeral in mono. Square, not circular — this is a count on a form, not a notification bubble.
- **Segmented control** (session type): 44dp, 1dp `--rule` container, radius 6dp, selected segment `--ink` fill.
- **Toast:** 56dp, `--ink` fill, `--on-ink` text, bottom-anchored 24dp above the gesture bar, 4s. Never used for deltas — deltas get a card.

---

## 7. Screen specifications

### 01 · Cold open
Wordmark centred, `--ink` on `--paper`. Below it, one line at 17sp `--ink-soft`: *நீங்கள் கேட்டதின் நகல்* / "your copy of what you were told". No spinner. 900ms, then crossfade to Home. Single text layer for the wordmark — see the naming note.

### 02–04 · Onboarding
Three frames, one idea each. Full-bleed illustration area (280dp) + headline (30sp) + one paragraph (17sp, max 60 characters per line) + a page indicator built from three 6dp squares, not dots. Skip control top-right at 15sp.

**02 — what it does.** Illustration: a speech mark and a paper sheet, connected by a perforated line. *"நீங்கள் கேட்டது. ஆவணத்தில் உள்ளது. இரண்டையும் ஒப்பிடுகிறோம்."*

**03 — what it does not do.** This is the most important onboarding frame and it maps straight to §3. Three field rows, each with a struck-through label: *no verdict · no score · no accusation.* Then, on its own line: **"VAAKKU குற்றம் சாட்டுவதில்லை. மேற்கோள் காட்டுகிறது."** — VAAKKU never accuses, it quotes. Put this frame in the deck.

**04 — audio is discarded.** Diagram: microphone → an extraction node → a claim ledger, and a waveform icon crossed out at the boundary with a caption underneath. *"ஒலி சேமிக்கப்படுவதில்லை."*

### 05 · Permission primer
Purpose stated **before** the system dialog fires. Two field rows: microphone, camera — each with a one-line reason in plain Tamil, not policy language. Primary button `தொடரவும்`. A caption underneath: *"எதுவும் இணையத்திற்கு அனுப்பப்படுவதில்லை."*

### 06 · Home — Records
Top app bar 56dp, title `பதிவுகள்` at 24sp left-aligned, settings icon right. List of Session Cards, 12dp gaps. Bottom: a fixed 64dp `புதிய அமர்வு` button, full width minus gutters, `--ink` fill. List gets 88dp bottom inset so the last card never hides behind the button.

**06b · Empty state.** Centred, 40% down: a line drawing of a blank duplicate form, then *"இன்னும் பதிவுகள் இல்லை"* and one line of direction: *"ஒரு விற்பனை உரையாடலுக்கு முன் அமர்வைத் தொடங்குங்கள்."* The empty screen is an invitation, not an apology.

### 07 · Session setup
The last screen before things get serious. Four field rows:

1. **Session type** — segmented control: காப்பீடு / கடன் / வாடகை / மற்றவை (insurance / loan / rental / other). Drives which of the six claim types are armed.
2. **Language** — தமிழ் + English (fixed in v1, shown as a locked row so the scope is legible).
3. **Counterparty label** — optional free text, e.g. *"XYZ வங்கி, அடையாறு"*. Goes in the grievance packet header.
4. **Offline check** — a live status row. If the radio is on: `--stamp` text, *"விமானப் பயன்முறையை இயக்கவும்"*, tappable to open settings. When off: the airplane chip appears with a check.

Primary: `அமர்வைத் தொடங்கு`. **Disable it until the offline check passes.** This is a design decision with a pitch payoff — the app itself refuses to run online, so the airplane-mode claim in your demo is enforced by the product rather than asserted by you.

### 08 · Session live — listening, no claims
Deliberately near-empty.

```
┌────────────────────────────────────────┐
│ ✈ இணைப்பு இல்லை   00:00:14   📄 இல்லை  │  status strip
│                                        │
│                                        │
│                                        │
│              ╭───────╮                 │
│             (    ●    )                │  listening indicator, 38% height
│              ╰───────╯                 │
│                                        │
│         கேட்டுக்கொண்டிருக்கிறது          │  20sp ink-soft
│                                        │
│                                        │
│  ─────────────────────────────────────  │  1dp rule
│  உரிமைகோரல்கள் இங்கே தோன்றும்            │  13sp ink-faint, ledger rail placeholder
│  ─────────────────────────────────────  │
│                                        │
│ ┌──────────────────────┬─────────────┐ │
│ │ ▣ ஆவணத்தைப் படி       │ அமர்வை முடி  │ │
│ └──────────────────────┴─────────────┘ │
└────────────────────────────────────────┘
```

### 09 · Session live — ledger building
Same frame, ledger rail populated. Claim Chips scroll horizontally, newest entering from the right. The rail is 76dp tall with 8dp gaps, and it holds around 2.5 chips — enough to show that extraction is running, not enough to invite reading.

Show four chips in the frame: `வருமானம் 8.00%`, `உறுதி asserted`, `பூட்டு 1 yr`, `திரும்பப்பெறல் 1 yr`. Set the first two to the **delta** state (2dp `--stamp` bottom border) once the document has been read, so a single screenshot proves the mechanism.

A small text control under the rail, 15sp `--ink-soft`: `எல்லாம் பார் ⌃` — opens the Ledger Sheet.

### 10 · Document capture — viewfinder
Full-screen camera, `rgba(23,36,46,.55)` scrim outside the detected quad.

- Four L-brackets at 28dp, 2dp stroke, `--on-ink`, snapping to the detected page edges.
- The quad outline turns `--stamp` when the page is stable and in focus.
- Coaching line, bottom third above the shutter, 17sp `--on-ink`: *"ஆவணம் முழுவதும் தெரியட்டும்"* → *"நகர்த்தாதீர்கள்"* → auto-capture on 600ms stability.
- Glare detected: *"வெளிச்சம் பிரதிபலிக்கிறது — சற்று சாய்க்கவும்"*.
- Shutter 72dp, 3dp `--on-ink` ring, transparent centre. Page counter chips top-right: `1 2 +`.
- Multi-page: captured pages stack as 44 × 60dp thumbnails bottom-left, most recent on top.

### 11 · Document capture — reading
Captured page shown at 60% opacity. Clause regions light up progressively with a 1dp `--stamp` outline and a small mono label (`§4.2`) as each is parsed. Stagger 40ms per region — this is the one place a stagger is earned, because it shows real sequential work. Bottom: `14 விதிகள் படிக்கப்பட்டன` + `முடிந்தது`.

### 12 · Delta Card — CONTRADICTED — **P0**
The Delta Card (§6.1) over a dimmed frame 09. This is the hero frame. Build it twice — once for the deck at 2× scale, once in-app.

Content for the demo, straight from the brief:
- Spoken: `உறுதியான 8% வருமானம்` / "guaranteed 8% return", `00:04:12`
- Written: `உறுதி இல்லை` / "non-guaranteed, illustrative only", `பக்கம் 3 §4.2`

### 13 · Delta Card — UNSUPPORTED
Same shell, different bottom half. The document side renders as an **empty field** — the label, a full-width `--rule` line, and nothing on it. Below the line, 15sp `--ink-soft`: *"இந்த ஆவணத்தில் இது குறிப்பிடப்படவில்லை."*

The perforation stays `--stamp` but drops to 1dp dots. Absence gets a quieter seam than contradiction — the only hierarchy in the system, and it is expressed in stroke weight, not hue.

### 14 · Delta stack
Second delta while the first is still open. The first card slides back and up 12dp, scales to 0.96, drops to 40% opacity; the new card enters in front. A count appears top-left of the stack: Delta Count Badge showing `2`. Maximum two visible; older cards collapse into the badge. Swipe a card horizontally to move through the stack.

### 15 · Ledger sheet
The pitch-deck money shot. Full-height bottom sheet, two columns, claims aligned by type.

```
┌────────────────────────────────────────┐
│              ▬▬▬▬                       │  drag handle
│  உரிமைகோரல் பேரேடு              ✕      │  24sp
│  ──────────────────────────────────────│
│   சொன்னது          ┊        ஆவணம்      │  column heads, 13sp
│  ┌──────────────┐  ┊  ┌──────────────┐ │
│  │ வருமானம்      │··┊··│ வருமானம்      │ │  ← perforated connector,
│  │ 8.00%        │  ┊  │ 4% / 8% illus│ │     --stamp, when they differ
│  └──────────────┘  ┊  └──────────────┘ │
│  ┌──────────────┐  ┊  ┌──────────────┐ │
│  │ உறுதி         │··┊··│ உறுதி         │ │
│  │ asserted     │  ┊  │ none         │ │
│  └──────────────┘  ┊  └──────────────┘ │
│  ┌──────────────┐──┊──┌──────────────┐ │  ← plain --rule connector
│  │ பூட்டு        │  ┊  │ பூட்டு        │ │     when they match
│  │ 1 yr         │  ┊  │ 1 yr         │ │
│  └──────────────┘  ┊  └──────────────┘ │
└────────────────────────────────────────┘
```

Left column `--paper-canary`, right column `--paper-white`, spoken in sans, written in serif. The connector between each pair is either a plain hairline (match) or a violet perforation (delta). **One screenshot, entire product explained.** Put this next to the Delta Card in the deck.

### 16 · Discreet mode
Long-press the listening indicator for 600ms. The screen becomes a plain clock face: time at 52sp `--ink` on `--paper`, nothing else. Listening continues. Deltas render as a single 8dp `--stamp` square below the time — no text, no card.

Long-press anywhere to return. A 15sp `--ink-faint` line sits at the very bottom: `அழுத்திப் பிடிக்கவும்` — because per the interaction rules, a gesture-only feature needs a visible affordance.

This exists because the real constraint is social, not technical: sometimes the person across the table should not see the screen change. Judges respond to this one.

### 17 · End session confirm
Bottom sheet. Title `அமர்வை முடிக்கவா?`. Two field rows summarising what will be kept — claims, clause references, timestamps — and one showing what will not: **ஒலி** with a struck-through waveform icon. Buttons: `தொடர்` (secondary) / `முடி` (primary, `--ink`).

### 18 · Session summary
Counts, not a score. This screen is where the temptation to draw a gauge is strongest — do not.

```
   4                    2                    9
வேறுபாடுகள்      ஆவணத்தில் இல்லை        பொருந்துகிறது
   --stamp            --ink                --ink-soft
```

Three numbers at 52sp mono, labels at 13sp underneath, separated by vertical hairlines. `--stamp` on the delta count only. No percentage. No ratio. No "risk". No progress arc.

Below: the four delta rows as Field Rows, then two buttons — `விவரம்` and `ஏற்றுமதி`.

### 19 · Session detail — timeline
Vertical timeline, 2dp `--rule` spine 24dp from the left edge. Each event is a node: 8dp square, `--stamp` for deltas, `--rule-strong` otherwise. Events: session start, each claim extracted, document captured, each delta, session end. Timestamps in mono, left of the spine. Tap any node → Claim Detail.

Numbering is appropriate here and only here: this genuinely is a sequence.

### 20 · Claim detail
Full evidence for one claim. Stacked, generous, Record Mode density.

1. The claim type as a headline.
2. **Spoken** block on `--paper-canary`: the Tamil transcript excerpt with the extracted span underlined 2dp `--stamp`, timestamp in mono, and a caption *"ஒலி சேமிக்கப்படவில்லை — உரை மட்டும்."*
3. **Written** block on `--paper-white`: the cropped clause image at full width, radius 2dp, 1dp rule; below it the clause text in IBM Plex Serif / Noto Serif Tamil, with the citation in mono.
4. A field row: `ஒப்பீடு` → the verdict word alone in plain ink. Not a badge, not a pill, not a colour block.

### 21 · Export / Office Kit
The phone-to-laptop bridge, and 10% of your score.

Header: `அமர்வுத் தொகுப்பு`. Then a manifest drawn as a stack of Field Rows, each with size in mono:

```
  உரிமைகோரல்கள் (JSON)                      4.2 KB
  ─────────────────────────────────────────────
  விதி மேற்கோள்கள் (JSON)                   6.8 KB
  ─────────────────────────────────────────────
  ஆவணப் படங்கள் (3)                        1.4 MB
  ─────────────────────────────────────────────
  ~~ஒலி~~                            சேமிக்கப்படவில்லை
  ─────────────────────────────────────────────
```

The struck-through audio row is the single best piece of copy in the app. It is a privacy claim rendered as a manifest line. Keep it.

Transfer state: a horizontal determinate bar, 4dp, `--ink` track / `--stamp` fill — no spinner. Then a Field Row confirming the destination path on the laptop.

### 22 · Grievance packet preview
Paper mode, full commitment. A4-ratio page previews in a horizontal pager, 3dp page shadow, real margins. Page 1: header with counterparty label, date, session ID in mono; then a numbered table of contradictions with clause citations. Bottom bar: `PDF ஏற்றுமதி`.

Set this page entirely in IBM Plex Serif / Noto Serif Tamil. It is a document, not a screen.

### 23 · Settings
Grouped Field Rows, section headers at 15sp `--ink-soft`, hairline group separators.

- **மொழி** — Tamil + English (locked in v1, shown so the scope is honest)
- **எச்சரிக்கை அளவு** — Standard / Large / Extra large. This is a glance-distance control, not a vanity setting; state that in the helper text.
- **அதிர்வு** — haptics on/off
- **மறைவு பயன்முறை** — discreet mode as default, on/off
- **ஒலி சேமிப்பு** — a **locked** row reading `எப்போதும் இல்லை` with a lock icon and a helper line. Non-toggleable by design. A settings row you cannot change is a strong statement.
- **இயந்திரங்கள்** — model status: ASR engine, OCR engine, both with an on-device badge
- **தனியுரிமை** → frame 24
- **பற்றி** — version, build

### 24 · Privacy & storage
One diagram, three paragraphs, no legalese. The diagram from onboarding 04, larger. Then: what is stored, where it lives, what leaves the phone (nothing, unless you export deliberately), and how to delete. A `--destructive` text button at the bottom, spatially separated by 40dp from everything else per the destructive-action rule.

### 25 · Error states — one frame, four panels
Every one names what happened and what to do. No apologies, no mood.

- **Tamil ASR unavailable on this device** → *"இந்தச் சாதனத்தில் தமிழ் ஆஃப்லைன் ஒலியறிதல் இல்லை."* Action: `English-only-ல் தொடர்`. This is your live fallback path, designed rather than improvised at 2am.
- **OCR confidence low** → show the crop, action `மீண்டும் படி`.
- **No document scanned yet** → *"ஒப்பிட ஆவணம் தேவை."* Action: `ஆவணத்தைப் படி`. Until a document exists, the app can only observe claims, not compare them — say so plainly rather than showing a broken comparison.
- **Thermal / battery** → *"சாதனம் சூடாகிறது — ஒலியறிதல் மெதுவாகும்."* Session continues, degraded, and says so.

### 26 · Night theme
Rebuild frames 09 and 12 with the Night tokens. Check `--stamp` at #B79BE0 against `--paper` at #141A1D (7.1:1) and confirm the perforation is still visible — it thins out perceptually on dark and may need 2.5dp.

---

## 8. Motion

Everything under 300ms. One orchestrated moment in the whole app.

| Transition | Duration | Easing | Notes |
|---|---|---|---|
| Delta Card enter | 240ms | spring, damping 0.8 | translateY +48 → 0, opacity 0 → 1 |
| Delta Card exit | 150ms | ease-in | ~62% of enter |
| Claim Chip enter | 200ms | ease-out | translateX +24 → 0 |
| Sheet open | 280ms | ease-out | plus scrim fade 200ms |
| Sheet close | 180ms | ease-in | |
| Screen push | 240ms | ease-out | slide from right, predictive-back aware |
| Press feedback | 120ms | ease-out | scale 0.97, opacity −8% |
| Listening ring | 2400ms | sine, infinite | amplitude driven by real input level |
| Setup → Session | 400ms | ease-in-out | **the orchestrated moment**, see below |

**Setup → Session Mode** is the one place worth spending motion budget. The whole Record Mode chrome — app bar, buttons, background — fades and lifts away over 400ms while the listening indicator scales up from the centre. It should feel like the room going quiet. Everything else in the app is functional, sub-300ms, and forgettable.

**Rules:** transform and opacity only, never width or height. All animations interruptible — a tap cancels immediately. Input is never blocked during motion. `prefers-reduced-motion` collapses everything to a 100ms crossfade and freezes the listening ring.

---

## 9. Haptics and sound

**Sound: none. Ever.** No alert tone, no shutter click, no confirmation chime. Audio would announce the app to the person across the table. Suppress the camera shutter sound where the region allows it.

Haptics, all `VibrationEffect`:

| Event | Effect |
|---|---|
| Delta detected | Two short ticks, 40ms apart — deliberately not a long buzz, which is audible on a table |
| Document captured | Single `EFFECT_CLICK` |
| Session start / end | Single `EFFECT_HEAVY_CLICK` |
| Button press | `EFFECT_TICK` |

If the phone is face-down on a table, a long vibration is loud. Two ticks are not. This is the kind of detail that is invisible until it goes wrong in front of a jury.

---

## 10. Accessibility checklist

- Body text ≥ 17sp everywhere. Delta Card ≥ 34sp at any type setting.
- Contrast: body ≥ 4.5:1, Delta Card statements ≥ 7:1, `--stamp` on `--paper` verified at 6.4:1.
- **Nothing is communicated by colour alone.** Every state has a structural form too: perforation, dashed border, empty field line, tint shift.
- Touch targets ≥ 48 × 48dp with 8dp minimum spacing. Icons smaller than 48dp get expanded hit areas.
- Dynamic type to 200% without truncation. Tamil wraps, never ellipsizes.
- TalkBack: the Delta Card is one node with a composed announcement. Reading order matches visual order. Every icon-only control is labelled.
- Reduced motion respected across every transition.
- Discreet mode, a gesture feature, has a visible text affordance.
- Landscape: Session Mode locks portrait (it lives on a table); Record Mode reflows.

---

## 11. Copy deck

Tamil first, English gloss where the UI shows both. **Have a native speaker read every string before the event** — machine-plausible Tamil reads as machine-plausible Tamil to the exact audience you are building for, and it is the cheapest possible credibility loss.

| Key | Tamil | English |
|---|---|---|
| `app.tagline` | நீங்கள் கேட்டதின் நகல் | Your copy of what you were told |
| `session.listening` | கேட்டுக்கொண்டிருக்கிறது | Listening |
| `session.offline` | இணைப்பு இல்லை | Offline |
| `session.start` | அமர்வைத் தொடங்கு | Start session |
| `session.end` | அமர்வை முடி | End session |
| `session.scan` | ஆவணத்தைப் படி | Scan document |
| `session.no_doc` | ஆவணம் இல்லை | No document |
| `session.clauses` | {n} விதிகள் | {n} clauses |
| `delta.spoken` | அவர் சொன்னது | What was said |
| `delta.written` | ஆவணத்தில் | In the document |
| `delta.absent` | இந்த ஆவணத்தில் இது குறிப்பிடப்படவில்லை | Not mentioned in this document |
| `delta.hide` | மறை | Hide |
| `delta.detail` | விவரம் | Detail |
| `ledger.title` | உரிமைகோரல் பேரேடு | Claim ledger |
| `ledger.all` | எல்லாம் பார் | See all |
| `summary.deltas` | வேறுபாடுகள் | Differences |
| `summary.unsupported` | ஆவணத்தில் இல்லை | Not in document |
| `summary.consistent` | பொருந்துகிறது | Matches |
| `privacy.no_audio` | ஒலி சேமிக்கப்படுவதில்லை | Audio is never saved |
| `privacy.nothing_sent` | எதுவும் இணையத்திற்கு அனுப்பப்படுவதில்லை | Nothing is sent to the internet |
| `capture.frame` | ஆவணம் முழுவதும் தெரியட்டும் | Fit the whole document in frame |
| `capture.hold` | நகர்த்தாதீர்கள் | Hold still |
| `capture.glare` | வெளிச்சம் பிரதிபலிக்கிறது — சற்று சாய்க்கவும் | Glare — tilt slightly |
| `home.empty` | இன்னும் பதிவுகள் இல்லை | No records yet |
| `home.new` | புதிய அமர்வு | New session |
| `records.title` | பதிவுகள் | Records |
| `settings.title` | அமைப்புகள் | Settings |
| `export.bundle` | அமர்வுத் தொகுப்பு | Session bundle |
| `onboard.never_accuse` | VAAKKU குற்றம் சாட்டுவதில்லை. மேற்கோள் காட்டுகிறது. | VAAKKU never accuses. It quotes. |

**Voice rules.** Errors state what happened and what to do; they never apologise. Buttons name their outcome — `ஏற்றுமதி` produces a toast that says `ஏற்றுமதி ஆனது`, not `முடிந்தது`. Never call a claim "suspicious", "risky", "fake", or "fraud" anywhere in the UI, including in developer-facing strings — someone will screenshot the debug ledger.

---

## 12. What must never appear in this UI

This list is as load-bearing as the schema constraint it enforces. If any of these show up in a frame, the visual system has quietly turned VAAKKU into a fraud detector.

- Any score, rating, percentage-safe, gauge, dial, meter, or progress arc
- Green checkmarks or green anything on a claim — green is endorsement
- Red as a verdict colour (`--destructive` is for delete buttons only)
- A shield, lock, warning triangle, or siren icon
- Words: fraud, scam, suspicious, risky, fake, danger, alert-as-noun
- A chat interface, a message bubble, an assistant avatar, a typing indicator
- Charts, graphs, analytics, "sessions this month", streaks
- Confetti, celebration, or any success animation
- Faces, avatars, or any representation of the counterparty as a person
- A "safe / unsafe" summary state
- Full-screen colour washes on alert

---

## 13. Build order

For Claude Design, then HTML.

**Round 1 — prove the concept (build these three first).**
`12` Delta Card → `15` Ledger sheet → `09` Session live. If these three look right, the rest is execution. If they do not, nothing else will save it.

**Round 2 — the demo path.** `07` Setup → `08` Listening → `10` Capture → `18` Summary → `21` Export. This is the exact sequence you walk a judge through.

**Round 3 — the shell.** `06` Home, `19` Detail, `20` Claim detail, `23` Settings.

**Round 4 — credibility.** `03` What this is not, `24` Privacy, `25` Errors, `16` Discreet mode. These are the frames that answer questions before they are asked.

**Round 5 — polish.** `01`, `02`, `04`, `05`, `06b`, `11`, `13`, `14`, `17`, `22`, `26`.

### HTML build notes

```html
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+Tamil:wght@400;500;600;700&family=Noto+Serif+Tamil:wght@400;600;700&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Serif:wght@400;600&family=IBM+Plex+Mono:wght@400;500&display=swap" rel="stylesheet">
```

- Frame each screen in a `412 × 916` container with `overflow: hidden` and the safe-area insets drawn as guides, so what you see is what Compose will get.
- Put every token in `:root` as a CSS custom property using the exact names in §3. Night theme goes in `[data-theme="night"]`, same names. Zero raw hex in any component.
- The perforation is a `repeating-linear-gradient`, not a border-image — you need to control dot and gap independently.
- Set `font-feature-settings: 'tnum'` on every mono element so the timer does not jitter.
- Build the Delta Card as one component with a `variant` prop (`contradicted` / `unsupported`) rather than two components. It is one idea in two states.
- `touch-action: manipulation` on all controls.

### Compose handoff

Tokens go in `ui/theme/` as `VaakkuColors`, `VaakkuTypography`, `VaakkuSpacing`. Ship subset Noto Sans Tamil, Noto Serif Tamil, and IBM Plex Mono as bundled assets — the demo runs in airplane mode, so nothing can be fetched at runtime. Verify Tamil glyph rendering on the actual iQOO 15 before the event; OriginOS ships its own font stack and system fallback for Tamil is not something to discover on stage.

---

## 14. The three-frame test

Before you build anything else, check the design against this. Hand a judge three static frames with no explanation:

1. The Delta Card (12)
2. The two-column Ledger (15)
3. The "what this is not" onboarding frame (03)

If they cannot describe the product back to you from those three, the design has not done its job — and no amount of pitch will fix it on the day.
