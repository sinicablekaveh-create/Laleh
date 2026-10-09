package com.sinicable.telegramelectric;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** One-at-a-time public outbox delivery; owns no thread, client, session or credentials. */
public final class DiscoverySyncRunner implements AutoCloseable {
    public enum Result { ACCEPTED, RETRYABLE_FAILURE, PERMANENT_REJECTION }
    public interface Completion { void complete(Result result); }
    public interface Transport { void upload(DiscoveryMetadata item, Completion completion); }
    /** Production adapters must cancel the underlying request, not merely discard its callback. */
    public interface CancellableTransport extends Transport {
        Runnable uploadCancellable(DiscoveryMetadata item, Completion completion);
        @Override default void upload(DiscoveryMetadata item, Completion completion) {
            uploadCancellable(item, completion);
        }
    }
    private final DiscoverySyncQueue queue;
    private final PublicDiscoveryCache cache;
    private final Transport transport;
    private final ScheduledExecutorService scheduler;
    private final LongSupplier clock;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Completion> active = new AtomicReference<>();

    public DiscoverySyncRunner(DiscoverySyncQueue queue, PublicDiscoveryCache cache, Transport transport,
                               ScheduledExecutorService scheduler, LongSupplier clock) {
        this.queue = Objects.requireNonNull(queue); this.cache = Objects.requireNonNull(cache);
        this.transport = Objects.requireNonNull(transport); this.scheduler = Objects.requireNonNull(scheduler);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean runOnce() {
        if (closed.get() || !running.compareAndSet(false, true)) return false;
        final DiscoveryMetadata item;
        final long consent;
        try {
            synchronized (queue) {
                item = queue.nextReady(clock.getAsLong());
                consent = queue.consentGeneration();
            }
        } catch (RuntimeException error) { running.set(false); throw error; }
        if (item == null || closed.get()) { running.set(false); return false; }
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean dispatching = new AtomicBoolean(true);
        AtomicBoolean finalized = new AtomicBoolean();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (!dispatching.get() && finalized.get() && released.compareAndSet(false, true)) {
                active.set(null); running.set(false);
            }
        };
        AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
        AtomicReference<Runnable> cancellation = new AtomicReference<>();
        Completion finish = result -> {
            if (!completed.compareAndSet(false, true)) return;
            ScheduledFuture<?> timer = deadline.get();
            if (timer != null) timer.cancel(false);
            try {
                Runnable cancel = cancellation.getAndSet(null);
                if (cancel != null) cancel.run();
            } catch (RuntimeException ignored) { /* A failed cancel must not wedge local state. */ }
            try {
                synchronized (queue) {
                    if (!closed.get() && queue.hasConsent(consent)) {
                        if (result == Result.ACCEPTED) {
                            cache.put(item);
                            queue.acknowledge(item.groupId, item.revision);
                        } else {
                            queue.defer(item.groupId, item.revision, clock.getAsLong(),
                                    result == Result.PERMANENT_REJECTION);
                        }
                    }
                }
            } finally {
                finalized.set(true); release.run();
            }
        };
        active.set(finish);
        try {
            ScheduledFuture<?> timer = scheduler.schedule(() -> finish.complete(Result.RETRYABLE_FAILURE),
                    30, TimeUnit.SECONDS);
            deadline.set(timer);
            if (completed.get() && timer != null) timer.cancel(false);
            if (closed.get()) finish.complete(Result.RETRYABLE_FAILURE);
            synchronized (queue) {
                if (!queue.hasConsent(consent)) finish.complete(Result.RETRYABLE_FAILURE);
                if (!completed.get() && !closed.get()) {
                    if (transport instanceof CancellableTransport) {
                        Runnable cancel = ((CancellableTransport) transport).uploadCancellable(item, finish);
                        cancellation.set(cancel);
                        if (completed.get()) {
                            Runnable lateCancel = cancellation.getAndSet(null);
                            if (lateCancel != null) lateCancel.run();
                        }
                    } else transport.upload(item, finish);
                }
            }
        } catch (RuntimeException error) { finish.complete(Result.RETRYABLE_FAILURE); }
        finally {
            dispatching.set(false);
            release.run();
        }
        return true;
    }

    @Override public void close() {
        closed.set(true);
        Completion completion = active.get();
        if (completion != null) completion.complete(Result.RETRYABLE_FAILURE);
    }
}
