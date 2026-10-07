import { parsePublicMetadata, type PublicGroup } from "./metadata.ts";
import { normalizeQuery, searchQuery } from "./query.ts";

export type SearchHit = Readonly<{ group: PublicGroup; score: number }>;
export type SearchPage = Readonly<{ query: string; total: number; offset: number; limit: number; hits: readonly SearchHit[] }>;
export interface DiscoveryIndex {
  get(groupId: string): PublicGroup | undefined;
  search(query: string, offset?: number, limit?: number): SearchPage;
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
    this.rowWords.set(row.groupId, terms);
    if (previous && previous.username !== row.username) this.usernames.delete(previous.username);
    this.usernames.set(row.username, row.groupId);
    this.rows.set(row.groupId, row);
    return true;
  }

  get(groupId: string): PublicGroup | undefined { return this.rows.get(groupId); }

  search(raw: string, offset = 0, limit = 20): SearchPage {
    const query = searchQuery(raw);
    const terms = [...new Set(words(query))].slice(0, 16);
    const boundedOffset = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0;
    const boundedLimit = Number.isFinite(limit) ? Math.max(1, Math.min(50, Math.floor(limit))) : 20;
    let candidates: Set<string> | undefined;
    for (const term of terms) {
      const matching = new Set<string>();
      for (const [word, ids] of this.postings) {
        if (word.startsWith(term)) for (const id of ids) matching.add(id);
      }
      candidates = candidates === undefined ? matching
        : new Set([...candidates].filter(id => matching.has(id)));
      if (candidates.size === 0) break;
    }
    const hits: SearchHit[] = [];
    const normalizedPhrase = terms.join(" ");
    for (const id of candidates ?? this.rows.keys()) {
      const group = this.rows.get(id)!;
      const title = words(group.title).join(" ");
      let score = 0;
      if (normalizedPhrase) {
        if (title === normalizedPhrase) score += 1000;
        if (phrase(title, normalizedPhrase)) score += 500;
        if (group.username.includes(query)) score += 100;
        if (group.category && phrase(query, group.category)) score += 100;
        if (group.location && phrase(query, group.location)) score += 40;
        for (const term of terms) if (phrase(title, term)) score += 20;
      }
      hits.push(Object.freeze({ group, score }));
    }
    hits.sort((a, b) => b.score - a.score || (BigInt(a.group.groupId) < BigInt(b.group.groupId) ? -1 : 1));
    return Object.freeze({ query, total: hits.length, offset: boundedOffset, limit: boundedLimit,
      hits: Object.freeze(hits.slice(boundedOffset, boundedOffset + boundedLimit)) });
  }
}
