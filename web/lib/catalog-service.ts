import { MemoryDiscoveryIndex, type SearchPage } from "./discovery-index.ts";
import { ResultCache } from "./result-cache.ts";
import { searchQuery } from "./query.ts";
import { searchOptions, type SearchOptions } from "./search-options.ts";
import { createHash } from "node:crypto";

/** Local approved catalog boundary. No Telegram client or network transport is used. */
export class CatalogService {
  private index: MemoryDiscoveryIndex;
  private readonly cache: ResultCache;
  constructor(rows: readonly unknown[] = [], clock: () => number = Date.now) {
    this.index = new MemoryDiscoveryIndex(rows);
    this.cache = new ResultCache(50, 30_000, clock);
  }
  replace(rows: readonly unknown[]): void {
    this.index = new MemoryDiscoveryIndex(rows); this.cache.clear();
  }
  search(raw: string, offset = 0, limit = 20, options: SearchOptions = {}): SearchPage {
    const query = searchQuery(raw);
    const start = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0;
    const count = Number.isFinite(limit) ? Math.max(1, Math.min(50, Math.floor(limit))) : 20;
    const filters = searchOptions(options);
    const key = createHash("sha256").update(JSON.stringify([query, start, count, filters])).digest("hex");
    const cached = this.cache.get(key);
    if (cached) return cached;
    const result = this.index.search(query, start, count, filters);
    this.cache.put(key, result);
    return result;
  }
  get(groupId: string) { return this.index.get(groupId); }
  categories() { return this.index.categories(); }
  locations() { return this.index.locations(); }
}
