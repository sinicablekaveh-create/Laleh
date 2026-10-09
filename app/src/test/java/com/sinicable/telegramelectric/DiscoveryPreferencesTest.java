package com.sinicable.telegramelectric;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiscoveryPreferencesTest {
    @Test public void historyRequiresOptInAndSurvivesReconstructionUntilRevoked() {
        TestPreferences storage = new TestPreferences();
        DiscoveryPreferences prefs = new DiscoveryPreferences(storage.context);
        prefs.remember("query"); assertTrue(prefs.history().isEmpty());
        prefs.setRememberHistory(true);
        for (int i = 0; i < 25; i++) prefs.remember("كابل " + i);
        assertEquals(20, prefs.history().size());
        assertEquals("کابل 24", new DiscoveryPreferences(storage.context).history().get(0));
        prefs.setRememberHistory(false); assertTrue(new DiscoveryPreferences(storage.context).history().isEmpty());
    }
    @Test public void favoritesAreBoundedDecimalIdsAndClearPreservesOtherStores() {
        TestPreferences storage = new TestPreferences();
        DiscoveryPreferences prefs = new DiscoveryPreferences(storage.context);
        for (int i = 1; i <= 110; i++) prefs.toggleFavorite(-i);
        assertEquals(100, prefs.favorites().size());
        prefs.toggleFavorite(-110); assertFalse(prefs.favorites().contains(-110L));
        prefs.toggleFavorite(1); assertEquals(99, prefs.favorites().size());
        storage.values("telegram_discovery").put("sentinel", "preserved");
        prefs.setTheme("dark"); prefs.setCategory("برق صنعتي");
        assertEquals("برق صنعتی", prefs.category());
        prefs.clear(); assertEquals("system", prefs.theme()); assertTrue(prefs.favorites().isEmpty());
        assertEquals("preserved", storage.values("telegram_discovery").get("sentinel"));
    }
    @Test public void malformedPreferencesAreRecoverable() {
        TestPreferences storage = new TestPreferences();
        storage.values("discovery_preferences_v1").put("favorites", "[\"-9223372036854775809\",{},\"-55\"]");
        DiscoveryPreferences prefs = new DiscoveryPreferences(storage.context);
        assertEquals(java.util.Collections.singleton(-55L), prefs.favorites());
        prefs.setTheme("unsupported"); assertEquals("system", prefs.theme());
    }
}
