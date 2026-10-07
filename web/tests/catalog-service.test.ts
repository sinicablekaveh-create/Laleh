import { test } from "node:test";
import assert from "node:assert/strict";
import { CatalogService } from "../lib/catalog-service.ts";

const group = { schemaVersion: 2, groupId: "-55", title: "برق صنعتی تهران", username: "electric_group",
  category: "برق صنعتی", location: "تهران", revision: "1" };

test("local catalog searches approved metadata and replacement invalidates cached results", () => {
  const catalog = new CatalogService([group]);
  assert.equal(catalog.search("برق تهران").total, 1);
  assert.equal(catalog.search("برق تهران").hits[0]?.group.username, "electric_group");
  catalog.replace([]);
  assert.equal(catalog.search("برق تهران").total, 0);
  assert.equal(catalog.get("-55"), undefined);
});
test("local catalog does not expose records with private fields", () => {
  const catalog = new CatalogService([{ ...group, contacts: ["synthetic"] }]);
  assert.equal(catalog.search("").total, 0);
});
