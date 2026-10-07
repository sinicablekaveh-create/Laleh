package com.sinicable.telegramelectric;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class PublicDiscoveryCacheTest {
    private DiscoveryMetadata item(long id, long revision) {
        return new DiscoveryMetadata(id, "برق", "electric_group", "برق", "تهران", revision);
    }
    @Test public void persistedEntriesExpireAtTtlAndClockRollbackInvalidatesThem() {
        TestPreferences storage = new TestPreferences();
        AtomicLong now = new AtomicLong(1000);
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, now::get);
        cache.put(item(-1, 1));
        assertNotNull(new PublicDiscoveryCache(storage.context, 1000, now::get).get(-1));
        now.set(2000);
        assertNull(cache.get(-1));
        assertEquals(0, new PublicDiscoveryCache(storage.context, 1000, now::get).size());
        cache.put(item(-2, 1));
        now.set(1500);
        assertNull(cache.get(-2));
    }
    @Test public void lruCapacityRevisionProtectionAndExplicitInvalidationWork() {
        TestPreferences storage = new TestPreferences();
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        for (int i = 1; i <= 200; i++) cache.put(item(-i, 2));
        assertNotNull(cache.get(-1));
        cache.put(item(-201, 1));
        assertEquals(200, cache.size());
        assertNull(cache.get(-2));
        assertNotNull(cache.get(-1));
        assertFalse(cache.put(item(-1, 1)));
        cache.invalidate(-1);
        assertNull(cache.get(-1));
        cache.clear();
        assertEquals(0, new PublicDiscoveryCache(storage.context, 1000, () -> 1000).size());
    }
}
