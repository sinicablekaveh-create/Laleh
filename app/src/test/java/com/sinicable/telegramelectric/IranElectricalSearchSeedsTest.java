package com.sinicable.telegramelectric;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IranElectricalSearchSeedsTest {
    @Test public void seedPackIncludesCategoryAndMajorCityQueries() {
        List<String> seeds = IranElectricalSearchSeeds.build();

        assertEquals(1000, seeds.size());
        assertTrue(seeds.contains("برق صنعتی تهران"));
        assertTrue(seeds.contains("تابلو برق مشهد"));
        assertTrue(seeds.contains("انرژی خورشیدی اصفهان"));
    }
}
