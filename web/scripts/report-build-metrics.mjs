import { readFile, readdir, stat } from "node:fs/promises";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else files.push(path);
  }
  return files;
}

const root = ".next/static/chunks";
const files = (await walk(root)).filter(path => path.endsWith(".js"));
if (!files.length) throw new Error("No production JavaScript chunks found");

let rawBytes = 0;
let gzipBytes = 0;
let largestBytes = 0;
for (const path of files) {
  const metadata = await stat(path);
  const content = await readFile(path);
  rawBytes += metadata.size;
  gzipBytes += gzipSync(content, { level: 9 }).byteLength;
  largestBytes = Math.max(largestBytes, metadata.size);
}

console.log(
  `NEXT_BUILD_METRICS js_chunks=${files.length} raw_js_bytes=${rawBytes} gzip_js_bytes=${gzipBytes} largest_js_bytes=${largestBytes}`,
);
