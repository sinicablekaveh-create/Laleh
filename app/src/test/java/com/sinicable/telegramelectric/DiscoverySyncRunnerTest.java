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
    @Test public void revokedConsentCannotAcceptAnEarlierUploadAfterReenable() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        AtomicReference<DiscoverySyncRunner.Completion> response = new AtomicReference<>();
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache,
                (value, callback) -> response.set(callback), mock(ScheduledExecutorService.class), () -> 1000);
        assertTrue(runner.runOnce());
        queue.setEnabled(false); queue.setEnabled(true); queue.offer(item(1));
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        assertNull(cache.get(-55));
        assertEquals(1, queue.snapshot().size());
    }
    @Test public void closeCancelsTransportAndDoesNotAcceptLateCallbacks() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        AtomicReference<DiscoverySyncRunner.Completion> response = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger cancels = new java.util.concurrent.atomic.AtomicInteger();
        DiscoverySyncRunner.CancellableTransport transport = (value, callback) -> {
            response.set(callback); return cancels::incrementAndGet;
        };
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache, transport,
                mock(ScheduledExecutorService.class), () -> 1000);
        assertTrue(runner.runOnce()); runner.close(); runner.close();
        assertEquals(1, cancels.get()); assertFalse(runner.runOnce());
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        assertNull(cache.get(-55)); assertEquals(1, queue.snapshot().size());
    }
    @Test public void consentRevokedBeforeDispatchDoesNotInitiateTransport() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
            queue.setEnabled(false); return mock(ScheduledFuture.class);
        });
        DiscoverySyncRunner.Transport transport = mock(DiscoverySyncRunner.Transport.class);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue,
                new PublicDiscoveryCache(storage.context, 1000, () -> 1000), transport, scheduler, () -> 1000);
        assertTrue(runner.runOnce()); verifyNoInteractions(transport); assertFalse(runner.runOnce());
    }
    @Test public void immediateCompletionCannotStartAnotherUploadBeforeHandleReturns() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        AtomicReference<DiscoverySyncRunner> owner = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger cancels = new java.util.concurrent.atomic.AtomicInteger();
        DiscoverySyncRunner.CancellableTransport transport = (value, callback) -> {
            callback.complete(DiscoverySyncRunner.Result.ACCEPTED);
            queue.offer(item(2)); assertFalse(owner.get().runOnce());
            return cancels::incrementAndGet;
        };
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue,
                new PublicDiscoveryCache(storage.context, 1000, () -> 1000), transport,
                mock(ScheduledExecutorService.class), () -> 1000);
        owner.set(runner); assertTrue(runner.runOnce()); assertEquals(1, cancels.get());
        assertEquals(2, queue.snapshot().get(0).revision);
    }
    @Test public void timeoutCancelsPhysicalTransportOnceAndBacksOffRetry() {
        TestPreferences storage = new TestPreferences();
        DiscoverySyncQueue queue = new DiscoverySyncQueue(storage.context);
        queue.setEnabled(true); queue.offer(item(1));
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        AtomicReference<DiscoverySyncRunner.Completion> response = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger cancels = new java.util.concurrent.atomic.AtomicInteger();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
            timeout.set(call.getArgument(0)); return mock(ScheduledFuture.class);
        });
        DiscoverySyncRunner.CancellableTransport transport = (value, callback) -> {
            response.set(callback); return cancels::incrementAndGet;
        };
        PublicDiscoveryCache cache = new PublicDiscoveryCache(storage.context, 1000, () -> 1000);
        DiscoverySyncRunner runner = new DiscoverySyncRunner(queue, cache, transport, scheduler, () -> 1000);
        assertTrue(runner.runOnce()); timeout.get().run();
        response.get().complete(DiscoverySyncRunner.Result.ACCEPTED);
        assertEquals(1, cancels.get()); assertFalse(runner.runOnce()); assertNull(cache.get(-55));
        assertNotNull(queue.nextReady(6000));
    }
}
