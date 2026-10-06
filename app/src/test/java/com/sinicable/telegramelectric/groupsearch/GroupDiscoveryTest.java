package com.sinicable.telegramelectric.groupsearch;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class GroupDiscoveryTest {
    @Test public void suggestionsContainRequestedFormsAndStayGroupFocused() {
        List<String> values = new GroupSuggestionEngine().suggest("برق لاله زار");
        assertTrue(values.containsAll(List.of("برق گروه", "لاله زار گروه", "گروه برق لاله",
                "گروه لاله زار برق", "گروه برق لاله زار", "لاله زار گروه برق")));
        assertEquals(values.size(), new HashSet<>(values).size());
        for (String value : values) assertTrue(PersianNormalizer.contains(value, "گروه"));
        assertFalse(values.toString().contains("PLC"));
        assertTrue(new GroupSuggestionEngine().suggest("  ").isEmpty());
        assertTrue(new GroupSuggestionEngine().suggest("گروه").isEmpty());
    }
    @Test public void normalizesArabicVariantsDiacriticsAndHalfSpaces() {
        assertEquals("الکتریکی لاله زار", PersianNormalizer.normalize("  اَلِكْتريكي  لاله‌زار  "));
        assertEquals(PersianNormalizer.normalize("لاله‌زار"), PersianNormalizer.normalize("لاله زار"));
        assertEquals("", PersianNormalizer.normalize(null));
        assertFalse(PersianNormalizer.contains("برقی", "برق"));
    }
    @Test public void queryBuilderDoesNotDuplicateGroupMarker() {
        SearchQueryBuilder builder = new SearchQueryBuilder();
        assertEquals("گروه برق", builder.publicQuery("برق"));
        assertEquals("برق گروه", builder.publicQuery("برق گروه"));
        assertEquals("", builder.publicQuery("گروه"));
        assertEquals(List.of(), new GroupKeywordBank().related("آشپزی"));
    }
    @Test public void acceptsOnlyBasicGroupsAndNonChannelSupergroups() {
        TdApi.Chat chat = new TdApi.Chat();
        assertFalse(GroupFilter.accepts(null));
        assertFalse(GroupFilter.accepts(chat));
        chat.type = new TdApi.ChatTypeBasicGroup();
        assertTrue(GroupFilter.accepts(chat));
        TdApi.ChatTypeSupergroup type = new TdApi.ChatTypeSupergroup();
        chat.type = type;
        assertTrue(GroupFilter.accepts(chat));
        type.isChannel = true;
        assertFalse(GroupFilter.accepts(chat));
        chat.type = new TdApi.ChatTypePrivate();
        assertFalse(GroupFilter.accepts(chat));
        chat.type = new TdApi.ChatTypeSecret();
        assertFalse(GroupFilter.accepts(chat));
    }
    @Test public void scoringHasIndependentSignalsAndNoUnrelatedLocationBonus() {
        GroupRankingEngine ranker = new GroupRankingEngine();
        assertEquals(100, ranker.score("گروه برق لاله زار", "", "برق لاله زار"));
        assertEquals(60, ranker.score("فروشندگان برق لاله زار", "", "برق لاله زار"));
        assertEquals(10, ranker.score("برق تهران", "", "برق لاله زار"));
        assertEquals(80, ranker.score("گروه برق", "", "برق"));
        assertEquals(0, ranker.score("آشپزی", "", "برق لاله زار"));
        assertEquals(0, ranker.score(null, null, null));
        assertEquals(0, ranker.score("برقی", "", "برق"));
    }
    @Test public void cacheNormalizesExpiresEvictsAndClears() {
        AtomicLong clock = new AtomicLong(100);
        GroupSearchCache<List<Long>> cache = new GroupSearchCache<>(2, 1000, clock::get);
        cache.put("لاله‌زار", List.of(1L));
        cache.put("برق", List.of(2L));
        assertEquals(List.of(1L), cache.get("لاله زار"));
        cache.put("صنعت", List.of(3L));
        assertNull(cache.get("برق"));
        clock.set(1100);
        assertNull(cache.get("لاله زار"));
        cache.put("برق", List.of());
        assertEquals(List.of(), cache.get("برق"));
        cache.clear();
        assertNull(cache.get("برق"));
    }
    @Test public void syncRoundTripsEverySourceAndLargeIds() throws Exception {
        for (GroupSearchRecord.Source source : GroupSearchRecord.Source.values()) {
            GroupSearchRecord input = new GroupSearchRecord("برق", Long.MIN_VALUE, "گروه برق", "electric", source, 80, 1234);
            GroupSearchRecord output = GroupSearchRecord.fromJson(input.toJson());
            assertEquals(Long.MIN_VALUE, output.groupId);
            assertEquals(input.source, output.source);
            assertEquals(input.score, output.score);
            assertEquals(input.query, output.query);
            assertEquals(input.username, output.username);
            assertEquals(input.title, output.title);
            assertEquals(input.timestamp, output.timestamp);
        }
    }
}
