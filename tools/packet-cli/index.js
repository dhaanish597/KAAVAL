#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const { assertNoNumbers, FormatError } = require('./lib/canonical');
const { verifyChain, verifySignature, overallIntegrity } = require('./lib/verify');
const { readZip, ZipError } = require('./lib/unzip');
const { loadStrings } = require('./lib/strings');
const { packetModel, renderHtml } = require('./lib/render');
const { printToPdf } = require('./lib/pdf');

/**
 * VAAKKU packet CLI — build plan §7.4.
 *
 *   node tools/packet-cli/index.js --in <receipt folder or zip> [--out <packet.pdf>]
 *
 * Step 1 verifies the hash chain and the signature and prints
 * `INTEGRITY: PASSED` or `INTEGRITY: FAILED (<reason>)`. Step 2 renders the
 * packet. A failed chain does not stop the packet being rendered — the failure
 * is printed at the top of it instead, because somebody holding a receipt that
 * does not verify needs to read it more, not less.
 *
 * `--out` is optional. Without it the tool verifies and prints, which is what a
 * gate run needs and what the tamper test in §13 G7 checks.
 *
 * Exit codes: 0 verified · 2 the record did not verify · 3 the input could not
 * be read as a receipt. Distinct on purpose, so a script can tell "this record
 * was altered" from "this is not a receipt".
 */

const EXIT_FAILED = 2;
const EXIT_BAD_INPUT = 3;

const USAGE = `VAAKKU packet CLI (build plan §7.4)

  node tools/packet-cli/index.js --in <folder|zip> [--out <packet.pdf>]

  --in    a session folder, or a zip of one, containing receipt.json
  --out   where to write the packet; omit to verify without rendering

Exit codes: 0 verified, 2 the record did not verify, 3 the input is not a readable receipt.`;

function parseArgs(argv) {
  const args = { in: null, out: null };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--in' || arg === '--out') {
      const value = argv[i + 1];
      if (value === undefined || value.startsWith('--')) throw new FormatError(`${arg} needs a path after it`);
      args[arg === '--in' ? 'in' : 'out'] = value;
      i += 1;
    } else if (arg === '--help' || arg === '-h') {
      args.help = true;
    } else {
      throw new FormatError(`unrecognized argument ${arg}`);
    }
  }
  return args;
}

/**
 * Every file in the session, as `Map<name relative to the session, Buffer>`,
 * plus the receipt's name when `--in` named one file outright.
 */
function loadInput(inPath) {
  const stat = fs.statSync(inPath);
  if (stat.isDirectory()) return { files: readFolder(inPath), receiptHint: null };
  if (inPath.toLowerCase().endsWith('.zip')) return { files: readZip(fs.readFileSync(inPath)), receiptHint: null };
  // A single JSON file, whatever it is called. Handy at a keyboard, and the
  // reason the name is not required to be `receipt.json`: the G7 tamper test
  // points at an edited copy, and a copy that has been edited to prove a point
  // is usually named to say so. The crops are then absent, which the packet
  // already states plainly.
  const name = path.basename(inPath);
  return { files: new Map([[name, fs.readFileSync(inPath)]]), receiptHint: name };
}

function readFolder(root) {
  const files = new Map();
  const walk = (dir, prefix) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      const name = prefix ? `${prefix}/${entry.name}` : entry.name;
      if (entry.isDirectory()) walk(full, name);
      else if (entry.isFile()) files.set(name, fs.readFileSync(full));
    }
  };
  walk(root, '');
  return files;
}

/**
 * The receipt in a session, wherever it sits.
 *
 * §7.3 exports `Download/Vaakku/<sessionId>/receipt.json`, and a zip of that
 * folder may or may not keep the folder as a prefix depending on who made it.
 * Both shapes are accepted rather than making somebody repack their evidence.
 */
function findReceipt(files) {
  const named = [...files.keys()].filter((name) => name.toLowerCase().endsWith('receipt.json'));
  if (named.length === 0) {
    throw new FormatError(`no receipt.json here. Found: ${[...files.keys()].join(', ') || '(nothing)'}`);
  }
  // Shallowest wins, so a nested backup copy never shadows the real one.
  named.sort((a, b) => a.split('/').length - b.split('/').length || a.length - b.length);
  return named[0];
}

/** The repo root, for the app's fonts and its reviewed Tamil strings. */
function findRepoRoot() {
  if (process.env.VAAKKU_REPO) return process.env.VAAKKU_REPO;
  let dir = __dirname;
  for (;;) {
    if (fs.existsSync(path.join(dir, 'settings.gradle.kts'))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) {
      throw new Error('could not find the repository root; set VAAKKU_REPO to it');
    }
    dir = parent;
  }
}

function main(argv) {
  let args;
  try {
    args = parseArgs(argv);
  } catch (error) {
    console.error(`${error.message}\n\n${USAGE}`);
    return EXIT_BAD_INPUT;
  }
  if (args.help || args.in === null) {
    console.log(USAGE);
    return args.help ? 0 : EXIT_BAD_INPUT;
  }

  let files;
  let receiptName;
  let receipt;
  try {
    const loaded = loadInput(args.in);
    files = loaded.files;
    receiptName = loaded.receiptHint !== null ? loaded.receiptHint : findReceipt(files);
    receipt = JSON.parse(files.get(receiptName).toString('utf8'));
    assertNoNumbers(receipt);
  } catch (error) {
    if (error instanceof FormatError || error instanceof ZipError || error instanceof SyntaxError) {
      console.error(`Cannot read ${args.in} as a receipt: ${error.message}`);
    } else {
      console.error(`Cannot read ${args.in}: ${error.message}`);
    }
    return EXIT_BAD_INPUT;
  }

  // A receipt inside a folder refers to its crops relative to itself, so the
  // lookup table is rebased on wherever receipt.json turned out to be.
  const prefix = receiptName.includes('/') ? `${receiptName.slice(0, receiptName.lastIndexOf('/'))}/` : '';
  const sessionFiles = new Map();
  for (const [name, bytes] of files) {
    if (name.startsWith(prefix)) sessionFiles.set(name.slice(prefix.length), bytes);
  }

  let chain;
  try {
    chain = verifyChain(receipt);
  } catch (error) {
    console.error(`Cannot check ${args.in}: ${error.message}`);
    return EXIT_BAD_INPUT;
  }
  const signature = verifySignature(receipt);
  const integrity = overallIntegrity(chain, signature);

  console.log(integrity.passed ? 'INTEGRITY: PASSED' : `INTEGRITY: FAILED (${integrity.reason})`);
  console.log(
    signature.state === 'verified'
      ? `SIGNATURE: verified (StrongBox reported by the phone: ${signature.strongBox ? 'yes' : 'no'})`
      : signature.state === 'absent'
        ? 'SIGNATURE: none — this receipt is not signed'
        : `SIGNATURE: FAILED (${signature.reason})`,
  );
  console.log(`Session:   ${receipt.sessionId}  ·  ${receipt.events.length} events  ·  head ${receipt.head}`);

  if (args.out !== null) {
    try {
      const strings = loadStrings(findRepoRoot());
      const model = packetModel(receipt, integrity, signature, strings, sessionFiles);
      const html = renderHtml(model, strings, findRepoRoot());
      fs.mkdirSync(path.dirname(path.resolve(args.out)), { recursive: true });
      const { browser, htmlPath } = printToPdf(html, path.resolve(args.out));
      console.log(`Packet:    ${path.resolve(args.out)}`);
      console.log(`           ${model.rows.length} row(s), rendered by ${browser}`);
      console.log(`           source HTML kept at ${htmlPath}`);
    } catch (error) {
      console.error(`The record was checked, but the packet could not be rendered: ${error.message}`);
      return integrity.passed ? EXIT_BAD_INPUT : EXIT_FAILED;
    }
  }

  return integrity.passed ? 0 : EXIT_FAILED;
}

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}

module.exports = { main, parseArgs, findReceipt };
