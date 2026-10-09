import { parsePublicMetadata, type PublicGroup } from "./metadata.ts";
import { searchQuery } from "./query.ts";

export const PREFERENCES_KEY = "laleh.discovery.preferences.v1";
export type Preferences = Readonly<{ version: 1; rememberHistory: boolean;
  theme: "system" | "light" | "dark"; category: string;
  history: readonly string[]; favorites: readonly PublicGroup[] }>;
export function emptyPreferences(): Preferences {
  return { version: 1, rememberHistory: false, theme: "system", category: "", history: [], favorites: [] };
}
/** Local browser preferences only. Never sent to catalog/sync or used as account identity. */
export function parsePreferences(raw: string | null): Preferences {
  if (!raw || raw.length > 128_000) return emptyPreferences();
  try {
    const row = JSON.parse(raw);
    if (!row || row.version !== 1 || typeof row.rememberHistory !== "boolean"
      || !["system", "light", "dark"].includes(row.theme)
      || typeof row.category !== "string" || !Array.isArray(row.history) || !Array.isArray(row.favorites)) return emptyPreferences();
    const favorites = new Map<string, PublicGroup>();
    for (const value of row.favorites.slice(0, 100)) {
      const group = parsePublicMetadata(value);
      if (group) favorites.set(group.groupId, group);
    }
    return { version: 1, rememberHistory: row.rememberHistory, theme: row.theme,
      category: searchQuery(row.category),
      history: row.rememberHistory ? [...new Set<string>(row.history.slice(0, 20)
        .filter((value: unknown) => typeof value === "string")
        .map((value: string) => searchQuery(value)).filter(Boolean))] : [],
      favorites: [...favorites.values()] };
  } catch { return emptyPreferences(); }
}
export function rememberQuery(state: Preferences, raw: string): Preferences {
  const query = searchQuery(raw);
  if (!state.rememberHistory || !query || state.history[0] === query) return state;
  return { ...state, history: [query, ...state.history.filter(value => value !== query)].slice(0, 20) };
}
export function toggleFavorite(state: Preferences, value: unknown): Preferences {
  const group = parsePublicMetadata(value);
  if (!group) return state;
  const exists = state.favorites.some(row => row.groupId === group.groupId);
  return { ...state, favorites: exists ? state.favorites.filter(row => row.groupId !== group.groupId)
    : [group, ...state.favorites].slice(0, 100) };
}
