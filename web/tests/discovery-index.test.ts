import { test } from "node:test";
import assert from "node:assert/strict";
import { MemoryDiscoveryIndex } from "../lib/discovery-index.ts";

function row(id: string, title: string, revision = "1") {
  return { schemaVersion: 2, groupId: id, title, username: `group_${id.slice(1)}`,
    category: "برق صنعتی", location: "تهران", revision };
}

test("index deduplicates revisions, drops old postings and supports prefix stages", () => {
  const index = new MemoryDiscoveryIndex([row("-1", "کابل ساختمان")]);
  assert.equal(index.search("كابل ساخ").total, 1);
  assert.equal(index.upsert(row("-1", "کابل قدیمی", "1")), false);
  assert.equal(index.upsert(row("-1", "انرژی خورشیدی", "2")), true);
  assert.equal(index.search("کابل").total, 0);
  assert.equal(index.search("خورشیدی").total, 1);
});
test("exact title beats broader titles and ties do not depend on insertion order", () => {
  const entries = [row("-1", "گروه برق صنعتی تهران"), row("-2", "برق صنعتی تهران"), row("-3", "برق صنعتی تهران")];
  const left = new MemoryDiscoveryIndex(entries).search("برق صنعتی تهران");
  const right = new MemoryDiscoveryIndex([...entries].reverse()).search("برق صنعتی تهران");
  assert.deepEqual(left.hits.map(hit => hit.group.groupId), ["-3", "-2", "-1"]);
  assert.deepEqual(left, right);
});
test("pages and immutable output are bounded and malformed private rows are rejected", () => {
  const index = new MemoryDiscoveryIndex(Array.from({ length: 70 }, (_, i) => row(String(-i - 1), "برق")));
  const page = index.search("", 5, 1000);
  assert.equal(page.hits.length, 50);
  assert.equal(page.total, 70);
  assert.ok(Object.isFrozen(page.hits));
  assert.equal(index.upsert({ ...row("-100", "public"), session: "synthetic" }), false);
});

test("duplicate usernames cannot identify distinct groups and revisions release former aliases", () => {
  const index = new MemoryDiscoveryIndex([row("-1", "برق")]);
  assert.equal(index.upsert({ ...row("-2", "public"), username: "group_1" }), false);
  assert.equal(index.upsert({ ...row("-1", "برق", "2"), username: "renamed_group" }), true);
  assert.equal(index.upsert({ ...row("-2", "public"), username: "group_1" }), true);
});

test("punctuation-only queries never expand into unfiltered browse", () => {
  const index = new MemoryDiscoveryIndex([row("-1", "برق")]);
  assert.equal(index.search("!!!").total, 0);
  assert.equal(index.search("").total, 1);
});

test("sorted vocabulary stays current across new and removed prefixes", () => {
  const index = new MemoryDiscoveryIndex([row("-1", "کابل")]);
  assert.equal(index.search("کاب").total, 1);
  index.upsert(row("-1", "خورشیدی", "2"));
  assert.equal(index.search("کاب").total, 0);
  assert.equal(index.search("خور").total, 1);
});
