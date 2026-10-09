import { test } from "node:test";
import assert from "node:assert/strict";
import { parsePublicMetadata } from "../lib/metadata.ts";

const row = { schemaVersion: 2, groupId: "-9223372036854775808", title: "گروه برق", username: "electric_group",
  category: "برق", location: "تهران", revision: "9223372036854775807" };

test("decimal string identities round-trip without JavaScript precision loss", () => {
  assert.deepEqual(parsePublicMetadata(JSON.parse(JSON.stringify(row))), row);
  assert.ok(Object.isFrozen(parsePublicMetadata(row)));
});
test("v1 safe integer rows migrate and unknown/private fields are rejected", () => {
  assert.equal(parsePublicMetadata({ ...row, schemaVersion: 1, groupId: -55, revision: 1 })?.groupId, "-55");
  assert.equal(parsePublicMetadata({ ...row, schemaVersion: 1, groupId: -9223372036854775808, revision: 1 }), null);
  assert.equal(parsePublicMetadata({ ...row, privateMessage: "synthetic" }), null);
  assert.equal(parsePublicMetadata({ ...row, schemaVersion: 99 }), null);
  assert.equal(parsePublicMetadata({ ...row, groupId: "-9223372036854775809" }), null);
  assert.equal(parsePublicMetadata({ ...row, username: "https://invalid.test" }), null);
  assert.equal(parsePublicMetadata({ ...row, title: "public\u202e" }), null);
});

test("public facets reject controls and bidi overrides before normalization", () => {
  for (const field of ["category", "location"]) {
    for (const value of ["public\u0000", "public\u202e", "public\u2066"]) {
      assert.equal(parsePublicMetadata({ ...row, [field]: value }), null);
    }
  }
});
