package com.hooshikadev.app;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchPlannerTest {
    @Test public void createsDistinctPublicRubikaQueries() {
        List<String> queries = SearchPlanner.queries("تابلو برق");
        assertEquals(8, queries.size());
        assertTrue(queries.get(0).contains("site:rubika.ir"));
        assertTrue(queries.get(0).contains("تابلو برق"));
    }
}
