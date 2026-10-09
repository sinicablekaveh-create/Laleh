package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

/** Bounded opt-in public-metadata outbox sharing the existing discovery preferences. */
public final class DiscoverySyncQueue {
    private static final String KEY_ENABLED = "public_sync_enabled";
    private static final String KEY_QUEUE = "public_sync_queue_v1";
    private static final String KEY_RETRIES = "public_sync_retries_v1";
    public static final int CAPACITY = 100;
    private final SharedPreferences prefs;
    private final LinkedHashMap<Long, DiscoveryMetadata> pending = new LinkedHashMap<>();
    private boolean enabled;
    private long consentGeneration;
    private final java.util.Map<Long, Retry> retries = new java.util.HashMap<>();
    private static final class Retry {
        final long revision, readyAt;
        final int attempts;
        Retry(long revision, long readyAt, int attempts) {
            this.revision = revision; this.readyAt = readyAt; this.attempts = attempts;
        }
    }

    public DiscoverySyncQueue(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("telegram_discovery", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(KEY_ENABLED, false);
        if (!enabled) return;
        try {
            String raw = prefs.getString(KEY_QUEUE, "[]");
            JSONArray items = new JSONArray(raw != null && raw.length() <= 1_000_000 ? raw : "[]");
            for (int i = 0; i < Math.min(items.length(), CAPACITY); i++) {
                try {
                    DiscoveryMetadata item = DiscoveryMetadata.fromJson(items.getJSONObject(i));
                    DiscoveryMetadata old = pending.get(item.groupId);
                    if (old == null || item.revision > old.revision) pending.put(item.groupId, item);
                } catch (JSONException | IllegalArgumentException ignored) { /* Discard invalid public entry. */ }
            }
        } catch (JSONException ignored) { /* Corrupt outbox is treated as empty, without logging content. */ }
        try {
            String raw = prefs.getString(KEY_RETRIES, "[]");
            JSONArray values = new JSONArray(raw != null && raw.length() <= 1_000_000 ? raw : "[]");
            for (int i = 0; i < Math.min(values.length(), CAPACITY); i++) {
                JSONObject value = values.getJSONObject(i);
                long id = value.getLong("groupId"), revision = value.getLong("revision");
                DiscoveryMetadata item = pending.get(id);
                if (item != null && item.revision == revision) retries.put(id,
                        new Retry(revision, Math.max(0, value.getLong("readyAt")),
                                Math.max(0, Math.min(10, value.getInt("attempts")))));
            }
        } catch (JSONException ignored) { /* Retry metadata is recoverable. */ }
    }

    public synchronized boolean isEnabled() { return enabled; }

    synchronized long consentGeneration() { return consentGeneration; }
    synchronized boolean hasConsent(long generation) {
        return enabled && generation == consentGeneration;
    }

    /** Disabling removes queued public records as well as revoking future enqueue. */
    public synchronized void setEnabled(boolean value) {
        if (enabled != value) consentGeneration++;
        enabled = value;
        if (!enabled) { pending.clear(); retries.clear(); }
        persist();
    }

    public synchronized boolean offer(DiscoveryMetadata item) {
        if (!enabled || item == null) return false;
        DiscoveryMetadata old = pending.get(item.groupId);
        if (old != null && old.revision >= item.revision) return false;
        if (old == null && pending.size() >= CAPACITY) return false;
        pending.put(item.groupId, item);
        retries.remove(item.groupId);
        persist();
        return true;
    }

    public synchronized List<DiscoveryMetadata> snapshot() { return new ArrayList<>(pending.values()); }

    /** An acknowledgment of an older revision must never remove a newer queued update. */
    public synchronized boolean acknowledge(long groupId, long revision) {
        DiscoveryMetadata item = pending.get(groupId);
        if (item == null || item.revision != revision) return false;
        pending.remove(groupId);
        retries.remove(groupId);
        persist();
        return true;
    }

    public synchronized DiscoveryMetadata nextReady(long now) {
        if (!enabled) return null;
        for (DiscoveryMetadata item : pending.values()) {
            Retry retry = retries.get(item.groupId);
            if (retry == null || (retry.readyAt != Long.MAX_VALUE && retry.readyAt <= now)) return item;
        }
        return null;
    }

    synchronized void defer(long id, long revision, long now, boolean permanent) {
        DiscoveryMetadata item = pending.get(id);
        if (item == null || item.revision != revision) return;
        Retry old = retries.get(id);
        int attempts = Math.min(10, old == null ? 1 : old.attempts + 1);
        long delay = Math.min(3_600_000, 5000L << (attempts - 1));
        long readyAt = permanent || now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
        retries.put(id, new Retry(revision, readyAt, attempts));
        persist();
    }

    private void persist() {
        try {
            JSONArray items = new JSONArray();
            for (DiscoveryMetadata item : pending.values()) items.put(item.toJson());
            JSONArray retryValues = new JSONArray();
            for (java.util.Map.Entry<Long, Retry> entry : retries.entrySet()) {
                Retry retry = entry.getValue();
                retryValues.put(new JSONObject().put("groupId", entry.getKey())
                        .put("revision", retry.revision).put("readyAt", retry.readyAt)
                        .put("attempts", retry.attempts));
            }
            prefs.edit().putBoolean(KEY_ENABLED, enabled).putString(KEY_QUEUE, items.toString())
                    .putString(KEY_RETRIES, retryValues.toString()).apply();
        } catch (JSONException error) {
            throw new IllegalStateException("Public outbox serialization failed", error);
        }
    }
}
