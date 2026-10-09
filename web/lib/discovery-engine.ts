import type { PublicGroup } from "./metadata.ts";
import type { SearchPage } from "./discovery-index.ts";
import type { SearchOptions } from "./search-options.ts";
import type { QueryAnalysis } from "./query.ts";

/** Versioned extension; v1 API and schema-v2 metadata remain supported. */
export type DiscoveryResult = Readonly<{ engineVersion: 3; searchVersion: 6; page: SearchPage;
  analysis: QueryAnalysis; suggestions: readonly string[] }>;
export interface DiscoveryEngine {
  recommend(groupId: string, limit?: number): readonly PublicGroup[];
  discover(raw: string, offset?: number, limit?: number, options?: SearchOptions): DiscoveryResult;
}
