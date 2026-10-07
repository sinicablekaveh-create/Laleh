package com.sinicable.telegramelectric;

import org.junit.Test;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class DiscoverySyncRunnerTest {
    private DiscoveryMetadata item(long revision) {
        return new DiscoveryMetadata(-55, "برق", "electric_group", "برق", "تهران", revision);
    }
    @Test public void oldAcceptanceDoesNotDropNewerWorkAndDuplicateCallbacksDoNothing() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        AtomicReference<DiscoverySyncRunner.Completion> response = new AtomicReference<>();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache,
                (value, callback) -> response.set(callback), scheduler, () -> 1000);
        assertTrue(runner.runOnce()); assertFalse(runner.runOnce());
        queue.offer(item(2));
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        response.get().complete(DiscoverySyncRunner.Result.PERMANENT_REJECTION);
        assertEquals(2, queue.snapshot().get(0).revision);
        assertEquals(1, cache.get(-55).revision);
        assertTrue(runner.runOnce());
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        assertTrue(queue.snapshot().isEmpty());
        assertEquals(2, cache.get(-55).revision);
    }
    @Test public void retryDelaySurvivesReconstructionAndPermanentRejectionPausesWork() {
        TestPreferences storage = new TestPreferences();
        AtomicLong now = new AtomicLong(1000);
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, now::get);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache,
                (value, callback) -> callback.complete(DiscoverySyncRunner.Result.RETRYABLE_FAILURE),
                mock(ScheduledExecutorService.class), now::get);
        assertTrue(runner.runOnce()); assertFalse(runner.runOnce());
        assertNull(new DiscoverySyncQueue(storage.context).nextReady(5999));
        assertNotNull(new DiscoverySyncQueue(storage.context).nextReady(6000));
        now.set(6000);
        DiscoverySyncRunner rejecting = new DiscoverySyncRunner(queue, cache,
                (value, callback) -> callback.complete(DiscoverySyncRunner.Result.PERMANENT_REJECTION),
                mock(ScheduledExecutorService.class), now::get);
        assertTrue(rejecting.runOnce()); assertFalse(rejecting.runOnce());
        assertEquals(1, queue.snapshot().size());
        assertNull(new DiscoverySyncQueue(storage.context).nextReady(1_000_000));
    }
    @Test public void timeoutKeepsWorkQueuedAndDiscardsLateAcceptance() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        AtomicReference<DiscoverySyncRunner.Completion> response = new AtomicReference<>();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
            timeout.set(call.getArgument(0)); return mock(ScheduledFuture.class);
        });
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache,
                (value, callback) -> response.set(callback), scheduler, () -> 1000);
        assertTrue(runner.runOnce()); timeout.get().run();
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        assertEquals(1, queue.snapshot().size()); assertNull(cache.get(-55));
        queue.setEnabled(false); assertFalse(runner.runOnce());
    }
}
