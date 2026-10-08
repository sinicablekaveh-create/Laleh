import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeQuery, searchQuery } from "../lib/query.ts";

test("normalization matches Arabic/Persian city and category variants", () => {
  assert.equal(normalizeQuery("  تَأسیسات\u200cكابل   تهران "), "تاسیسات کابل تهران");
  assert.equal(normalizeQuery("INDUSTRIAL"), "industrial");
});

test("URL query handling bounds input and selects the first parameter", () => {
  assert.equal(searchQuery(undefined), "");
  assert.equal(searchQuery(["كابل", "ignore"]), "کابل");
  assert.equal([...searchQuery("a".repeat(200))].length, 96);
});
