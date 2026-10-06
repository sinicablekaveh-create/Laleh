package com.sinicable.telegramelectric;

import com.sinicable.telegramelectric.groupsearch.GroupSearchResult;
import com.sinicable.telegramelectric.groupsearch.GroupSearchService;
import com.sinicable.telegramelectric.groupsearch.TelegramGroupSearchAdapter;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramGroupSearchAdapterTest {

    @Test
    public void adapterMapsOnlyIdsReturnedByCurrentDiscoveryRequest() {
        TelegramClientManager manager = mock(TelegramClientManager.class);

        TelegramClientManager.GroupInfo current =
                new TelegramClientManager.GroupInfo(
                        77L,
                        "گروه برق صنعتی",
                        "https://t.me/electric_group",
                        1200,
                        "public",
                        true,
                        true
                );
        TelegramClientManager.GroupInfo stale =
                new TelegramClientManager.GroupInfo(
                        88L,
                        "گروه قدیمی",
                        "https://t.me/old_group",
                        10,
                        "public",
                        true,
                        true
                );

        when(manager.getFoundGroups()).thenReturn(List.of(current, stale));
        doAnswer(call -> {
            TelegramClientManager.DiscoveryCallback callback = call.getArgument(1);
            callback.onDetailedResult(true, 1, 1, "ok", List.of(77L));
            return null;
        }).when(manager).discoverPublicGroupsForReview(eq("برق صنعتی"), any());

        TelegramGroupSearchAdapter adapter = new TelegramGroupSearchAdapter(manager);
        AtomicReference<List<GroupSearchResult>> received = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        adapter.search("برق صنعتی", new GroupSearchService.GatewayCallback() {
            @Override
            public void onResults(List<GroupSearchResult> results) {
                received.set(results);
            }

            @Override
            public void onError(String message) {
                error.set(message);
            }
        });

        assertNull(error.get());
        assertNotNull(received.get());
        assertEquals(1, received.get().size());
        GroupSearchResult result = received.get().get(0);
        assertEquals(77L, result.getChatId());
        assertEquals("electric_group", result.getUsername());
        assertEquals(1200, result.getMemberCount());
        assertTrue(result.getScore() > 0);
    }
}
