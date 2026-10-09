import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { analyzeQuery } from "../lib/query.ts";
const fixtures = JSON.parse(readFileSync(new URL("../../shared/query-contract-fixtures.json", import.meta.url), "utf8"));
test("Android and web use the same checked-in query contract fixtures", () => {
  for (const row of fixtures) {
    const result = analyzeQuery(row.raw);
    for (const key of ["normalized", "category", "location", "intent"] as const) assert.equal(result[key], row[key]);
  }
});
