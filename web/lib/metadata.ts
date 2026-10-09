import { normalizeQuery } from "./query.ts";

export type PublicGroup = Readonly<{
  schemaVersion: 2;
  groupId: string;
  title: string;
  username: string;
  category: string;
  location: string;
  revision: string;
}>;

const fields = new Set(["schemaVersion", "groupId", "title", "username", "category", "location", "revision"]);
const unsafeLabel = /[\u0000-\u001F\u007F\u202A-\u202E\u2066-\u2069]/u;
const MIN_LONG = -9223372036854775808n;
const MAX_LONG = 9223372036854775807n;

/** V1 numeric IDs migrate only when JavaScript can represent them exactly. */
export function parsePublicMetadata(value: unknown): PublicGroup | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const row = value as Record<string, unknown>;
  if (Object.keys(row).some(key => !fields.has(key)) || (row.schemaVersion !== 1 && row.schemaVersion !== 2)) return null;
  let id = row.groupId;
  let revision = row.revision;
  if (row.schemaVersion === 1) {
    if (!Number.isSafeInteger(id) || !Number.isSafeInteger(revision)) return null;
    id = String(id); revision = String(revision);
  }
  if (typeof id !== "string" || !/^-[1-9][0-9]{0,18}$/u.test(id) || BigInt(id) < MIN_LONG) return null;
  if (typeof revision !== "string" || !/^[1-9][0-9]{0,18}$/u.test(revision) || BigInt(revision) > MAX_LONG) return null;
  if (typeof row.title !== "string" || !row.title.trim() || [...row.title.trim()].length > 256
    || /[\u0000-\u001F\u007F\u202A-\u202E\u2066-\u2069]/u.test(row.title)) return null;
  if (typeof row.username !== "string" || !/^[a-z][a-z0-9_]{4,31}$/u.test(row.username)) return null;
  if (typeof row.category !== "string" || typeof row.location !== "string"
    || unsafeLabel.test(row.category) || unsafeLabel.test(row.location)) return null;
  const category = normalizeQuery(row.category), location = normalizeQuery(row.location);
  if ([...category].length > 96 || [...location].length > 96) return null;
  return Object.freeze({ schemaVersion: 2, groupId: id, title: row.title.trim(), username: row.username,
    category, location, revision });
}
