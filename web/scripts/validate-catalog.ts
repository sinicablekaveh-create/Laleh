import { readFileSync } from "node:fs";
import { parsePublicMetadata } from "../lib/metadata.ts";
import { MemoryDiscoveryIndex } from "../lib/discovery-index.ts";
const rows: unknown = JSON.parse(readFileSync(new URL("../data/public-groups.json", import.meta.url), "utf8"));
if (!Array.isArray(rows) || rows.length > MemoryDiscoveryIndex.capacity) throw new Error("Approved catalog exceeds array/capacity contract");
const ids = new Set<string>(), usernames = new Set<string>();
for (const [index, value] of rows.entries()) {
  const row = parsePublicMetadata(value);
  if (!row || ids.has(row.groupId) || usernames.has(row.username)) throw new Error(`Invalid or duplicate approved catalog row at index ${index}`);
  ids.add(row.groupId); usernames.add(row.username);
}
console.log(`Approved catalog validation passed: ${rows.length} public records. Source approval remains an operator responsibility.`);
