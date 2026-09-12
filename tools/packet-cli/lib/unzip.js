'use strict';

const zlib = require('node:zlib');

/**
 * Just enough of the ZIP format to read a receipt folder that was exported as a
 * zip — build plan §7.4's `--in <receipt folder or zip>`.
 *
 * ### Why this is here instead of `npm install adm-zip`
 *
 * §7.4 allows a zip dependency and §15's list of things that can go wrong says
 * the venue Wi-Fi may be down. A dependency that has to be fetched on the day is
 * a dependency that can fail on the day, and the failure would land while
 * somebody is waiting to be shown a packet. Node ships DEFLATE in `zlib`; the
 * container around it is two record layouts. So `npm install` is not part of
 * this tool at all.
 *
 * This reads exactly what §7.3 produces — a small zip of JSON and JPEG written
 * by `java.util.zip` — and refuses anything else rather than guessing: no
 * ZIP64, no encryption, no compression method other than stored and deflated.
 */

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;
const EOCD_MIN_SIZE = 22;
const ZIP64_SENTINEL = 0xffffffff;

const STORED = 0;
const DEFLATED = 8;

class ZipError extends Error {
  constructor(message) {
    super(message);
    this.name = 'ZipError';
  }
}

/**
 * Every file in `buffer`, as `Map<name, Buffer>`.
 *
 * Directory entries are skipped. Names keep their forward slashes, which is
 * what the zip format stores and what `cropFile` in a receipt looks like.
 */
function readZip(buffer) {
  const eocd = findEndOfCentralDirectory(buffer);
  const count = buffer.readUInt16LE(eocd + 10);
  const centralOffset = buffer.readUInt32LE(eocd + 16);

  if (centralOffset === ZIP64_SENTINEL || count === 0xffff) {
    throw new ZipError('this is a ZIP64 archive; this reader handles the small zips the app exports');
  }

  const files = new Map();
  let cursor = centralOffset;
  for (let i = 0; i < count; i += 1) {
    if (cursor + 46 > buffer.length || buffer.readUInt32LE(cursor) !== CENTRAL_SIGNATURE) {
      throw new ZipError(`the central directory ends early, at entry ${i + 1} of ${count}`);
    }
    const flags = buffer.readUInt16LE(cursor + 8);
    const method = buffer.readUInt16LE(cursor + 10);
    const crc = buffer.readUInt32LE(cursor + 16);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const uncompressedSize = buffer.readUInt32LE(cursor + 24);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const localOffset = buffer.readUInt32LE(cursor + 42);
    const name = buffer.toString('utf8', cursor + 46, cursor + 46 + nameLength);

    if ((flags & 0x1) !== 0) {
      throw new ZipError(`${name} is encrypted; this reader does not open encrypted entries`);
    }
    if (!name.endsWith('/')) {
      files.set(name, inflateEntry(buffer, { name, localOffset, method, compressedSize, uncompressedSize, crc }));
    }
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

function inflateEntry(buffer, entry) {
  if (entry.localOffset + 30 > buffer.length || buffer.readUInt32LE(entry.localOffset) !== LOCAL_SIGNATURE) {
    throw new ZipError(`${entry.name} does not start where the directory says it does`);
  }
  // The local header repeats the sizes, but they are zero when the entry was
  // written in streaming mode; the central directory is the copy that is always
  // filled in, so the lengths come from there and only the variable-length
  // fields are read here.
  const nameLength = buffer.readUInt16LE(entry.localOffset + 26);
  const extraLength = buffer.readUInt16LE(entry.localOffset + 28);
  const start = entry.localOffset + 30 + nameLength + extraLength;
  const end = start + entry.compressedSize;
  if (end > buffer.length) {
    throw new ZipError(`${entry.name} runs past the end of the archive`);
  }
  const raw = buffer.subarray(start, end);

  let content;
  if (entry.method === STORED) {
    content = Buffer.from(raw);
  } else if (entry.method === DEFLATED) {
    try {
      content = zlib.inflateRawSync(raw);
    } catch (error) {
      throw new ZipError(`${entry.name} could not be decompressed (${error.message})`);
    }
  } else {
    throw new ZipError(`${entry.name} uses compression method ${entry.method}; only stored and deflated are handled`);
  }

  if (content.length !== entry.uncompressedSize) {
    throw new ZipError(
      `${entry.name} is ${content.length} bytes but the directory says ${entry.uncompressedSize}`,
    );
  }
  // The CRC is in the file and costs nothing to check. A truncated copy — half
  // an email attachment, a bad USB transfer — would otherwise reach the chain
  // check and be reported as a receipt that does not verify, which reads as
  // something quite different from "this copy arrived damaged".
  const actualCrc = zlib.crc32(content) >>> 0;
  if (actualCrc !== entry.crc) {
    throw new ZipError(`${entry.name} is damaged: its checksum does not match (the copy may be incomplete)`);
  }
  return content;
}

function findEndOfCentralDirectory(buffer) {
  if (buffer.length < EOCD_MIN_SIZE) throw new ZipError('this file is too short to be a zip');
  // The record is last but carries a trailing comment of up to 64 KiB, so it is
  // found by scanning backwards for the signature.
  const earliest = Math.max(0, buffer.length - EOCD_MIN_SIZE - 0xffff);
  for (let at = buffer.length - EOCD_MIN_SIZE; at >= earliest; at -= 1) {
    if (buffer.readUInt32LE(at) === EOCD_SIGNATURE) return at;
  }
  throw new ZipError('this file does not end like a zip (no end-of-central-directory record)');
}

module.exports = { readZip, ZipError };
