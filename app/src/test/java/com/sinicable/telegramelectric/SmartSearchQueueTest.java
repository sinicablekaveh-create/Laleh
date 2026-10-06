package com.sinicable.telegramelectric;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SmartSearchQueueTest {
    private TestPreferences storage;
    private WordBank words;
    private SmartSearchQueue queue;

    @Before public void setUp() {
        storage = new TestPreferences();
        words = mock(WordBank.class);
        when(words.allWords()).thenReturn(List.of("برق ساختمان", "صنعت ساختمان"));
        queue = new SmartSearchQueue(storage.context, words);
    }

    @Test public void defaultStagesMatchBothRequestedExamples() {
        assertEquals(List.of("برق ساختمان", "برق ساختما", "برق ساختم", "برق ساخت", "برق ساخ"),
                SmartSearchQueue.stagesFor("برق ساختمان", 5, 3));
        assertEquals(List.of("صنعت ساختمان", "صنعت ساختما", "صنعت ساختم", "صنعت ساخت", "صنعت ساخ"),
                SmartSearchQueue.stagesFor("صنعت ساختمان", 5, 3));
    }

    @Test public void configuredStopPreservesPrefixAndShortWordsSearchOnce() {
        assertEquals(List.of("برق ساختمان", "برق ساختما"), SmartSearchQueue.stagesFor("برق ساختمان", 2, 3));
        assertEquals(List.of("صنعت ساختمان", "صنعت ساختما", "صنعت ساختم"),
                SmartSearchQueue.stagesFor("صنعت ساختمان", 50, 5));
        assertEquals(List.of("برق"), SmartSearchQueue.stagesFor("برق", 5, 3));
        assertEquals(List.of("صنعت برق"), SmartSearchQueue.stagesFor("صنعت برق", 5, 3));
    }

    @Test public void everyStageOfEveryWordRunsOnceThenQueueCompletes() {
        for (String word : List.of("برق ساختمان", "صنعت ساختمان")) {
            for (int stage = 1; stage <= 5; stage++) {
                SmartSearchQueue.SearchTask task = queue.nextTask();
                assertEquals(word, task.originalWord);
                assertEquals(stage, task.stage);
                assertTrue(queue.recordResult(task, true, 0, 0, "", List.of(), false));
            }
        }
        assertNull(queue.nextTask());
        assertEquals(2, queue.completedWordCount());
        assertEquals(10, queue.getHistoryCount());
    }

    @Test public void processRecreationResumesCurrentWordAndRetainsResultIds() {
        SmartSearchQueue.SearchTask first = queue.nextTask();
        queue.recordResult(first, true, 1, 2, "", List.of(-100L, -200L), false);
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals("برق ساختما", restored.nextTask().query);
        WordSearchResult history = restored.getHistory().get(0);
        assertEquals("برق ساختمان", history.originalWord);
        assertEquals(1, history.stage);
        assertEquals(List.of(-100L, -200L), history.resultIds);
        assertTrue(history.success);
        assertTrue(history.timestamp > 0L);
    }

    @Test public void stoppingDoesNotConsumeStageOrAcceptLateResult() {
        SmartSearchQueue.SearchTask task = queue.nextTask();
        queue.cancelPending();
        assertFalse(queue.recordResult(task, true, 1, 1, "", List.of(), false));
        assertEquals(task.query, queue.nextTask().query);
        assertEquals(0, queue.getHistoryCount());
    }

    @Test public void sameSizeRenameRemovesStaleTaskAndAddsNewWord() {
        SmartSearchQueue.SearchTask old = queue.nextTask();
        when(words.allWords()).thenReturn(List.of("تابلو برق", "صنعت ساختمان"));
        assertEquals(2, queue.size());
        assertFalse(queue.recordResult(old, true, 1, 1, "", List.of(), false));
        assertEquals("تابلو برق", queue.nextTask().originalWord);
    }

    @Test public void emptySavedBankRemovesLegacyStates() {
        when(words.allWords()).thenReturn(List.of());
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals(0, restored.size());
        assertNull(restored.nextTask());
    }

    @Test public void failedStageIsRetainedAndNextStageStillRuns() {
        SmartSearchQueue.SearchTask task = queue.nextTask();
        queue.recordResult(task, false, 0, 0, "Telegram 403: SEARCH_FORBIDDEN", List.of(), false);
        assertEquals(2, queue.nextTask().stage);
        WordSearchResult result = queue.getHistory().get(0);
        assertFalse(result.success);
        assertEquals("Telegram 403: SEARCH_FORBIDDEN", result.error);
        verify(words, never()).recordSearchFeedback(anyString(), anyInt(), anyInt());
    }

    @Test public void rateLimitRetainsSameStageAcrossRestartAndRecordsError() {
        SmartSearchQueue.SearchTask task = queue.nextTask();
        queue.recordResult(task, false, 0, 0, "Telegram 429: FLOOD_WAIT_300", List.of(), true);
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals(1, restored.nextTask().stage);
        assertFalse(restored.getHistory().get(0).success);
    }

    @Test public void historyRetainsAllResultsAndPagesNewestFirst() {
        when(words.allWords()).thenReturn(List.of("برق"));
        for (int i = 0; i < 125; i++) {
            queue.restart();
            queue.recordResult(queue.nextTask(), true, 1, 1, "", List.of((long) i), false);
        }
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals(125, restored.getHistory().size());
        assertEquals(List.of(124L), restored.getHistory(0, 10).get(0).resultIds);
        assertEquals(List.of(114L), restored.getHistory(10, 10).get(0).resultIds);
        assertEquals(List.of(0L), restored.getHistory(124, 10).get(0).resultIds);
    }

    @Test public void configRestartsStagesButPreservesHistoryAndPersistsBounds() {
        queue.recordResult(queue.nextTask(), true, 1, 1, "", List.of(), false);
        queue.configure(3, 5);
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals(3, restored.getMaxStages());
        assertEquals(5, restored.getMinimumLength());
        assertEquals(1, restored.nextTask().stage);
        assertEquals(1, restored.getHistoryCount());
    }

    @Test public void newlyAddedEarlierWordCannotInterruptCurrentWordAcrossRecreation() {
        queue.recordResult(queue.nextTask(), true, 0, 0, "", List.of(), false);
        when(words.allWords()).thenReturn(List.of("آمپر", "برق ساختمان", "صنعت ساختمان"));
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        for (int stage = 2; stage <= 5; stage++) {
            SmartSearchQueue.SearchTask task = restored.nextTask();
            assertEquals("برق ساختمان", task.originalWord);
            assertEquals(stage, task.stage);
            restored.recordResult(task, true, 0, 0, "", List.of(), false);
            restored = new SmartSearchQueue(storage.context, words);
        }
        assertEquals("آمپر", restored.nextTask().originalWord);
    }

    @Test public void rateLimitedCurrentWordStaysPinnedWhenEarlierWordIsAdded() {
        SmartSearchQueue.SearchTask task = queue.nextTask();
        queue.recordResult(task, false, 0, 0, "Telegram 429: FLOOD_WAIT_300", List.of(), true);
        when(words.allWords()).thenReturn(List.of("آمپر", "برق ساختمان", "صنعت ساختمان"));
        SmartSearchQueue restored = new SmartSearchQueue(storage.context, words);
        assertEquals("برق ساختمان", restored.nextTask().originalWord);
        assertEquals(1, restored.nextTask().stage);
    }
}
