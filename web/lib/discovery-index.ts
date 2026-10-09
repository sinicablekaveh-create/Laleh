import { parsePublicMetadata, type PublicGroup } from "./metadata.ts";
import { normalizeQuery, searchQuery, analyzeQuery } from "./query.ts";
import { searchOptions, type SearchOptions } from "./search-options.ts";

export type SearchHit = Readonly<{ group: PublicGroup; score: number }>;
export type SearchPage = Readonly<{ query: string; total: number; offset: number; limit: number; hits: readonly SearchHit[] }>;
export interface DiscoveryIndex {
  get(groupId: string): PublicGroup | undefined;
  search(query: string, offset?: number, limit?: number, options?: SearchOptions): SearchPage;
}

function words(text: string): string[] {
  return normalizeQuery(text).split(/[^\p{L}\p{N}_]+/u).filter(Boolean);
}
function phrase(text: string, query: string): boolean {
  return (` ${text} `).includes(` ${query} `);
}

/** Derived public-only index. Authoritative persistence/remote transport lives behind DiscoveryIndex. */
export class MemoryDiscoveryIndex implements DiscoveryIndex {
  static readonly capacity = 10_000;
  private readonly rows = new Map<string, PublicGroup>();
  private readonly usernames = new Map<string, string>();
  private readonly postings = new Map<string, Set<string>>();
  private vocabulary: string[] | undefined;
  private readonly titles = new Map<string, string>();
  private readonly rowWords = new Map<string, Set<string>>();

  constructor(rows: readonly unknown[] = []) {
    for (const row of rows.slice(0, MemoryDiscoveryIndex.capacity)) this.upsert(row);
  }

  upsert(value: unknown): boolean {
    const row = parsePublicMetadata(value);
    if (!row) return false;
    const previous = this.rows.get(row.groupId);
    const usernameOwner = this.usernames.get(row.username);
    if (usernameOwner && usernameOwner !== row.groupId) return false;
    if (previous && BigInt(previous.revision) >= BigInt(row.revision)) return false;
    if (!previous && this.rows.size >= MemoryDiscoveryIndex.capacity) return false;
    for (const word of this.rowWords.get(row.groupId) ?? []) {
      const ids = this.postings.get(word);
      ids?.delete(row.groupId);
      if (ids?.size === 0) this.postings.delete(word);
    }
    const terms = new Set(words(`${row.title} ${row.username} ${row.category} ${row.location}`));
    for (const word of terms) {
      const ids = this.postings.get(word) ?? new Set<string>();
      ids.add(row.groupId);
      this.postings.set(word, ids);
    }
    this.vocabulary = undefined;
    this.titles.set(row.groupId, words(row.title).join(" "));
    this.rowWords.set(row.groupId, terms);
    if (previous && previous.username !== row.username) this.usernames.delete(previous.username);
    this.usernames.set(row.username, row.groupId);
    this.rows.set(row.groupId, row);
    return true;
  }

  private prefixIds(term: string): Set<string> {
    const vocabulary = this.vocabulary ??= [...this.postings.keys()].sort();
    let left = 0, right = vocabulary.length;
    while (left < right) {
      const middle = (left + right) >>> 1;
      if (vocabulary[middle]! < term) left = middle + 1; else right = middle;
    }
    const ids = new Set<string>();
    for (let i = left; i < vocabulary.length && vocabulary[i]!.startsWith(term); i++) {
      for (const id of this.postings.get(vocabulary[i]!)!) ids.add(id);
    }
    return ids;
  }

  get(groupId: string): PublicGroup | undefined { return this.rows.get(groupId); }

  categories(): readonly Readonly<{ name: string; count: number }>[] {
    const counts = new Map<string, number>();
    for (const row of this.rows.values()) if (row.category) counts.set(row.category, (counts.get(row.category) ?? 0) + 1);
    return [...counts].sort(([a], [b]) => a.localeCompare(b, "fa"))
      .map(([name, count]) => Object.freeze({ name, count }));
  }

  locations(): readonly string[] {
    return [...new Set([...this.rows.values()].map(row => row.location).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, "fa"));
  }

  search(raw: string, offset = 0, limit = 20, options: SearchOptions = {}): SearchPage {
    const filters = searchOptions(options);
    const query = searchQuery(raw);
    const terms = [...new Set(words(query))].slice(0, 16);
    const boundedOffset = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0;
    const boundedLimit = Number.isFinite(limit) ? Math.max(1, Math.min(50, Math.floor(limit))) : 20;
    const context = analyzeQuery(query);
    let candidates: Set<string> | undefined = query && terms.length === 0 ? new Set() : undefined;
    for (const term of terms) {
      const matching = this.prefixIds(term);
      candidates = candidates === undefined ? matching
        : new Set([...candidates].filter(id => matching.has(id)));
      if (candidates.size === 0) break;
    }
    const hits: SearchHit[] = [];
    const normalizedPhrase = terms.join(" ");
    for (const id of candidates ?? this.rows.keys()) {
      const group = this.rows.get(id)!;
      if (filters.category && normalizeQuery(group.category) !== filters.category) continue;
      if (filters.location && normalizeQuery(group.location) !== filters.location) continue;
      const title = this.titles.get(id)!;
      let score = 0;
      if (normalizedPhrase) {
        if (title === normalizedPhrase) score += 1000;
        if (phrase(title, normalizedPhrase)) score += 500;
        if (group.username === normalizedPhrase.replaceAll(" ", "_")) score += 250;
        if (group.username.includes(query)) score += 100;
        if (context.category && phrase(title, context.category)) score += 100;
        if (context.location && phrase(title, context.location)) score += 40;
        for (const term of terms) {
          if (phrase(title, term)) score += 20;
          if (group.username.includes(term)) score += 5;
        }
      }
      hits.push(Object.freeze({ group, score }));
    }
    hits.sort((a, b) => (filters.sort === "title"
      ? normalizeQuery(a.group.title).localeCompare(normalizeQuery(b.group.title), "fa") : b.score - a.score)
      || (BigInt(a.group.groupId) < BigInt(b.group.groupId) ? -1 : 1));
    return Object.freeze({ query, total: hits.length, offset: boundedOffset, limit: boundedLimit,
      hits: Object.freeze(hits.slice(boundedOffset, boundedOffset + boundedLimit)) });
  }
}
