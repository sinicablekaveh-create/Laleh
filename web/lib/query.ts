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
  let bounded = "", count = 0;
  for (const point of raw) { if (count++ >= 96) break; bounded += point; }
  return normalizeQuery(bounded);
}

const cities = ["تهران", "مشهد", "اصفهان", "شیراز", "تبریز"];
const categories = ["برق صنعتی", "برق ساختمان", "انرژی خورشیدی", "تابلو برق", "اتوماسیون صنعتی", "سیم و کابل", "برق", "کابل", "روشنایی"];
export type QueryAnalysis = Readonly<{ normalized: string; category: string; location: string;
  intent: "discovery" | "learning" | "market"; keywords: readonly string[] }>;

/** Same bounded intent/category/location rules used by Android SearchQuery. */
export function analyzeQuery(raw: string): QueryAnalysis {
  const normalized = searchQuery(raw);
  const has = (phrase: string) => (` ${normalized} `).includes(` ${phrase} `);
  const location = cities.find(has) ?? "";
  const category = categories.find(has) ?? "";
  const intent = has("آموزش") ? "learning" : has("خرید") || has("فروش") ? "market" : "discovery";
  const keywords = [...new Set(normalized.split(" ").filter(word => word && word !== location
    && !["گروه", "تلگرام", "و", "در", "از", "به", "برای"].includes(word)))].slice(0, 16);
  return Object.freeze({ normalized, category, location, intent, keywords: Object.freeze(keywords) });
}
