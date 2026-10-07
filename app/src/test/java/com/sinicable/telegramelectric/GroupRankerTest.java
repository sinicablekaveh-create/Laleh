package com.sinicable.telegramelectric;

import com.sinicable.telegramelectric.groupsearch.GroupRanker;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class GroupRankerTest {
    private final GroupRanker ranker = new GroupRanker();

    @Test public void exactThenPhraseThenCategoryAndLocationThenUnrelated() {
        String query = "برق صنعتی تهران";
        int exact = ranker.score(query, "", query);
        int phrase = ranker.score("گروه برق صنعتی تهران", "", query);
        int tokens = ranker.score("صنعتی تهران برق", "", query);
        assertTrue(exact > phrase);
        assertTrue(phrase > tokens);
        assertTrue(tokens > ranker.score("برق", "", query));
        assertEquals(0, ranker.score("عمومی", "", query));
    }

    @Test public void emptyQueryHasNoRelevanceAndArabicVariantsMatch() {
        assertEquals(0, ranker.score("برق", "electric", null));
        assertEquals(0, ranker.score("برق", "electric", "  "));
        assertEquals(ranker.score("کابل", "", "کابل"), ranker.score("كابل", "", "کابل"));
    }

    @Test public void caseMatchingDoesNotDependOnDeviceLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(ranker.score("industrial", "", "industrial"),
                    ranker.score("INDUSTRIAL", "", "industrial"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
