"use client";

import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { emptyPreferences, parsePreferences, PREFERENCES_KEY, rememberQuery, toggleFavorite,
  type Preferences } from "../../lib/preferences";
import type { PublicGroup } from "../../lib/metadata";

const eventName = "laleh-preferences-changed";
function subscribe(listener: () => void) {
  window.addEventListener("storage", listener);
  window.addEventListener(eventName, listener);
  return () => { window.removeEventListener("storage", listener); window.removeEventListener(eventName, listener); };
}
function read() {
  try { return window.localStorage.getItem(PREFERENCES_KEY) ?? ""; } catch { return ""; }
}
function usePreferences() {
  const serialized = useSyncExternalStore(subscribe, read, () => "");
  const preferences = useMemo(() => parsePreferences(serialized), [serialized]);
  const [storageFailed, setStorageFailed] = useState(false);
  const update = useCallback((change: (state: Preferences) => Preferences) => {
    try {
      const previous = parsePreferences(read());
      const next = change(previous);
      if (next !== previous) window.localStorage.setItem(PREFERENCES_KEY, JSON.stringify(next));
      setStorageFailed(false);
      window.dispatchEvent(new Event(eventName));
    } catch { setStorageFailed(true); }
  }, []);
  useEffect(() => { document.documentElement.dataset.theme = preferences.theme; }, [preferences.theme]);
  return { preferences, update, storageFailed };
}

export function FavoriteButton({ group }: { group: PublicGroup }) {
  const { preferences, update, storageFailed } = usePreferences();
  const selected = preferences.favorites.some(row => row.groupId === group.groupId);
  return <><button className="secondary" type="button" aria-pressed={selected}
    onClick={() => update(state => toggleFavorite(state, group))}>
    {selected ? "حذف از علاقه‌مندی‌ها" : "ذخیره در علاقه‌مندی‌ها"}</button>
    {storageFailed && <p role="status">ذخیره‌سازی مرورگر در دسترس نیست.</p>}</>;
}

export function DiscoveryPreferences({ query = "", category = "" }: { query?: string; category?: string }) {
  const { preferences, update, storageFailed } = usePreferences();
  useEffect(() => { update(state => rememberQuery(state, query)); }, [query, preferences.rememberHistory, update]);
  return <details className="preferences"><summary>تنظیمات و جست‌وجوهای اخیر</summary>
    <p>این تنظیمات فقط در همین مرورگر ذخیره می‌شوند و به تلگرام یا سرویس دیگری ارسال نمی‌شوند.</p>
    <label><input type="checkbox" checked={preferences.rememberHistory}
      onChange={event => update(state => ({ ...state, rememberHistory: event.target.checked,
        history: event.target.checked ? state.history : [] }))} /> ذخیرهٔ تاریخچهٔ جست‌وجو در این مرورگر</label>
    <label htmlFor="discovery-theme">ظاهر</label>
    <select id="discovery-theme" value={preferences.theme} onChange={event => update(state => ({
      ...state, theme: event.target.value as Preferences["theme"] }))}>
      <option value="system">مطابق دستگاه</option><option value="light">روشن</option><option value="dark">تیره</option>
    </select>
    <button type="button" className="secondary" onClick={() => update(state => ({ ...state, category }))}>
      ذخیرهٔ دسته‌بندی انتخابی</button>
    {preferences.category && <p><Link href={`/?${new URLSearchParams({ category: preferences.category })}`}>
      دسته‌بندی دلخواه: {preferences.category}</Link></p>}
    <p><Link href="/saved">گروه‌های ذخیره‌شده</Link></p>
    {preferences.history.length > 0 && <ul>{preferences.history.map(value => <li key={value}>
      <Link href={`/?${new URLSearchParams({ q: value })}`}>{value}</Link></li>)}</ul>}
    <button type="button" className="secondary" onClick={() => update(() => emptyPreferences())}>
      پاک کردن همهٔ داده‌های ذخیره‌شدهٔ مرورگر</button>
    {storageFailed && <p role="status">ذخیره‌سازی مرورگر در دسترس نیست.</p>}
  </details>;
}

export function SavedGroups() {
  const { preferences } = usePreferences();
  return <section aria-label="گروه‌های ذخیره‌شده">
    {preferences.favorites.length === 0 ? <p>هنوز گروهی ذخیره نشده است.</p>
      : <div className="results">{preferences.favorites.map(group => <article className="group" key={group.groupId}>
        <h2>{group.title}</h2><p>{[group.category, group.location].filter(Boolean).join(" · ")}</p>
        <p><Link href={`/groups/${group.groupId}`}>بررسی وضعیت فعلی گروه</Link></p>
        <FavoriteButton group={group} />
      </article>)}</div>}
  </section>;
}
