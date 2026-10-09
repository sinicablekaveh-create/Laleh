import type { DiscoveryEngine, DiscoveryResult } from "./discovery-engine.ts";
import { MemoryDiscoveryIndex, type SearchPage } from "./discovery-index.ts";
import { ResultCache } from "./result-cache.ts";
import { searchQuery, analyzeQuery } from "./query.ts";
import { searchOptions, type SearchOptions } from "./search-options.ts";
import { createHash } from "node:crypto";

/** Local approved catalog boundary. No Telegram client or network transport is used. */
export class CatalogService implements DiscoveryEngine {
  private index: MemoryDiscoveryIndex;
  private readonly cache: ResultCache;
  private readonly clock: () => number;
  private searches = 0;
  private cacheHits = 0;
  private latencyMillis = 0;
  private maximumLatencyMillis = 0;
  constructor(rows: readonly unknown[] = [], clock: () => number = Date.now) {
    this.clock = clock;
    this.index = new MemoryDiscoveryIndex(rows);
    this.cache = new ResultCache(50, 30_000, clock);
  }
  replace(rows: readonly unknown[]): void {
    this.index = new MemoryDiscoveryIndex(rows); this.cache.clear();
  }
  search(raw: string, offset = 0, limit = 20, options: SearchOptions = {}): SearchPage {
    const started = this.clock();
    this.searches++;
    const query = searchQuery(raw);
    const start = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0;
    const count = Number.isFinite(limit) ? Math.max(1, Math.min(50, Math.floor(limit))) : 20;
    const filters = searchOptions(options);
    const key = createHash("sha256").update(JSON.stringify([query, start, count, filters])).digest("hex");
    const cached = this.cache.get(key);
    if (cached) { this.cacheHits++; this.recordLatency(started); return cached; }
    const result = this.index.search(query, start, count, filters);
    this.cache.put(key, result);
    this.recordLatency(started);
    return result;
  }
  private recordLatency(started: number): void {
    const elapsed = Math.max(0, this.clock() - started);
    this.latencyMillis += elapsed;
    this.maximumLatencyMillis = Math.max(this.maximumLatencyMillis, elapsed);
  }
  metrics() {
    return Object.freeze({ searches: this.searches, cacheHits: this.cacheHits,
      latencyMillis: this.latencyMillis, maximumLatencyMillis: this.maximumLatencyMillis });
  }
  discover(raw: string, offset = 0, limit = 20, options: SearchOptions = {}): DiscoveryResult {
    const analysis = analyzeQuery(raw);
    const page = this.search(analysis.normalized, offset, limit, options);
    const suggestions: string[] = [];
    if (analysis.normalized && page.total === 0) {
      const words = analysis.normalized.split(" ");
      const candidates = new Set<string>();
      for (let count = words.length - 1; count > 0; count--) candidates.add(words.slice(0, count).join(" "));
      if (analysis.category) candidates.add(analysis.category);
      if (analysis.location) candidates.add(analysis.location);
      for (const candidate of candidates) {
        if (candidate !== analysis.normalized && this.search(candidate, 0, 1, options).total > 0) suggestions.push(candidate);
        if (suggestions.length === 6) break;
      }
    }
    return Object.freeze({ engineVersion: 3, searchVersion: 6, page, analysis,
      suggestions: Object.freeze(suggestions) });
  }
  recommend(groupId: string, limit = 6) {
    const group = this.index.get(groupId);
    if (!group) return Object.freeze([]);
    const count = Number.isFinite(limit) ? Math.max(1, Math.min(20, Math.floor(limit))) : 6;
    const filters = group.category ? { category: group.category }
      : group.location ? { location: group.location } : {};
    return Object.freeze(this.search("", 0, count + 1, filters).hits
      .map(hit => hit.group).filter(candidate => candidate.groupId !== groupId).slice(0, count));
  }
  get(groupId: string) { return this.index.get(groupId); }
  categories() { return this.index.categories(); }
  locations() { return this.index.locations(); }
}
