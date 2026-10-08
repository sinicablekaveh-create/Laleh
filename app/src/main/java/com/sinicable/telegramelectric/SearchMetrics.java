package com.sinicable.telegramelectric;

/** Lifetime-local numeric aggregates; contains no query, identifier or user content. */
public final class SearchMetrics {
    public static final class Snapshot {
        public final long requests, completed, failures, timeouts, cacheHits, coalesced;
        public final long discoveryResults, totalLatencyNanos, maximumLatencyNanos;
        private Snapshot(SearchMetrics source) {
            requests = source.requests;
            completed = source.completed;
            failures = source.failures;
            timeouts = source.timeouts;
            cacheHits = source.cacheHits;
            coalesced = source.coalesced;
            discoveryResults = source.discoveryResults;
            totalLatencyNanos = source.totalLatencyNanos;
            maximumLatencyNanos = source.maximumLatencyNanos;
        }
    }
    private long requests, completed, failures, timeouts, cacheHits, coalesced;
    private long discoveryResults, totalLatencyNanos, maximumLatencyNanos;
    synchronized void requestStarted() { requests++; }
    synchronized void requestCompleted(boolean success, boolean timeout, long latencyNanos) {
        completed++;
        if (!success) failures++;
        if (timeout) timeouts++;
        long elapsed = Math.max(0, latencyNanos);
        totalLatencyNanos += elapsed;
        maximumLatencyNanos = Math.max(maximumLatencyNanos, elapsed);
    }
    synchronized void cacheHit() { cacheHits++; }
    synchronized void coalesced() { coalesced++; }
    synchronized void discovered(int count) { discoveryResults += Math.max(0, count); }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
}
