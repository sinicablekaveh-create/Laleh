package com.sinicable.telegramelectric;

import com.sinicable.telegramelectric.groupsearch.GroupKeywordBank;
import com.sinicable.telegramelectric.groupsearch.SmartKeywordQueue;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class DiscoverySuggestionsTest {
    @Test public void suggestionsKeepCityAndNormalizeAndDeduplicateVariants() {
        List<String> result = new SmartKeywordQueue().build("كابل مشهد",
                new GroupKeywordBank().related("كابل مشهد"));
        assertTrue(result.contains("سیم و کابل مشهد"));
        assertEquals(result.size(), Set.copyOf(result).size());
        assertEquals(List.of("کابل"), new SmartKeywordQueue().build("كابل", List.of("کابل", "كابل", "")));
    }
    @Test public void suggestionsAreBoundedAndUnknownTopicsHaveNoExpansion() {
        assertEquals(8, new SmartKeywordQueue().build("word", List.of(
                "aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh", "ii")).size());
        assertTrue(new GroupKeywordBank().related("unknown").isEmpty());
        assertTrue(new SmartKeywordQueue().build(null, null).isEmpty());
    }
    @Test public void deletedSuggestionIsNotRestoredUntilUserManuallyAddsIt() {
        TestPreferences storage = new TestPreferences();
        storage.values("electrical_word_bank").put("iran_seed_version", 2);
        storage.values("electrical_word_bank").put("words", Set.of("کابل برق مشهد"));
        WordBank bank = new WordBank(storage.context);
        assertTrue(bank.remove("کابل برق مشهد"));
        assertTrue(bank.suggestions("کابل مشهد").contains("کابل برق مشهد"));
        assertEquals(0, bank.size());
        assertTrue(bank.add("کابل برق مشهد"));
        assertFalse(bank.suggestions("کابل مشهد").contains("کابل برق مشهد"));
    }
}
