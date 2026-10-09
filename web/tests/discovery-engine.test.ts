import { test } from "node:test";
import assert from "node:assert/strict";
import { CatalogService } from "../lib/catalog-service.ts";
import { analyzeQuery } from "../lib/query.ts";
const row = { schemaVersion: 2, groupId: "-1", title: "برق صنعتی تهران", username: "electric_group",
  category: "برق صنعتی", location: "تهران", revision: "1" };

test("query analysis extracts deterministic intent, category and location", () => {
  const result = analyzeQuery("آموزش برق صنعتي تهران");
  assert.equal(result.intent, "learning");
  assert.equal(result.category, "برق صنعتی");
  assert.equal(result.location, "تهران");
});
test("recovery is explicit, catalog-backed and preserves selected filters", () => {
  const catalog = new CatalogService([row]);
  const result = catalog.discover("برق صنعتی تهران ناشناخته");
  assert.equal(result.page.total, 0);
  assert.ok(result.suggestions.includes("برق صنعتی تهران"));
  assert.equal(result.engineVersion, 3);
  assert.deepEqual(catalog.discover("برق صنعتی تهران ناشناخته", 0, 20, { location: "شیراز" }).suggestions, []);
  assert.deepEqual(new CatalogService().discover("برق صنعتی تهران").suggestions, []);
});
test("metrics contain only numeric aggregates and replacement invalidates cache", () => {
  const catalog = new CatalogService([row], () => 1000);
  catalog.search("برق"); catalog.search("برق");
  assert.equal(catalog.metrics().cacheHits, 1);
  assert.ok(Object.values(catalog.metrics()).every(value => typeof value === "number"));
  catalog.replace([]);
  assert.equal(catalog.search("برق").total, 0);
});

test("recommendations are bounded, public and deterministic without personal state", () => {
  const catalog = new CatalogService([row, { ...row, groupId: "-2", username: "second_group" }]);
  assert.deepEqual(catalog.recommend("-1").map(group => group.groupId), ["-2"]);
  assert.deepEqual(catalog.recommend("-999"), []);
  assert.ok(Object.isFrozen(catalog.recommend("-1")));
});
