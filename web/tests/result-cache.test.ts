import { test } from "node:test";
import assert from "node:assert/strict";
import { ResultCache } from "../lib/result-cache.ts";
import { MemoryDiscoveryIndex } from "../lib/discovery-index.ts";

test("cache expires at boundary, invalidates on clock rollback and respects LRU bounds", () => {
  let now = 100;
  const cache = new ResultCache(2, 10, () => now);
  const value = new MemoryDiscoveryIndex().search("");
  cache.put("a", value); cache.put("b", value);
  assert.ok(cache.get("a")); cache.put("c", value);
  assert.equal(cache.get("b"), undefined);
  now = 110; assert.equal(cache.get("a"), undefined);
  cache.put("d", value); now = 109; assert.equal(cache.get("d"), undefined);
});
test("result cache copies snapshots and can be explicitly invalidated", () => {
  const cache = new ResultCache();
  const value = new MemoryDiscoveryIndex().search("");
  cache.put("a", value);
  assert.notEqual(cache.get("a"), value);
  assert.notEqual(cache.get("a"), cache.get("a"));
  const copy = cache.get("a")!;
  Object.assign(copy, { query: "changed" });
  assert.equal(cache.get("a")?.query, "");
  assert.throws(() => cache.put("x".repeat(513), value), RangeError);
  cache.clear(); assert.equal(cache.get("a"), undefined);
});
