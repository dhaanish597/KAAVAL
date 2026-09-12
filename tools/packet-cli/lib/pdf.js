'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

/**
 * HTML → PDF with the laptop's own browser — build plan §7.4 step 2.
 *
 * ### Why a browser and not a PDF library
 *
 * Tamil is a complex script: `ஸ்கேன்` is not its code points laid out left to
 * right. Vowel signs reorder around the consonant, and ligatures form. A
 * browser's text engine (HarfBuzz) does this correctly; most pure-JS PDF
 * libraries draw glyphs in code-point order, which produces a page that looks
 * like Tamil to somebody who cannot read Tamil and is wrong to everybody who
 * can. The build plan says so directly, and this is the reason.
 *
 * Edge is already on the Windows laptop, so there is nothing to install.
 */

/** Where Edge and Chrome live on Windows, plus the PATH names for elsewhere. */
const CANDIDATES = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  '/usr/bin/microsoft-edge',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
];

/**
 * The browser to print with, or null.
 *
 * `VAAKKU_BROWSER` wins, so a laptop with neither in the usual place can still
 * produce a packet without editing this file.
 */
function findBrowser() {
  const override = process.env.VAAKKU_BROWSER;
  if (override) {
    if (!fs.existsSync(override)) {
      throw new Error(`VAAKKU_BROWSER is set to ${override}, which does not exist`);
    }
    return override;
  }
  return CANDIDATES.find((candidate) => fs.existsSync(candidate)) || null;
}

/**
 * Prints [html] to [outPdf]. Returns the browser that did it.
 *
 * The HTML is kept beside the PDF rather than deleted. It is self-contained —
 * fonts and crops are embedded — so it is a readable, searchable copy of the
 * same document, and when a PDF comes out wrong it is the first thing worth
 * opening.
 */
function printToPdf(html, outPdf) {
  const browser = findBrowser();
  if (browser === null) {
    throw new Error(
      'no Edge or Chrome found. The packet is rendered by a real browser because Tamil needs proper text ' +
        'shaping. Set VAAKKU_BROWSER to a browser executable and run this again.',
    );
  }

  const htmlPath = `${outPdf.replace(/\.pdf$/i, '')}.html`;
  fs.writeFileSync(htmlPath, html, 'utf8');

  // A throwaway profile. Without it the run attaches to the everyday browser
  // profile, and if a window is already open the headless process exits at once
  // and writes nothing.
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'vaakku-packet-'));

  const args = [
    '--headless=new',
    '--disable-gpu',
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    // Offline by habit, not only by policy: nothing in the HTML is remote, and
    // this makes that true rather than merely intended.
    '--disable-extensions',
    '--disable-background-networking',
    '--no-pdf-header-footer',
    `--print-to-pdf=${outPdf}`,
    fileUrl(htmlPath),
  ];

  const result = spawnSync(browser, args, { timeout: 120_000, encoding: 'utf8' });
  try {
    fs.rmSync(profile, { recursive: true, force: true });
  } catch {
    // A leftover temp profile is not worth failing a packet over.
  }

  if (result.error) throw new Error(`could not run ${browser}: ${result.error.message}`);
  if (!fs.existsSync(outPdf)) {
    const detail = (result.stderr || result.stdout || '').trim().split('\n').slice(-5).join('\n');
    throw new Error(`${browser} exited with code ${result.status} and wrote no PDF.\n${detail}`);
  }
  return { browser, htmlPath };
}

function fileUrl(filePath) {
  const absolute = path.resolve(filePath).replace(/\\/g, '/');
  return `file:///${absolute.replace(/^\//, '')}`;
}

module.exports = { printToPdf, findBrowser };
