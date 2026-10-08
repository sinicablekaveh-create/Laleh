import { searchQuery } from "./query.ts";

export type SearchOptions = Readonly<{ category?: string; location?: string; sort?: "relevance" | "title" }>;
type Parameter = string | string[] | undefined;
export type SearchParameters = { q?: Parameter; category?: Parameter; location?: Parameter; sort?: Parameter; page?: Parameter };
const first = (value: Parameter) => Array.isArray(value) ? value[0] : value;

export function searchOptions(options: SearchOptions = {}): Required<SearchOptions> {
  return { category: searchQuery(options.category), location: searchQuery(options.location),
    sort: options.sort === "title" ? "title" : "relevance" };
}

export function searchState(params: SearchParameters) {
  const page = first(params.page) ?? "1";
  return { query: searchQuery(params.q), ...searchOptions({ category: first(params.category),
    location: first(params.location), sort: first(params.sort) === "title" ? "title" : "relevance" }),
    page: /^\d{1,6}$/.test(page) ? Math.max(1, Math.min(500, Number(page))) : 1 };
}

export function searchLink(state: ReturnType<typeof searchState>, page = 1): string {
  const params = new URLSearchParams({ q: state.query });
  if (state.category) params.set("category", state.category);
  if (state.location) params.set("location", state.location);
  if (state.sort !== "relevance") params.set("sort", state.sort);
  params.set("page", String(Math.max(1, Math.min(500, Math.floor(page) || 1))));
  return `/?${params}`;
}
