package com.sinicable.telegramelectric.groupsearch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded LRU with monotonic expiry. Values must be immutable snapshots. */
public final class GroupSearchCache<T> {
    private static final class Entry<T> {
        final T value; final long time;
        Entry(T value, long time) { this.value = value; this.time = time; }
    }
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    private final long ttlMillis;
    private final LongSupplier clock;
    public GroupSearchCache(int capacity, long ttlMillis, LongSupplier clock) {
        if (capacity < 1 || ttlMillis < 1) throw new IllegalArgumentException();
        this.capacity = capacity; this.ttlMillis = ttlMillis; this.clock = clock;
    }
    public synchronized T get(String query) {
        String key = PersianNormalizer.normalize(query);
        Entry<T> entry = entries.get(key);
        if (entry == null) return null;
        if (clock.getAsLong() - entry.time >= ttlMillis) { entries.remove(key); return null; }
        return entry.value;
    }
    public synchronized void put(String query, T value) {
        entries.put(PersianNormalizer.normalize(query), new Entry<>(value, clock.getAsLong()));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    public synchronized void clear() { entries.clear(); }
}
