package com.sinicable.telegramelectric;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** One-at-a-time public outbox delivery; owns no thread, client, session or credentials. */
public final class DiscoverySyncRunner {
    public enum Result { ACCEPTED, RETRYABLE_FAILURE, PERMANENT_REJECTION }
    public interface Completion { void complete(Result result); }
    public interface Transport { void upload(DiscoveryMetadata item, Completion completion); }
    private final DiscoverySyncQueue queue;
    private final PublicDiscoveryCache cache;
    private final Transport transport;
    private final ScheduledExecutorService scheduler;
    private final LongSupplier clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public DiscoverySyncRunner(DiscoverySyncQueue queue, PublicDiscoveryCache cache, Transport transport,
                               ScheduledExecutorService scheduler, LongSupplier clock) {
        this.queue = queue; this.cache = cache; this.transport = transport;
        this.scheduler = scheduler; this.clock = clock;
    }

    public boolean runOnce() {
        if (!running.compareAndSet(false, true)) return false;
        DiscoveryMetadata item = queue.nextReady(clock.getAsLong());
        if (item == null) { running.set(false); return false; }
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
        Completion finish = result -> {
            if (!completed.compareAndSet(false, true)) return;
            ScheduledFuture<?> timer = deadline.get();
            if (timer != null) timer.cancel(false);
            try {
                if (result == Result.ACCEPTED) {
                    if (queue.isEnabled()) cache.put(item);
                    queue.acknowledge(item.groupId, item.revision);
                } else {
                    queue.defer(item.groupId, item.revision, clock.getAsLong(),
                            result == Result.PERMANENT_REJECTION);
                }
            } finally { running.set(false); }
        };
        try {
            deadline.set(scheduler.schedule(() -> finish.complete(Result.RETRYABLE_FAILURE),
                    30, TimeUnit.SECONDS));
            transport.upload(item, finish);
        } catch (RuntimeException error) { finish.complete(Result.RETRYABLE_FAILURE); }
        return true;
    }
}
