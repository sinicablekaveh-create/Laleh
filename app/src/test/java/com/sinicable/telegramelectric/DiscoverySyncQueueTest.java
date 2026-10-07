package com.sinicable.telegramelectric;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoverySyncQueueTest {
    private DiscoveryMetadata item(long id, long revision) {
        return new DiscoveryMetadata(id, "گروه برق", "electric_group", "برق", "تهران", revision);
    }

    @Test public void queueIsOptInAndDisablingClearsPersistedWork() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        assertFalse(queue.offer(item(-1, 1)));
        queue.setEnabled(true);
        assertTrue(queue.offer(item(-1, 1)));
        assertEquals(1, new DiscoverySyncQueue(storage.context).snapshot().size());
        queue.setEnabled(false);
        assertTrue(new DiscoverySyncQueue(storage.context).snapshot().isEmpty());
    }

    @Test public void oldAckCannotDropNewRevisionAndCapacityIsBounded() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true);
        assertTrue(queue.offer(item(-1, 1)));
        assertTrue(queue.offer(item(-1, 2)));
        assertFalse(queue.acknowledge(-1, 1));
        assertFalse(queue.offer(item(-1, 1)));
        for (int i = 2; i <= DiscoverySyncQueue.CAPACITY; i++) assertTrue(queue.offer(item(-i, 1)));
        assertFalse(queue.offer(item(-101, 1)));
        queue.snapshot().clear();
        assertEquals(100, queue.snapshot().size());
        assertTrue(queue.acknowledge(-1, 2));
        assertEquals(99, new DiscoverySyncQueue(storage.context).snapshot().size());
    }

    @Test public void malformedAndUnknownSchemaEntriesAreNotRestored() {
        TestPreferences storage = new TestPreferences();
        storage.values("telegram_discovery").put("public_sync_enabled", true);
        storage.values("telegram_discovery").put("public_sync_queue_v1", "[{\"schemaVersion\":99}]");
        assertTrue(new DiscoverySyncQueue(storage.context).snapshot().isEmpty());
    }

    @Test public void metadataRejectsInvalidPublicIdentityAndExcludesPrivateFields() throws Exception {
        for (String username : new String[] {"", "https://evil.test", "invite+hash", "abc", "12345"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DiscoveryMetadata(-1, "برق", username, "", "", 1));
        }
        assertThrows(IllegalArgumentException.class, () -> item(1, 1));
        assertThrows(IllegalArgumentException.class, () -> item(-1, 0));
        assertEquals("https://t.me/electric_group", item(-1, 1).publicLink());
        assertEquals(7, item(-1, 1).toJson().length());
        assertEquals(-1, DiscoveryMetadata.fromJson(item(-1, 1).toJson()).groupId);
    }
}
