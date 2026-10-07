package com.sinicable.telegramelectric;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WordBankTest {
    private TestPreferences storage;

    @Before public void setUp() {
        storage = new TestPreferences();
        storage.values("electrical_word_bank").put("iran_seed_version", 2);
        storage.values("electrical_word_bank").put("words", Set.of("برق", "برق ساختمان", "کابل"));
    }

    @Test public void removingSeedSurvivesReconstruction() {
        WordBank bank = new WordBank(storage.context);
        assertTrue(bank.remove("برق"));
        WordBank restored = new WordBank(storage.context);
        assertEquals(2, restored.size());
        assertFalse(restored.allWords().contains("برق"));
    }

    @Test public void intentionallyEmptyBankStaysEmptyEvenBeforeSeedMigration() {
        storage.values("electrical_word_bank").put("words", Set.of());
        storage.values("electrical_word_bank").remove("iran_seed_version");
        assertEquals(0, new WordBank(storage.context).size());
        assertEquals(0, new WordBank(storage.context).size());
    }

    @Test public void crudNormalizesPersianLettersAndWhitespaceAndRejectsDuplicates() {
        WordBank bank = new WordBank(storage.context);
        assertTrue(bank.add("  صنعت\u00a0   ساختمان  "));
        assertFalse(bank.add("صنعت ساختمان"));
        assertTrue(bank.edit("صنعت ساختمان", "صنعت\n  كابل"));
        assertTrue(bank.allWords().contains("صنعت کابل"));
        assertFalse(bank.edit("صنعت کابل", "کابل"));
        assertEquals(4, bank.size());
        assertTrue(bank.remove("صنعت  كابل"));
        assertEquals(3, new WordBank(storage.context).size());
    }

    @Test public void normalizationUnifiesArabicVariantsAndInvisibleSeparators() {
        WordBank bank = new WordBank(storage.context);
        assertTrue(bank.add("تأسیسات\u200cكهربائية"));
        String alternate = "تاسیسات كهربائيه";
        assertFalse(bank.add(alternate));
        assertTrue(bank.allWords().contains(WordBank.normalize(alternate)));
    }

    @Test public void invalidInputsDoNotMutateBank() {
        WordBank bank = new WordBank(storage.context);
        for (String value : new String[] {null, "", " ", "۱۱۲۳", "!!!", "ا", "x".repeat(97)}) {
            assertFalse(bank.add(value));
            assertFalse(bank.edit("برق", value));
        }
        assertEquals(3, bank.size());
    }

    @Test public void versionOneMigrationAddsCitiesAndPreservesDeletedWords() {
        storage.values("electrical_word_bank").put("iran_seed_version", 1);
        storage.values("electrical_word_bank").put("removed_words", Set.of("برق صنعتی تهران"));
        WordBank migrated = new WordBank(storage.context);
        assertFalse(migrated.allWords().contains("برق صنعتی تهران"));
        assertTrue(migrated.allWords().contains("برق صنعتی مشهد"));
        assertTrue(migrated.allWords().contains("کابل"));
        assertEquals(migrated.allWords(), new WordBank(storage.context).allWords());
        assertEquals(2, storage.values("electrical_word_bank").get("iran_seed_version"));
    }

    @Test public void versionOneEmptyBankRemainsEmptyAfterCityMigration() {
        storage.values("electrical_word_bank").put("iran_seed_version", 1);
        storage.values("electrical_word_bank").put("words", Set.of());
        assertEquals(0, new WordBank(storage.context).size());
        assertEquals(0, new WordBank(storage.context).size());
    }

    @Test public void deletedWordsCannotBeRelearnedButManualAddCanRestoreThem() {
        try (MockedConstruction<OfflineWordAI> models = mockConstruction(OfflineWordAI.class, (ai, context) ->
                when(ai.analyze(anyString(), anySet())).thenReturn(new OfflineWordAI.Result(100,
                        List.of(new OfflineWordAI.Suggestion("کابل", 20)))))) {
            WordBank bank = new WordBank(storage.context);
            assertTrue(bank.remove("کابل"));
            assertEquals(0, bank.learnFromMessage("برق کابل"));
            WordBank restored = new WordBank(storage.context);
            assertEquals(0, restored.learnFromMessage("برق کابل"));
            assertFalse(restored.allWords().contains("کابل"));
            assertTrue(restored.add("كابل"));
            assertTrue(new WordBank(storage.context).allWords().contains("کابل"));
        }
    }

    @Test public void renamedWordCannotBeRelearnedUnderOldName() {
        try (MockedConstruction<OfflineWordAI> models = mockConstruction(OfflineWordAI.class, (ai, context) ->
                when(ai.analyze(anyString(), anySet())).thenReturn(new OfflineWordAI.Result(100,
                        List.of(new OfflineWordAI.Suggestion("کابل", 20)))))) {
            WordBank bank = new WordBank(storage.context);
            assertTrue(bank.edit("کابل", "سیم کابل"));
            assertEquals(0, bank.learnFromMessage("برق کابل"));
            assertFalse(new WordBank(storage.context).allWords().contains("کابل"));
        }
    }
}
