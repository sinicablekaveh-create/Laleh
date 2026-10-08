/** Keep Persian/Arabic discovery normalization aligned with Android WordBank. */
export function normalizeQuery(value: string): string {
  return value.trim().toLowerCase()
    .replace(/[يىئ]/gu, "ی").replace(/ك/gu, "ک")
    .replace(/[ةۀ]/gu, "ه").replace(/[أإ]/gu, "ا").replace(/ؤ/gu, "و")
    .replace(/[\u200C\u200E\u200F]/gu, " ")
    .replace(/[\u064B-\u065F\u0670]/gu, "")
    .replace(/[\s\p{Z}]+/gu, " ").trim();
}

export function searchQuery(value: string | string[] | undefined): string {
  const raw = Array.isArray(value) ? value[0] ?? "" : value ?? "";
  return normalizeQuery([...raw].slice(0, 96).join(""));
}
