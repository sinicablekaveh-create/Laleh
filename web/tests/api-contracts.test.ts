import { test } from "node:test";
import assert from "node:assert/strict";
import { CatalogService } from "../lib/catalog-service.ts";
import {
  categoriesContract,
  groupContract,
  healthContract,
  relatedContract,
  searchContract,
  type ContractResponse,
} from "../lib/api-contracts.ts";

const base = {
  schemaVersion: 2,
  groupId: "-100",
  title: "برق صنعتی تهران",
  username: "electric_tehran",
  category: "برق صنعتی",
  location: "تهران",
  revision: "1",
};
const rows = [
  base,
  { ...base, groupId: "-101", username: "electric_shiraz", title: "برق صنعتی شیراز", location: "شیراز" },
  { ...base, groupId: "-102", username: "tools_tehran", title: "ابزار برق تهران", category: "ابزار", location: "تهران" },
];

function dataOf<T>(response: ContractResponse<T>): T {
  if ("error" in response.body) throw new Error(`${response.body.error.code}: ${response.body.error.message}`);
  return response.body.data;
}

function errorOf<T>(response: ContractResponse<T>) {
  if ("data" in response.body) throw new Error("Expected error response");
  return response.body.error;
}

test("search contract normalizes filters, bounds paging and exposes only public catalog fields", () => {
  const catalog = new CatalogService(rows);
  const response = searchContract(catalog, new URLSearchParams({
    q: " برق ",
    category: "برق صنعتي",
    location: "تهران",
    sort: "title",
    offset: "0",
    limit: "999",
  }));
  assert.equal(response.status, 200);
  const data = dataOf(response);
  assert.equal(data.total, 1);
  assert.equal(data.limit, 50);
  assert.deepEqual(data.filters, { category: "برق صنعتی", location: "تهران", sort: "title" });
  assert.deepEqual(Object.keys(data.items[0]!.group).sort(),
    ["category", "groupId", "location", "revision", "schemaVersion", "title", "username"].sort());
});

test("group contract distinguishes malformed and unknown identifiers", () => {
  const catalog = new CatalogService(rows);
  const malformed = groupContract(catalog, "100");
  assert.equal(malformed.status, 400);
  assert.equal(errorOf(malformed).code, "INVALID_GROUP_ID");

  const missing = groupContract(catalog, "-999");
  assert.equal(missing.status, 404);
  assert.equal(errorOf(missing).code, "GROUP_NOT_FOUND");

  const found = groupContract(catalog, "-100");
  assert.equal(found.status, 200);
  assert.equal(dataOf(found).username, "electric_tehran");
});

test("related contract stays inside public catalog and excludes the source group", () => {
  const catalog = new CatalogService(rows);
  const response = relatedContract(catalog, "-100", "20");
  const data = dataOf(response);
  assert.equal(data.groupId, "-100");
  assert.deepEqual(data.items.map(group => group.groupId), ["-101"]);
  assert.ok(data.items.every(group => group.category === "برق صنعتی"));
});

test("categories contract returns derived public facets only", () => {
  const data = dataOf(categoriesContract(new CatalogService(rows)));
  assert.equal(data.categories.find(row => row.name === "برق صنعتی")?.count, 2);
  assert.ok(data.locations.includes("تهران"));
  assert.ok(data.locations.includes("شیراز"));
});

test("health/status contract is truthful when no backend or approved rows exist", () => {
  const populated = dataOf(healthContract(new CatalogService(rows)));
  assert.deepEqual(populated, { status: "ok", backend: "disabled", catalogEntries: 3 });

  const empty = dataOf(healthContract(new CatalogService([])));
  assert.deepEqual(empty, { status: "ok", backend: "disabled", catalogEntries: 0 });
});
