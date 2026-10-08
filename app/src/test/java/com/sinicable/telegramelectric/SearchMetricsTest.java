package com.sinicable.telegramelectric;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchMetricsTest {
    @Test public void snapshotsContainOnlyConsistentNumericAggregates() {
        SearchMetrics metrics = new SearchMetrics();
        SearchMetrics.Snapshot before = metrics.snapshot();
        metrics.requestStarted();
        metrics.requestStarted();
        metrics.requestCompleted(true, false, 40);
        metrics.requestCompleted(false, true, 80);
        metrics.cacheHit();
        metrics.coalesced();
        metrics.discovered(2);
        SearchMetrics.Snapshot after = metrics.snapshot();
        assertEquals(0, before.requests);
        assertEquals(2, after.requests);
        assertEquals(2, after.completed);
        assertEquals(1, after.failures);
        assertEquals(1, after.timeouts);
        assertEquals(1, after.cacheHits);
        assertEquals(1, after.coalesced);
        assertEquals(2, after.discoveryResults);
        assertEquals(120, after.totalLatencyNanos);
        assertEquals(80, after.maximumLatencyNanos);
    }
}
