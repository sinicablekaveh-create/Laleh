import type { CatalogService } from "./catalog-service.ts";
import type { PublicGroup } from "./metadata.ts";
import { searchQuery } from "./query.ts";
import { searchOptions, type SearchOptions } from "./search-options.ts";

export const API_VERSION = 1 as const;
export const API_SOURCE = "local-approved-catalog" as const;

type ApiMeta = Readonly<{ source: typeof API_SOURCE; publicOnly: true }>;
export type ApiSuccess<T> = Readonly<{ apiVersion: typeof API_VERSION; data: T; meta: ApiMeta }>;
export type ApiErrorBody = Readonly<{
  apiVersion: typeof API_VERSION;
  error: Readonly<{ code: string; message: string }>;
  meta: ApiMeta;
}>;
export type ContractResponse<T> = Readonly<{ status: number; body: ApiSuccess<T> | ApiErrorBody }>;

const meta: ApiMeta = Object.freeze({ source: API_SOURCE, publicOnly: true });
const MIN_GROUP_ID = -9223372036854775808n;

function success<T>(data: T): ContractResponse<T> {
  return Object.freeze({ status: 200, body: Object.freeze({ apiVersion: API_VERSION, data, meta }) });
}

function failure<T>(status: number, code: string, message: string): ContractResponse<T> {
  return Object.freeze({
    status,
    body: Object.freeze({ apiVersion: API_VERSION, error: Object.freeze({ code, message }), meta }),
  });
}

function publicGroup(group: PublicGroup): PublicGroup {
  return Object.freeze({
    schemaVersion: 2,
    groupId: group.groupId,
    title: group.title,
    username: group.username,
    category: group.category,
    location: group.location,
    revision: group.revision,
  });
}

function boundedInteger(raw: string | null, fallback: number, minimum: number, maximum: number): number {
  if (!raw || !/^\d{1,8}$/u.test(raw)) return fallback;
  const value = Number(raw);
  return Number.isSafeInteger(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
}

function validGroupId(groupId: string): boolean {
  if (!/^-[1-9][0-9]{0,18}$/u.test(groupId)) return false;
  try {
    return BigInt(groupId) >= MIN_GROUP_ID;
  } catch {
    return false;
  }
}

export type SearchApiData = Readonly<{
  query: string;
  total: number;
  offset: number;
  limit: number;
  filters: Required<SearchOptions>;
  items: readonly Readonly<{ group: PublicGroup; score: number }>[];
}>;

export function searchContract(catalog: CatalogService, params: URLSearchParams): ContractResponse<SearchApiData> {
  const offset = boundedInteger(params.get("offset"), 0, 0, 10_000);
  const limit = boundedInteger(params.get("limit"), 20, 1, 50);
  const filters = searchOptions({
    category: params.get("category") ?? undefined,
    location: params.get("location") ?? undefined,
    sort: params.get("sort") === "title" ? "title" : "relevance",
  });
  const page = catalog.search(searchQuery(params.get("q") ?? undefined), offset, limit, filters);
  return success(Object.freeze({
    query: page.query,
    total: page.total,
    offset: page.offset,
    limit: page.limit,
    filters,
    items: Object.freeze(page.hits.map(hit => Object.freeze({ group: publicGroup(hit.group), score: hit.score }))),
  }));
}

export function groupContract(catalog: CatalogService, groupId: string): ContractResponse<PublicGroup> {
  if (!validGroupId(groupId)) return failure(400, "INVALID_GROUP_ID", "groupId must be a valid negative Telegram chat identifier.");
  const group = catalog.get(groupId);
  if (!group) return failure(404, "GROUP_NOT_FOUND", "No approved public group exists for this identifier.");
  return success(publicGroup(group));
}

export type RelatedApiData = Readonly<{ groupId: string; items: readonly PublicGroup[] }>;

export function relatedContract(
  catalog: CatalogService,
  groupId: string,
  rawLimit: string | null = null,
): ContractResponse<RelatedApiData> {
  if (!validGroupId(groupId)) return failure(400, "INVALID_GROUP_ID", "groupId must be a valid negative Telegram chat identifier.");
  const group = catalog.get(groupId);
  if (!group) return failure(404, "GROUP_NOT_FOUND", "No approved public group exists for this identifier.");

  const limit = boundedInteger(rawLimit, 6, 1, 20);
  const options: SearchOptions = group.category
    ? { category: group.category }
    : group.location ? { location: group.location } : {};
  const page = catalog.search("", 0, Math.min(50, limit + 1), options);
  const items = page.hits
    .map(hit => hit.group)
    .filter(candidate => candidate.groupId !== groupId)
    .slice(0, limit)
    .map(publicGroup);

  return success(Object.freeze({ groupId, items: Object.freeze(items) }));
}

export type CategoriesApiData = Readonly<{
  categories: readonly Readonly<{ name: string; count: number }>[];
  locations: readonly string[];
}>;

export function categoriesContract(catalog: CatalogService): ContractResponse<CategoriesApiData> {
  return success(Object.freeze({
    categories: Object.freeze(catalog.categories().map(row => Object.freeze({ name: row.name, count: row.count }))),
    locations: Object.freeze([...catalog.locations()]),
  }));
}

export type HealthApiData = Readonly<{
  status: "ok";
  backend: "disabled";
  catalogEntries: number;
}>;

export function healthContract(catalog: CatalogService): ContractResponse<HealthApiData> {
  return success(Object.freeze({
    status: "ok",
    backend: "disabled",
    catalogEntries: catalog.search("", 0, 1).total,
  }));
}

export function jsonResponse<T>(result: ContractResponse<T>): Response {
  return Response.json(result.body, {
    status: result.status,
    headers: { "Cache-Control": "no-store" },
  });
}
