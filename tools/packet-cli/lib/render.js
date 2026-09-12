'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * The packet's HTML — build plan §7.4 step 2 and its section list.
 *
 * ### Rendering goes through an allowlist, and the allowlist is checked
 *
 * A receipt records more than a packet may show. `confidence` and `reason` are
 * both in the JSON on purpose — §7.1 wants the account of what the app actually
 * did, and a chain covering only the flattering half of that would not be an
 * integrity guarantee — and both are banned from any surface a person reads
 * (§2.4, §5.1).
 *
 * The protection is not "remember not to print them". [packetModel] builds a
 * new object containing only the fields §7.4 lists, the template can only reach
 * that object, and [assertNothingInternal] then walks what was built and throws
 * if an internal name appears anywhere in it. Adding a field to the packet by
 * accident is therefore a crash on the laptop, not a line on somebody's paper.
 *
 * ### Which rows exist
 *
 * Only MATCHES, NOT_IN_DOCUMENT and DIFFERS (§2.3). PENDING and UNCERTAIN are
 * silent — CLAUDE.md #2 — and silent means absent, not greyed out and not
 * counted in a footnote. A topic nobody spoke about simply has no row.
 */

/** The three states a person ever sees. Everything else is silent. */
const VISIBLE_STATES = new Set(['MATCHES', 'NOT_IN_DOCUMENT', 'DIFFERS']);

/**
 * Field names that are recorded but must never reach a rendered surface.
 * [assertNothingInternal] refuses any of these anywhere in the packet model.
 */
const INTERNAL_ONLY = new Set([
  'confidence',
  'reason',
  'ambiguous',
  'mentionCount',
  'dismissed',
  'hedged',
  'negated',
  'conditional',
]);

/**
 * Where a person can take a packet. Institution names are stable facts of the
 * Indian insurance system; the addresses are not checked by this tool, which is
 * offline by design.
 *
 * UNVERIFIED — §7.4 says "the human verifies URLs". Recorded in STATUS.md as an
 * open issue until they have been. One place to fix, on purpose.
 */
const DESTINATIONS = [
  {
    en: "The insurer's grievance officer",
    ta: 'நிறுவனத்தின் புகார் அதிகாரி',
    note: 'named in the policy document',
  },
  {
    en: 'Bima Bharosa — the IRDAI grievance portal',
    ta: 'பீமா பரோசா — IRDAI புகார் தளம்',
    note: 'bimabharosa.irdai.gov.in',
  },
  {
    en: 'Insurance Ombudsman',
    ta: 'இன்சூரன்ஸ் ஒம்புட்ஸ்மேன்',
    note: 'cioins.co.in',
  },
];

/** The paper palette, from app/src/main/java/app/vaakku/ui/theme/Color.kt. */
const INK = '#111111';
const INK_SOFT = '#3A3A3A';
const INK_FAINT = '#6C6C6D';
const PAPER = '#F4F3F1';
const RULE = '#CFCBC3';
/** Violet stamp ink. Marks a DIFFERS row and nothing else (CLAUDE.md #9). */
const STAMP = '#5B3E8E';
const STAMP_WASH = '#EFE8F8';

/**
 * The only object the template can see.
 *
 * `integrity` is the combined result from `overallIntegrity` — the chain and
 * the signature folded into the one line §7.4 prints — and `signature` is kept
 * beside it for the detail lines.
 *
 * `files` maps a name inside the session folder to its bytes, for crops.
 */
function packetModel(receipt, integrity, signature, strings, files) {
  const model = {
    session: {
      sessionId: receipt.sessionId,
      appVersion: receipt.appVersion,
      deviceModel: receipt.deviceModel,
      startedAt: istStamp(receipt.startedAtMs),
      endedAt: istStamp(receipt.endedAtMs),
    },
    rows: receipt.entries.filter((entry) => VISIBLE_STATES.has(entry.state)).map((entry) => row(entry, strings, files)),
    integrity: {
      head: receipt.head,
      passed: integrity.passed,
      failureReason: integrity.reason,
      eventCount: receipt.events.length,
      // Rebuilt field by field rather than passed straight through, for two
      // reasons. `verifySignature` returns `reason`, which is also the name of
      // the ledger's internal ReasonCode — unrelated, but
      // [assertNothingInternal] rightly refuses that name anywhere in the
      // packet, and the guard stays absolute rather than growing a carve-out.
      // And the certificate's subject and expiry come back in that object too;
      // neither belongs on somebody's paper, so neither is copied here.
      signature: {
        state: signature.state,
        strongBox: signature.strongBox === true,
        failureReason: signature.reason === undefined ? null : signature.reason,
      },
    },
  };
  assertNothingInternal(model);
  return model;
}

function row(entry, strings, files) {
  const topic = strings.claim(entry.type);
  const state = strings.state(entry.state);
  return {
    state: entry.state,
    topicTa: topic.ta,
    topicEn: topic.en,
    stateTa: state.ta,
    stateEn: state.en,
    // §7.4: "spoken quote + timestamp". The quote is the span the recognizer
    // heard, printed as it was heard — not the normalized value phrase. What
    // was actually said is the evidence; the app's tidy wording of it is not.
    spoken: entry.spoken ? { quote: entry.spoken.provenance.span, at: clock(entry.spoken.tMs) } : null,
    written: (entry.written || []).map((observation) => ({
      clause: observation.provenance.lineText,
      page: observation.provenance.frameId,
      crop: cropDataUri(observation.provenance.cropFile, files),
      cropName: observation.provenance.cropFile || null,
    })),
  };
}

/**
 * A crop as a data URI, or null when the file is not in the folder.
 *
 * A missing crop is ordinary — `SessionEvidence` returns a null name when a
 * JPEG did not land, and a zip can arrive without its images. The packet says
 * the image is missing rather than showing a broken box, and the clause text
 * beside it carries the same information (CLAUDE.md #2).
 */
function cropDataUri(cropFile, files) {
  if (!cropFile || !files) return null;
  const bytes = files.get(cropFile) || files.get(cropFile.replace(/\\/g, '/'));
  if (!bytes) return null;
  const extension = path.extname(cropFile).toLowerCase();
  const mime = extension === '.png' ? 'image/png' : 'image/jpeg';
  return `data:${mime};base64,${bytes.toString('base64')}`;
}

/** Milliseconds from the start of the session as `m:ss`. */
function clock(tMs) {
  const total = Math.floor(Number(tMs) / 1000);
  if (!Number.isFinite(total) || total < 0) return null;
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const IST_OFFSET_MS = (5 * 60 + 30) * 60 * 1000;

/**
 * An epoch millisecond value as `13 Sep 2026, 04:12 IST`.
 *
 * The receipt carries `startedAt` as a UTC ISO string, and printing that would
 * be defensible and wrong for this reader. A session at ten past four in the
 * morning in Chennai prints as `2026-09-12T22:42:00Z` — the previous day. Next
 * to a file named `session_2026-09-13T04-12-00`, which is minted from local
 * time, that reads as a document that contradicts itself, and the person
 * holding it is the least equipped to work out which line to believe.
 *
 * So the packet states one time, in the zone the session happened in, and
 * labels it. The offset is added arithmetically rather than through `Intl`:
 * India has no daylight saving, the offset has not changed since 1945, and a
 * fixed number cannot depend on which ICU data a laptop happens to ship.
 */
function istStamp(epochMs) {
  const ms = Number(epochMs);
  if (!Number.isFinite(ms)) return null;
  const shifted = new Date(ms + IST_OFFSET_MS);
  const day = shifted.getUTCDate();
  const month = MONTHS[shifted.getUTCMonth()];
  const hours = String(shifted.getUTCHours()).padStart(2, '0');
  const minutes = String(shifted.getUTCMinutes()).padStart(2, '0');
  return `${day} ${month} ${shifted.getUTCFullYear()}, ${hours}:${minutes} IST`;
}

/**
 * Throws if any internal-only field name appears in the model.
 *
 * The allowlist above is what builds the packet; this is what proves the
 * allowlist was not quietly widened. Two separate mistakes would have to line
 * up for `confidence` to reach paper.
 */
function assertNothingInternal(value, where = 'packet') {
  if (value === null || typeof value !== 'object') return;
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNothingInternal(item, `${where}[${index}]`));
    return;
  }
  for (const key of Object.keys(value)) {
    if (INTERNAL_ONLY.has(key)) {
      throw new Error(`${where}.${key} is recorded in the receipt but must never be rendered — see render.js`);
    }
    assertNothingInternal(value[key], `${where}.${key}`);
  }
}

// ---------------------------------------------------------------------------
// HTML
// ---------------------------------------------------------------------------

/**
 * Fonts are embedded as data URIs rather than linked.
 *
 * Headless Chrome and Edge will not always read `file://` subresources from a
 * page in a temporary directory, and a packet that silently fell back to a
 * system font would render Tamil as boxes — or worse, render it badly enough to
 * look fine at a glance. Embedding makes the HTML self-contained: it can be
 * opened, mailed or archived on its own and still be the same document.
 */
const FONT_FACES = [
  { family: 'Noto Sans Tamil', weight: 400, file: 'notosanstamil_regular.ttf' },
  { family: 'Noto Sans Tamil', weight: 600, file: 'notosanstamil_semibold.ttf' },
  { family: 'IBM Plex Sans', weight: 400, file: 'ibmplexsans_regular.ttf' },
  { family: 'IBM Plex Sans', weight: 600, file: 'ibmplexsans_semibold.ttf' },
  { family: 'IBM Plex Mono', weight: 400, file: 'ibmplexmono_regular.ttf' },
];

function fontCss(repoRoot) {
  const dir = path.join(repoRoot, 'app', 'src', 'main', 'res', 'font');
  return FONT_FACES.map(({ family, weight, file }) => {
    const full = path.join(dir, file);
    let bytes;
    try {
      bytes = fs.readFileSync(full);
    } catch (error) {
      throw new Error(
        `could not read the font ${full} (${error.code}). The packet embeds the app's own fonts so Tamil renders ` +
          'the same on paper as on the phone; run this tool from inside the repository.',
      );
    }
    return `@font-face{font-family:'${family}';font-weight:${weight};font-style:normal;` +
      `src:url(data:font/ttf;base64,${bytes.toString('base64')}) format('truetype');}`;
  }).join('\n');
}

function escapeHtml(text) {
  if (text === null || text === undefined) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderHtml(model, strings, repoRoot) {
  const t = strings.packet;
  return `<!DOCTYPE html>
<html lang="ta">
<head>
<meta charset="utf-8">
<title>${escapeHtml(t.titleEn)} — ${escapeHtml(model.session.sessionId)}</title>
<style>
${fontCss(repoRoot)}
@page { size: A4; margin: 16mm 14mm; }
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: 'Noto Sans Tamil', 'IBM Plex Sans', sans-serif;
  color: ${INK};
  background: #FFFFFF;
  font-size: 11pt;
  line-height: 1.5;
}
.en { font-family: 'IBM Plex Sans', sans-serif; }
.mono { font-family: 'IBM Plex Mono', monospace; }

.cover { border-bottom: 2px solid ${INK}; padding-bottom: 10mm; margin-bottom: 8mm; }
.cover h1 { font-size: 22pt; font-weight: 600; margin: 0 0 2mm 0; }
.cover .sub { font-size: 12pt; color: ${INK_SOFT}; margin: 0 0 6mm 0; }
.cover .note { font-size: 11pt; color: ${INK}; background: ${PAPER}; border-left: 3px solid ${INK}; padding: 4mm 5mm; }
.cover .note .en { display: block; color: ${INK_SOFT}; font-size: 9.5pt; margin-top: 1.5mm; }

h2 { font-size: 13pt; font-weight: 600; margin: 8mm 0 3mm 0; padding-bottom: 1.5mm; border-bottom: 1px solid ${RULE}; }
h2 .en { font-weight: 400; color: ${INK_FAINT}; font-size: 9.5pt; margin-left: 2mm; }

.details { width: 100%; border-collapse: collapse; font-size: 10pt; }
.details td { padding: 1.5mm 0; vertical-align: top; }
.details td:first-child { color: ${INK_FAINT}; width: 42mm; }

.row { border: 1px solid ${RULE}; margin-bottom: 4mm; page-break-inside: avoid; }
.row.differs { border-color: ${STAMP}; border-left-width: 3px; }
.row-head { display: flex; justify-content: space-between; align-items: baseline;
  padding: 3mm 4mm; background: ${PAPER}; border-bottom: 1px solid ${RULE}; }
.row.differs .row-head { background: ${STAMP_WASH}; border-bottom-color: ${STAMP}; }
.row-topic { font-size: 12pt; font-weight: 600; }
.row-topic .en { font-weight: 400; color: ${INK_FAINT}; font-size: 9pt; margin-left: 2mm; }
.row-state { font-size: 11pt; font-weight: 600; }
.row.differs .row-state { color: ${STAMP}; }
.row-state .en { font-weight: 400; color: ${INK_FAINT}; font-size: 9pt; margin-left: 2mm; }

.side { padding: 3mm 4mm; }
.side + .side { border-top: 1px dashed ${RULE}; }
.side-label { font-size: 9pt; color: ${INK_FAINT}; margin-bottom: 1.5mm; }
.side-label .at { float: right; }
.quote { font-size: 11.5pt; }
.clause { font-size: 10.5pt; font-family: 'IBM Plex Sans', sans-serif; }
.crop { margin-top: 2.5mm; }
.crop img { max-width: 100%; border: 1px solid ${RULE}; display: block; }
.crop .missing { font-size: 9pt; color: ${INK_FAINT}; font-style: italic; }
.page { font-size: 9pt; color: ${INK_FAINT}; margin-top: 1.5mm; }

.integrity { border: 1px solid ${RULE}; padding: 4mm; page-break-inside: avoid; }
.integrity .line { font-size: 10pt; margin-bottom: 2mm; }
.integrity .line:last-child { margin-bottom: 0; }
.integrity .head { font-size: 9pt; word-break: break-all; color: ${INK_SOFT}; }
.integrity .status { font-weight: 600; font-size: 11pt; }
.integrity .caveat { font-size: 9pt; color: ${INK_FAINT}; margin-top: 3mm; line-height: 1.45; }

.where li { margin-bottom: 2mm; }
.where .note { font-size: 9pt; color: ${INK_FAINT}; }
.freelook { margin-top: 6mm; padding: 4mm; background: ${PAPER}; font-size: 10.5pt; }
.freelook .en { display: block; color: ${INK_SOFT}; font-size: 9pt; margin-top: 1.5mm; }
.footer { margin-top: 8mm; padding-top: 3mm; border-top: 1px solid ${RULE};
  font-size: 8.5pt; color: ${INK_FAINT}; }
</style>
</head>
<body>

<div class="cover">
  <h1>${escapeHtml(t.titleTa)}</h1>
  <p class="sub en">${escapeHtml(t.titleEn)}</p>
  <div class="note">
    ${escapeHtml(t.coverTa)}
    <span class="en">${escapeHtml(t.coverEn)}</span>
  </div>
</div>

${failureBanner(model, t)}

<h2>${escapeHtml(t.sessionTa)}<span class="en">${escapeHtml(t.sessionEn)}</span></h2>
<table class="details">
  <tr><td>Session</td><td class="mono">${escapeHtml(model.session.sessionId)}</td></tr>
  <tr><td>Started</td><td class="mono">${escapeHtml(model.session.startedAt)}</td></tr>
  <tr><td>Ended</td><td class="mono">${escapeHtml(model.session.endedAt)}</td></tr>
  <tr><td>Device</td><td>${escapeHtml(model.session.deviceModel)}</td></tr>
  <tr><td>App version</td><td class="mono">${escapeHtml(model.session.appVersion)}</td></tr>
</table>

<h2>${escapeHtml(t.comparedTa)}<span class="en">${escapeHtml(t.comparedEn)}</span></h2>
${
  model.rows.length === 0
    ? `<p>${escapeHtml(t.nothingTa)}<br><span class="en" style="color:${INK_FAINT};font-size:9.5pt;">${escapeHtml(t.nothingEn)}</span></p>`
    : model.rows.map((row) => renderRow(row, t)).join('\n')
}

<h2>${escapeHtml(t.integrityTa)}<span class="en">${escapeHtml(t.integrityEn)}</span></h2>
${renderIntegrity(model.integrity)}

<h2>${escapeHtml(t.whereTa)}<span class="en">${escapeHtml(t.whereEn)}</span></h2>
<ul class="where">
${DESTINATIONS.map(
  (place) => `  <li>${escapeHtml(place.ta)}<br><span class="en">${escapeHtml(place.en)}</span>` +
    `<span class="note"> — ${escapeHtml(place.note)}</span></li>`,
).join('\n')}
</ul>

<div class="freelook">
  ${escapeHtml(strings.freeLook.ta)}
  <span class="en">${escapeHtml(strings.freeLook.en)}</span>
</div>

<div class="footer">
  ${escapeHtml(t.coverEn)} · ${escapeHtml(model.session.sessionId)}
</div>

</body>
</html>
`;
}

/**
 * §7.4: "The packet is still rendered on FAILED, but with the failure printed
 * at the top."
 *
 * Withholding the document would be the wrong response to a record that did not
 * verify — whoever holds it still needs to read what it says. It is stated at
 * the top instead, in plain words, describing the file and nothing else.
 */
function failureBanner(model, t) {
  if (model.integrity.passed) return '';
  return `<div class="note" style="border-left:3px solid ${STAMP};background:${STAMP_WASH};padding:4mm 5mm;margin-bottom:6mm;">
  <strong>${escapeHtml(t.alteredTa)}</strong>
  <span class="en" style="display:block;color:${INK_SOFT};font-size:9.5pt;margin-top:1.5mm;">
    ${escapeHtml(t.alteredEn)} ${escapeHtml(model.integrity.failureReason)}.
    The pages below are printed as they were found in the file.
  </span>
</div>`;
}

function renderRow(row, t) {
  const differs = row.state === 'DIFFERS' ? ' differs' : '';
  const spoken = row.spoken
    ? `<div class="side">
    <div class="side-label">${escapeHtml(t.spokenTa)} <span class="en">${escapeHtml(t.spokenEn)}</span>` +
      `${row.spoken.at ? `<span class="at mono">${escapeHtml(row.spoken.at)}</span>` : ''}</div>
    <div class="quote">${escapeHtml(row.spoken.quote)}</div>
  </div>`
    : '';
  const written = row.written
    .map(
      (item) => `<div class="side">
    <div class="side-label">${escapeHtml(t.writtenTa)} <span class="en">${escapeHtml(t.writtenEn)}</span></div>
    <div class="clause">${escapeHtml(item.clause)}</div>
    <div class="crop">${
      item.crop
        ? `<img src="${item.crop}" alt="">`
        : item.cropName
          ? `<span class="missing">The image ${escapeHtml(item.cropName)} is not in this folder. The line above is the text that was read.</span>`
          : ''
    }</div>
    ${item.page ? `<div class="page mono">${escapeHtml(item.page)}</div>` : ''}
  </div>`,
    )
    .join('\n');

  return `<div class="row${differs}">
  <div class="row-head">
    <span class="row-topic">${escapeHtml(row.topicTa)}<span class="en">${escapeHtml(row.topicEn)}</span></span>
    <span class="row-state">${escapeHtml(row.stateTa)}<span class="en">${escapeHtml(row.stateEn)}</span></span>
  </div>
  ${spoken}
  ${written}
</div>`;
}

function renderIntegrity(integrity) {
  const status = integrity.passed
    ? `<div class="line status">INTEGRITY: PASSED</div>
       <div class="line">The ${integrity.eventCount} moments noted during this session hash to the value below,
       unchanged since the phone wrote them.</div>`
    : `<div class="line status">INTEGRITY: FAILED</div>
       <div class="line">${escapeHtml(integrity.failureReason)}.</div>`;

  const signature = integrity.signature;
  let signatureLine;
  if (signature.state === 'verified') {
    signatureLine =
      `<div class="line">Signature: checked, and it matches this record.</div>` +
      `<div class="line">Key held in secure hardware (StrongBox), as reported by the phone: ` +
      `${signature.strongBox ? 'yes' : 'no'}.</div>`;
  } else if (signature.state === 'absent') {
    signatureLine = '<div class="line">Signature: this record is not signed.</div>';
  } else {
    // When the signature *is* the failure, its reason is already the headline
    // above and repeating it verbatim two lines later reads as two separate
    // problems. When the chain failed as well, the headline is the chain's
    // reason and this one is still news, so it is printed.
    const alreadySaid = integrity.failureReason === signature.failureReason;
    signatureLine = alreadySaid
      ? '<div class="line">Signature: it does not match this record.</div>'
      : `<div class="line">Signature: it does not match this record — ${escapeHtml(signature.failureReason)}.</div>`;
  }

  return `<div class="integrity">
  ${status}
  ${signatureLine}
  <div class="line head mono">${escapeHtml(integrity.head)}</div>
  <div class="caveat">
    This section is about the file only: whether these pages are byte-for-byte what the phone wrote.
    It says nothing about whether anything printed here is true, and nothing about any person.
    ${
      signature.state === 'verified'
        ? 'The signature check confirms the key in the attached certificate signed this record; ' +
          'this tool is offline and does not check that certificate against a manufacturer root.'
        : ''
    }
  </div>
</div>`;
}

module.exports = { packetModel, renderHtml, assertNothingInternal, VISIBLE_STATES, INTERNAL_ONLY, DESTINATIONS };
