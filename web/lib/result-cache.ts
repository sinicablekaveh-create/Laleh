import type { SearchPage } from "./discovery-index.ts";

/** Bounded LRU response cache; all ingress/egress is copied to prevent aliasing. */
export class ResultCache {
  private readonly entries = new Map<string, { writtenAt: number; value: SearchPage }>();
  private readonly capacity: number;
  private readonly ttlMillis: number;
  private readonly clock: () => number;
  constructor(capacity = 50, ttlMillis = 30_000, clock: () => number = Date.now) {
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 200
      || !Number.isFinite(ttlMillis) || ttlMillis < 1 || ttlMillis > 86_400_000) throw new Error("Invalid cache bounds");
    this.capacity = capacity; this.ttlMillis = ttlMillis; this.clock = clock;
  }
  get(key: string): SearchPage | undefined {
    this.prune();
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    this.entries.delete(key); this.entries.set(key, entry);
    return structuredClone(entry.value);
  }
  put(key: string, value: SearchPage): void {
    if (key.length > 512 || value.hits.length > 50) throw new RangeError("Cache entry exceeds bounds");
    this.prune();
    this.entries.delete(key);
    this.entries.set(key, { writtenAt: this.clock(), value: structuredClone(value) });
    while (this.entries.size > this.capacity) this.entries.delete(this.entries.keys().next().value!);
  }
  clear(): void { this.entries.clear(); }
  private prune(): void {
    const now = this.clock();
    for (const [key, entry] of this.entries) {
      if (now < entry.writtenAt || now - entry.writtenAt >= this.ttlMillis) this.entries.delete(key);
    }
  }
}
