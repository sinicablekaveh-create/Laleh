package com.sinicable.telegramelectric.groupsearch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class GroupSearchServiceTest {

    @Test
    public void searchDeduplicatesAndKeepsBestRankedResult() {
        List<String> requested = new ArrayList<>();
        GroupSearchService.Gateway gateway = (query, callback) -> {
            requested.add(query);
            int score = query.startsWith("گروه ") ? 70 : 30;
            callback.onResults(List.of(
                    new GroupSearchResult(
                            77L,
                            "گروه برق صنعتی",
                            "electric_group",
                            "https://t.me/electric_group",
                            1200,
                            score
                    )
            ));
        };

        GroupSearchService service = new GroupSearchService(gateway);
        AtomicReference<List<GroupSearchResult>> received = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        service.search("برق  صنعتي", new GroupSearchService.Callback() {
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
        assertEquals(77L, received.get().get(0).getChatId());
        assertEquals(70, received.get().get(0).getScore());
        assertTrue(requested.contains("برق صنعتی"));
        assertTrue(requested.contains("گروه برق صنعتی"));
    }

    @Test
    public void emptyQueryReturnsEmptyWithoutCallingGateway() {
        int[] calls = {0};
        GroupSearchService service = new GroupSearchService((query, callback) -> calls[0]++);
        AtomicReference<List<GroupSearchResult>> received = new AtomicReference<>();

        service.search("   ", new GroupSearchService.Callback() {
            @Override
            public void onResults(List<GroupSearchResult> results) {
                received.set(results);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });

        assertEquals(0, calls[0]);
        assertNotNull(received.get());
        assertTrue(received.get().isEmpty());
    }
}
