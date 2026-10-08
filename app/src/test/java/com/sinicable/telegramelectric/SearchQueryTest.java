package com.sinicable.telegramelectric;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SearchQueryTest {
    @Test public void recognizesCategoryLocationAndLearningIntent() {
        SearchQuery query = SearchQuery.parse("گروه آموزش برق صنعتی تهران");
        assertEquals("برق صنعتی", query.category);
        assertEquals("تهران", query.location);
        assertEquals(SearchQuery.Intent.LEARNING, query.intent);
        assertEquals(List.of("آموزش", "برق", "صنعتی"), query.keywords);
        assertThrows(UnsupportedOperationException.class, () -> query.keywords.add("x"));
    }
    @Test public void normalizesArabicAndDoesNotInferCitiesFromSubstrings() {
        SearchQuery query = SearchQuery.parse(" خرید كابل  مشهد ");
        assertEquals("خرید کابل مشهد", query.normalized);
        assertEquals("کابل", query.category);
        assertEquals(SearchQuery.Intent.MARKET, query.intent);
        assertEquals("", SearchQuery.parse("تهرانی").location);
    }
    @Test public void unknownAndEmptyQueriesAreSafeAndBoundsPreserveCodepoints() {
        assertEquals("", SearchQuery.parse(null).normalized);
        assertEquals("", SearchQuery.parse("دانشگاه").category);
        assertEquals(96, SearchQuery.parse("😀".repeat(150)).normalized.codePointCount(0, 192));
        assertEquals(16, SearchQuery.parse("aa bb cc dd ee ff gg hh ii jj kk ll mm nn oo pp qq").keywords.size());
    }
}
