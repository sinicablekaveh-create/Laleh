package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

/** Derived, bounded public metadata cache using existing discovery preferences. */
public final class PublicDiscoveryCache {
    private static final String KEY_CACHE = "public_cache_v1";
    public static final int CAPACITY = 200;
    private final SharedPreferences prefs;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private static final class Entry {
        final DiscoveryMetadata item;
        final long writtenAt;
        Entry(DiscoveryMetadata item, long writtenAt) { this.item = item; this.writtenAt = writtenAt; }
    }

    public PublicDiscoveryCache(Context context, long ttlMillis) {
        this(context, ttlMillis, System::currentTimeMillis);
    }

    PublicDiscoveryCache(Context context, long ttlMillis, LongSupplier clock) {
        if (ttlMillis < 1000 || ttlMillis > 86_400_000) throw new IllegalArgumentException("Invalid cache TTL");
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        prefs = context.getApplicationContext().getSharedPreferences("telegram_discovery", Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CACHE, "[]");
        if (raw == null || raw.length() > 1_000_000) return;
        try {
            JSONArray values = new JSONArray(raw);
            long now = clock.getAsLong();
            for (int i = 0; i < Math.min(values.length(), CAPACITY); i++) {
                try {
                    JSONObject value = values.getJSONObject(i);
                    DiscoveryMetadata item = DiscoveryMetadata.fromJson(value.getJSONObject("metadata"));
                    Entry entry = new Entry(item, value.getLong("writtenAt"));
                    Entry old = entries.get(item.groupId);
                    if (!expired(entry, now) && (old == null || old.item.revision < item.revision))
                        entries.put(item.groupId, entry);
                } catch (JSONException | IllegalArgumentException ignored) { /* Invalid public cache entry. */ }
            }
        } catch (JSONException ignored) { /* Corrupt derived cache is disposable. */ }
        persist();
    }

    public synchronized DiscoveryMetadata get(long groupId) {
        prune();
        Entry entry = entries.get(groupId);
        return entry == null ? null : entry.item;
    }

    public synchronized boolean put(DiscoveryMetadata item) {
        if (item == null) return false;
        prune();
        Entry old = entries.get(item.groupId);
        if (old != null && old.item.revision > item.revision) return false;
        entries.put(item.groupId, new Entry(item, clock.getAsLong()));
        while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
        persist();
        return true;
    }

    public synchronized void invalidate(long groupId) { entries.remove(groupId); persist(); }
    public synchronized void clear() { entries.clear(); persist(); }
    public synchronized int size() { prune(); return entries.size(); }

    private boolean expired(Entry entry, long now) {
        return entry.writtenAt < 0 || now < entry.writtenAt || now - entry.writtenAt >= ttlMillis;
    }
    private void prune() {
        long now = clock.getAsLong();
        if (entries.values().removeIf(entry -> expired(entry, now))) persist();
    }
    private void persist() {
        try {
            JSONArray values = new JSONArray();
            for (Entry entry : entries.values()) values.put(new JSONObject()
                    .put("metadata", entry.item.toJson()).put("writtenAt", entry.writtenAt));
            prefs.edit().putString(KEY_CACHE, values.toString()).apply();
        } catch (JSONException error) { throw new IllegalStateException("Public cache serialization failed", error); }
    }
}
