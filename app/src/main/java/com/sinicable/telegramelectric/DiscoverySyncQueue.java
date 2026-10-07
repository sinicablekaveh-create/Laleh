package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

/** Bounded opt-in public-metadata outbox sharing the existing discovery preferences. */
public final class DiscoverySyncQueue {
    private static final String KEY_ENABLED = "public_sync_enabled";
    private static final String KEY_QUEUE = "public_sync_queue_v1";
    public static final int CAPACITY = 100;
    private final SharedPreferences prefs;
    private final LinkedHashMap<Long, DiscoveryMetadata> pending = new LinkedHashMap<>();
    private boolean enabled;

    public DiscoverySyncQueue(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("telegram_discovery", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(KEY_ENABLED, false);
        if (!enabled) return;
        try {
            JSONArray items = new JSONArray(prefs.getString(KEY_QUEUE, "[]"));
            for (int i = 0; i < Math.min(items.length(), CAPACITY); i++) {
                try {
                    DiscoveryMetadata item = DiscoveryMetadata.fromJson(items.getJSONObject(i));
                    DiscoveryMetadata old = pending.get(item.groupId);
                    if (old == null || item.revision > old.revision) pending.put(item.groupId, item);
                } catch (JSONException | IllegalArgumentException ignored) { /* Discard invalid public entry. */ }
            }
        } catch (JSONException ignored) { /* Corrupt outbox is treated as empty, without logging content. */ }
    }

    public synchronized boolean isEnabled() { return enabled; }

    /** Disabling removes queued public records as well as revoking future enqueue. */
    public synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) pending.clear();
        persist();
    }

    public synchronized boolean offer(DiscoveryMetadata item) {
        if (!enabled || item == null) return false;
        DiscoveryMetadata old = pending.get(item.groupId);
        if (old != null && old.revision >= item.revision) return false;
        if (old == null && pending.size() >= CAPACITY) return false;
        pending.put(item.groupId, item);
        persist();
        return true;
    }

    public synchronized List<DiscoveryMetadata> snapshot() { return new ArrayList<>(pending.values()); }

    /** An acknowledgment of an older revision must never remove a newer queued update. */
    public synchronized boolean acknowledge(long groupId, long revision) {
        DiscoveryMetadata item = pending.get(groupId);
        if (item == null || item.revision != revision) return false;
        pending.remove(groupId);
        persist();
        return true;
    }

    private void persist() {
        try {
            JSONArray items = new JSONArray();
            for (DiscoveryMetadata item : pending.values()) items.put(item.toJson());
            prefs.edit().putBoolean(KEY_ENABLED, enabled).putString(KEY_QUEUE, items.toString()).apply();
        } catch (JSONException error) {
            throw new IllegalStateException("Public outbox serialization failed", error);
        }
    }
}
