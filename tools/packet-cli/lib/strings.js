'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * The packet's words — build plan §7.4 sections, §8 copy.
 *
 * ### Shared text is read from the app, not copied into this file
 *
 * Claim labels, state labels and the free-look note all exist already in
 * `app/src/main/res/values/strings.xml`, reviewed line by line by a native
 * Tamil speaker (§8.1). Copying them here would create a second copy that is
 * correct today and drifts the first time one is reworded — and the packet is
 * the artefact somebody carries to a grievance officer, so it is the worst
 * place for last month's wording to survive.
 *
 * So this file parses that XML. The tool already reads the app's fonts from the
 * repo for the same reason, and both are absent-or-correct rather than
 * silently-wrong: if the file cannot be found, the tool stops and says so
 * instead of printing English where Tamil belongs.
 *
 * Only headings that exist nowhere else are defined here, marked TAMIL-REVIEW
 * in the same convention as strings.xml, because adding unused strings to the
 * app to hold packet text would put dead resources in the APK.
 */

/** Headings that exist only on paper. TAMIL-REVIEW — not yet reviewed by the human. */
const PACKET = {
  // §7.4: the cover line, fixed by the build plan.
  coverEn: 'A record of statements. Not a finding about any person.',
  coverTa: 'சொல்லப்பட்டவற்றின் பதிவு. யாரையும் பற்றிய முடிவு அல்ல.',

  titleEn: 'Session record',
  titleTa: 'உரையாடல் பதிவு',

  sessionEn: 'Session details',
  sessionTa: 'உரையாடல் விவரங்கள்',

  spokenEn: 'Said',
  spokenTa: 'பேச்சில்',

  writtenEn: 'Document',
  writtenTa: 'ஆவணத்தில்',

  topicEn: 'Topic',
  topicTa: 'தலைப்பு',

  stateEn: 'Compared',
  stateTa: 'ஒப்பீடு',

  comparedEn: 'What was said, and what the document says',
  comparedTa: 'பேச்சும் ஆவணமும்',

  // A session can end with nothing to show: every topic silent, or every one
  // PENDING or UNCERTAIN. The section then says so in one line rather than
  // leaving a heading over nothing, which reads as a tool that broke.
  nothingEn: 'No topic in this session was compared.',
  nothingTa: 'இந்த உரையாடலில் எந்தத் தலைப்பும் ஒப்பிடப்படவில்லை.',

  integrityEn: 'Integrity of this record',
  integrityTa: 'இந்தப் பதிவின் மாறாத்தன்மை',

  // Printed at the top of a packet that did not verify (§7.4). It says what is
  // wrong with the file and stops there: not who changed it, not why, not what
  // it means. The reason from the checker is appended after the colon.
  alteredTa: 'இந்தப் பதிவு எழுதப்பட்டபடி இல்லை.',
  alteredEn: 'This record does not match its own integrity check:',

  whereEn: 'Where you can take this',
  whereTa: 'இதை எங்கே கொண்டு செல்லலாம்',

  freeLookEn: 'You have a 30-day free-look period from the day you receive the policy document.',
};

const CLAIM_KEYS = {
  RETURN_RATE: 'claim_return_rate',
  GUARANTEE: 'claim_guarantee',
  LOCK_IN: 'claim_lock_in',
  LIQUIDITY: 'claim_liquidity',
  BUNDLING: 'claim_bundling',
  CHARGES: 'claim_charges',
};

const STATE_KEYS = {
  MATCHES: 'state_matches',
  NOT_IN_DOCUMENT: 'state_not_in_document',
  DIFFERS: 'state_differs',
};

/**
 * Loads the app's strings.
 *
 * Returns `{ claim(type), state(state), freeLook, packet }`, where `claim` and
 * `state` give `{ ta, en }`.
 */
function loadStrings(repoRoot) {
  const file = path.join(repoRoot, 'app', 'src', 'main', 'res', 'values', 'strings.xml');
  let xml;
  try {
    xml = fs.readFileSync(file, 'utf8');
  } catch (error) {
    throw new Error(
      `could not read ${file} (${error.code}). The packet's Tamil comes from the app's reviewed strings, ` +
        'so run this tool from inside the repository.',
    );
  }
  const table = parseStrings(xml);

  const pair = (key, what) => {
    const ta = table.get(key);
    const en = table.get(`${key}_en`);
    if (ta === undefined) throw new Error(`strings.xml has no <string name="${key}"> for ${what}`);
    return { ta, en: en === undefined ? null : en };
  };

  const claims = new Map();
  for (const [type, key] of Object.entries(CLAIM_KEYS)) claims.set(type, pair(key, type));
  const states = new Map();
  for (const [state, key] of Object.entries(STATE_KEYS)) states.set(state, pair(key, state));

  const freeLookTa = table.get('free_look_note');
  if (freeLookTa === undefined) throw new Error('strings.xml has no <string name="free_look_note">');

  return {
    claim: (type) => claims.get(type) || { ta: type, en: type },
    state: (state) => states.get(state) || { ta: state, en: state },
    freeLook: { ta: freeLookTa, en: PACKET.freeLookEn },
    packet: PACKET,
  };
}

/**
 * `<string name="x">text</string>` pairs from an Android strings file.
 *
 * Deliberately not a general XML parser. It reads one element shape from a file
 * this repo owns, and anything it does not understand — plurals, arrays,
 * attributes it was not looking for — it skips rather than guesses at. The
 * caller then fails on the specific name it wanted and says which one, which is
 * a better error than a parser's.
 */
function parseStrings(xml) {
  const table = new Map();
  const pattern = /<string\s+name="([^"]+)"\s*>([\s\S]*?)<\/string>/g;
  let match = pattern.exec(xml);
  while (match !== null) {
    table.set(match[1], decode(match[2]));
    match = pattern.exec(xml);
  }
  return table;
}

/** Android's backslash escapes and the five XML entities. */
function decode(text) {
  return text
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&')
    .trim();
}

module.exports = { loadStrings, PACKET };
