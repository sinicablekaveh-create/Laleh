import { performance } from "node:perf_hooks";
import { CatalogService } from "../lib/catalog-service.ts";
const rows = Array.from({ length: 10_000 }, (_, index) => ({ schemaVersion: 2,
  groupId: String(-index - 1), username: `synthetic_${index}`, title: `برق صنعتی شهر ${index}`,
  category: "برق صنعتی", location: `شهر ${index % 100}`, revision: "1" }));
const started = performance.now();
const catalog = new CatalogService(rows, performance.now.bind(performance));
const indexedMillis = performance.now() - started;
const samples: number[] = [];
for (let index = 0; index < 100; index++) {
  const before = performance.now();
  catalog.search(`برق ${index}`, 0, 20);
  samples.push(performance.now() - before);
}
for (let index = 0; index < 100; index++) catalog.search("برق 99", 0, 20);
samples.sort((a, b) => a - b);
console.log(JSON.stringify({ fixture: "synthetic-public-only", rows: rows.length, indexedMillis,
  searchP50Millis: samples[49], searchP95Millis: samples[94], ...catalog.metrics() }, null, 2));
