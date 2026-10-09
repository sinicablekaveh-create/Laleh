package com.sinicable.telegramelectric;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
public class DiscoveryResultsTest {
    private TelegramClientManager.GroupInfo group(int number, long id, String title, String link) {
        return new TelegramClientManager.GroupInfo(number, id, title, link, 0, "", false, true);
    }
    @Test public void rankedIdsRemainInOrderAndDuplicateCallbacksDoNotDuplicateCards() {
        TelegramClientManager.GroupInfo broad = group(1, -1, "گروه برق صنعتی تهران", "https://t.me/broad_group");
        TelegramClientManager.GroupInfo exact = group(9, -2, "برق صنعتی تهران", "https://t.me/exact_group");
        assertEquals(Arrays.asList(exact, broad), DiscoveryResults.select(Arrays.asList(broad, exact),
                Arrays.asList(-2L, -1L, -2L), Collections.emptySet(), false, ""));
    }
    @Test public void privateLinksAndUnselectedFavoritesAreExcluded() {
        TelegramClientManager.GroupInfo publicGroup = group(1, -1, "برق صنعتی تهران", "https://t.me/public_group");
        TelegramClientManager.GroupInfo invite = group(2, -2, "برق", "https://t.me/+private");
        assertEquals(Collections.singletonList(publicGroup), DiscoveryResults.select(Arrays.asList(publicGroup, invite),
                null, Collections.singleton(-1L), true, "برق صنعتي"));
        assertTrue(DiscoveryResults.select(Arrays.asList(publicGroup, invite), null,
                Collections.singleton(-2L), true, "").isEmpty());
    }
}
