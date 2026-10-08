import { test } from "node:test";
import assert from "node:assert/strict";
import { CatalogService } from "../lib/catalog-service.ts";
import { searchState, searchLink, searchOptions } from "../lib/search-options.ts";

const row = { schemaVersion: 2, groupId: "-55", title: "برق صنعتی تهران", username: "electric_group",
  category: "برق صنعتی", location: "تهران", revision: "1" };
const rows = [row,
  { ...row, groupId: "-56", username: "other_group", category: "برق", location: "شیراز", title: "ابزار برق" },
  { ...row, groupId: "-57", username: "tehran_group", category: "برق", title: "برق تهران" }];

test("exact normalized filters precede pagination and never include other categories", () => {
  const catalog = new CatalogService(rows);
  const result = catalog.search("برق", 0, 1, { category: "برق", location: " تهران " });
  assert.equal(result.total, 1);
  assert.equal(result.hits[0]?.group.groupId, "-57");
  assert.equal(catalog.search("", 0, 20, { category: "برق صنعتی", location: "شيراز" }).total, 0);
});

test("filter and sort choices have separate cache entries and deterministic pages", () => {
  const catalog = new CatalogService(rows);
  assert.equal(catalog.search("", 0, 1).hits[0]?.group.groupId, "-57");
  assert.equal(catalog.search("", 0, 1, { sort: "title" }).hits[0]?.group.groupId, "-56");
  assert.equal(catalog.search("", 1, 1, { sort: "title" }).hits[0]?.group.groupId, "-57");
  assert.equal(catalog.search("", 0, 20, { location: "شيراز" }).total, 1);
  assert.equal(catalog.search("", 0, 20).total, 3);
  assert.deepEqual(catalog.locations(), ["تهران", "شیراز"]);
  catalog.replace([]);
  assert.deepEqual(catalog.locations(), []);
  assert.equal(catalog.search("", 0, 20, { sort: "title" }).total, 0);
});

test("equal titles use numeric ID tie breaks regardless of catalog order", () => {
  const a = { ...row, groupId: "-8", username: "tie_group" };
  const catalog = new CatalogService([a, row]);
  assert.deepEqual(catalog.search("", 0, 20, { sort: "title" }).hits.map(hit => hit.group.groupId), ["-55", "-8"]);
});

test("pagination links retain bounded filters and tolerate malformed URL parameters", () => {
  const state = searchState({ q: ["برق", "ignored"], category: "برق صنعتي", location: "تهران",
    sort: ["title", "relevance"], page: "2garbage" });
  assert.equal(state.page, 1);
  const url = new URL(searchLink(state, 2), "http://localhost");
  assert.deepEqual(Object.fromEntries(url.searchParams), {
    q: "برق", category: "برق صنعتی", location: "تهران", sort: "title", page: "2" });
  assert.equal(searchState({ page: ["999999", "1"] }).page, 500);
  assert.equal(searchState({ page: "-2" }).page, 1);
  assert.equal(searchOptions({ location: "x".repeat(500) }).location.length, 96);
  assert.equal(searchState({ sort: "unknown" }).sort, "relevance");
  assert.equal(new URL(searchLink(state), "http://localhost").searchParams.get("page"), "1");
});

test("long Unicode and escaped filters cannot exceed the response cache key bound", () => {
  const catalog = new CatalogService(rows);
  assert.equal(catalog.search("😀".repeat(96), 0, 20,
    { category: "😀".repeat(96), location: "\u0000".repeat(96) }).total, 0);
  assert.equal(catalog.search("برق", 0, 20).total, 3);
});
