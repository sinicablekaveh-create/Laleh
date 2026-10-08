import { test } from "node:test";
import assert from "node:assert/strict";
import { CatalogService } from "../lib/catalog-service.ts";
import {
  categoriesContract,
  groupContract,
  jsonResponse,
  PUBLIC_METADATA_CACHE_CONTROL,
  searchContract,
} from "../lib/api-contracts.ts";
import { LIST_PREFETCH_BUDGET, shouldPrefetchListItem } from "../lib/navigation.ts";

const row = {
  schemaVersion: 2,
  groupId: "-100",
  title: "برق صنعتی تهران",
  username: "electric_tehran",
  category: "برق صنعتی",
  location: "تهران",
  revision: "1",
};

test("public metadata responses expose bounded cache policy and support conditional 304", async () => {
  const catalog = new CatalogService([row]);
  const request = new Request("https://example.test/api/categories");
  const first = await jsonResponse(categoriesContract(catalog), { request, publicCache: true });
  assert.equal(first.status, 200);
  assert.equal(first.headers.get("cache-control"), PUBLIC_METADATA_CACHE_CONTROL);
  const etag = first.headers.get("etag");
  assert.match(etag ?? "", /^"sha256-[0-9a-f]{64}"$/u);

  const conditional = new Request("https://example.test/api/categories", {
    headers: { "If-None-Match": `W/${etag}` },
  });
  const second = await jsonResponse(categoriesContract(catalog), { request: conditional, publicCache: true });
  assert.equal(second.status, 304);
  assert.equal(await second.text(), "");
  assert.equal(second.headers.get("etag"), etag);
});

test("search terms and error responses never enter a shared cache", async () => {
  const catalog = new CatalogService([row]);
  const search = await jsonResponse(searchContract(catalog, new URLSearchParams({ q: "تهران" })), {
    request: new Request("https://example.test/api/search?q=%D8%AA%D9%87%D8%B1%D8%A7%D9%86"),
  });
  assert.equal(search.headers.get("cache-control"), "no-store");
  assert.equal(search.headers.get("etag"), null);

  const missing = await jsonResponse(groupContract(catalog, "-999"), {
    request: new Request("https://example.test/api/groups/-999"),
    publicCache: true,
  });
  assert.equal(missing.status, 404);
  assert.equal(missing.headers.get("cache-control"), "no-store");
  assert.equal(missing.headers.get("etag"), null);
});

test("result-list route prefetch stays within a fixed fan-out budget", () => {
  assert.equal(LIST_PREFETCH_BUDGET, 4);
  assert.deepEqual(
    Array.from({ length: 8 }, (_, index) => shouldPrefetchListItem(index)),
    [true, true, true, true, false, false, false, false],
  );
  assert.equal(shouldPrefetchListItem(-1), false);
  assert.equal(shouldPrefetchListItem(1.5), false);
});
