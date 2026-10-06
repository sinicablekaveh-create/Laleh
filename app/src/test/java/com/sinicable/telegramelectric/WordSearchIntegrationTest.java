package com.sinicable.telegramelectric;

import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WordSearchIntegrationTest {
    private final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
    private final List<Scheduled> delayed = new ArrayList<>();
    private final List<Request> searches = new ArrayList<>();
    private MockedStatic<Looper> loopers;
    private MockedConstruction<Handler> handlers;
    private TestPreferences storage;
    private WordBank words;
    private TelegramClientManager telegram;
    private CentralCore core;

    private static final class Scheduled {
        final Runnable runnable;
        final long delay;
        Scheduled(Runnable runnable, long delay) { this.runnable = runnable; this.delay = delay; }
    }

    private static final class Request {
        final String query;
        final TelegramClientManager.DiscoveryCallback callback;
        Request(String query, TelegramClientManager.DiscoveryCallback callback) {
            this.query = query; this.callback = callback;
        }
    }

    @Before public void setUp() {
        loopers = mockStatic(Looper.class);
        loopers.when(Looper::getMainLooper).thenReturn(mock(Looper.class));
        handlers = mockConstruction(Handler.class, (handler, context) -> {
            when(handler.post(any(Runnable.class))).thenAnswer(call -> {
                immediate.add(call.getArgument(0)); return true;
            });
            when(handler.postDelayed(any(Runnable.class), anyLong())).thenAnswer(call -> {
                delayed.add(new Scheduled(call.getArgument(0), call.getArgument(1))); return true;
            });
            doAnswer(call -> {
                Runnable target = call.getArgument(0);
                immediate.removeIf(value -> value == target);
                delayed.removeIf(value -> value.runnable == target);
                return null;
            }).when(handler).removeCallbacks(any(Runnable.class));
        });
        storage = new TestPreferences();
        storage.values("electrical_word_bank").put("iran_seed_version", 1);
        storage.values("electrical_word_bank").put("words", Set.of("برق ساختمان", "صنعت ساختمان"));
        words = spy(new WordBank(storage.context));
        // AI's Android Base64 storage is independent of staged-search persistence under test.
        doNothing().when(words).recordSearchFeedback(anyString(), anyInt(), anyInt());
        telegram = mock(TelegramClientManager.class);
        when(telegram.isReadyForSending()).thenReturn(true);
        doAnswer(call -> {
            searches.add(new Request(call.getArgument(0), call.getArgument(1))); return null;
        }).when(telegram).discoverPublicGroupsForReview(anyString(), any());
        core = newCore();
    }

    @After public void tearDown() {
        if (core != null) core.shutdown();
        if (handlers != null) handlers.close();
        if (loopers != null) loopers.close();
    }

    @Test public void searchOnlyControlsRunEveryActualStageAndNeverEnableSending() {
        core.startWordSearch();
        runImmediate();
        List<String> expected = List.of("برق ساختمان", "برق ساختما", "برق ساختم", "برق ساخت", "برق ساخ",
                "صنعت ساختمان", "صنعت ساختما", "صنعت ساختم", "صنعت ساخت", "صنعت ساخ");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), searches.get(i).query);
            searches.get(i).callback.onDetailedResult(true, 1, 2, "", List.of(-10L - i, -20L - i));
            runImmediate();
            if (i < expected.size() - 1) runDelayed(30_000L);
        }
        assertFalse(core.isEnabled());
        assertFalse(core.isWordSearchRunning());
        assertEquals(10L, core.getSearchHistoryCount());
        assertEquals(2, core.completedSearchWordCount());
        assertEquals(List.of(-19L, -29L), core.getSearchHistory(0, 1).get(0).resultIds);
        verify(telegram, never()).sendTextToChat(anyLong(), anyString(), any());
        verify(telegram, never()).sendPhotoToChat(anyLong(), anyString(), anyString(), any());
    }

    @Test public void pauseInvalidatesResponseAndResumeRetriesUnfinishedStage() {
        core.startWordSearch();
        runImmediate();
        Request old = searches.get(0);
        core.stopWordSearch();
        String stopped = core.getWordSearchStatus();
        old.callback.onDetailedResult(true, 1, 1, "", List.of(-1L));
        runImmediate();
        assertEquals(stopped, core.getWordSearchStatus());
        assertEquals(0L, core.getSearchHistoryCount());
        core.startWordSearch();
        runImmediate();
        assertEquals(old.query, searches.get(1).query);
        old.callback.onResult(false, 0, 0, "Late timeout");
        runImmediate();
        assertEquals(0L, core.getSearchHistoryCount());
    }

    @Test public void recreatedCoreResumesPersistedSearchWithoutSending() {
        core.startWordSearch();
        runImmediate();
        searches.get(0).callback.onDetailedResult(true, 1, 1, "", List.of(-1L));
        runImmediate();
        core.shutdown();
        core = newCore();
        assertTrue(core.isWordSearchRunning());
        assertFalse(core.isEnabled());
        runDelayed(5_000L);
        assertEquals("برق ساختما", searches.get(1).query);
        assertEquals(1L, core.getSearchHistoryCount());
    }

    @Test public void floodWaitDoesNotAdvanceStageAndCannotBeCancelledByRestart() {
        assertRateLimitRetainsStage("Telegram 429: FLOOD_WAIT_300");
    }

    @Test public void retryAfterRateLimitDoesNotAdvanceStageAndSurvivesRecreation() {
        assertRateLimitRetainsStage("Telegram 429: Too Many Requests: retry after 300");
        core.shutdown();
        core = newCore();
        runDelayed(5_000L);
        assertEquals(1, searches.size());
        assertTrue(core.isWordSearchWaitingForTelegram());
        assertTrue(delayed.stream().anyMatch(task -> task.delay >= 290_000L));
    }

    @Test public void ordinaryApiFailureIsSavedAndContinuesNextStage() {
        core.startWordSearch();
        runImmediate();
        searches.get(0).callback.onResult(false, 0, 0, "Telegram 403: SEARCH_FORBIDDEN");
        runImmediate();
        WordSearchResult failed = core.getSearchHistory(0, 1).get(0);
        assertFalse(failed.success);
        assertEquals("Telegram 403: SEARCH_FORBIDDEN", failed.error);
        runDelayed(30_000L);
        assertEquals("برق ساختما", searches.get(1).query);
    }

    @Test public void watchdogCompletesFailureAndDiscardsLateSuccess() {
        core.startWordSearch();
        runImmediate();
        Request old = searches.get(0);
        runDelayed(90_000L);
        assertFalse(core.getSearchHistory(0, 1).get(0).success);
        assertTrue(core.getSearchHistory(0, 1).get(0).error.contains("SEARCH_TIMEOUT"));
        runDelayed(30_000L);
        old.callback.onDetailedResult(true, 1, 1, "", List.of(-1L));
        runImmediate();
        assertEquals(1L, core.getSearchHistoryCount());
        assertEquals("برق ساختما", searches.get(1).query);
    }

    @Test public void editingWordDuringRequestPreventsStaleHistoryAndContinuesUpdatedBank() {
        core.startWordSearch();
        runImmediate();
        assertTrue(words.edit("برق ساختمان", "تابلو برق"));
        core.onWordBankChanged();
        searches.get(0).callback.onDetailedResult(true, 1, 1, "", List.of(-1L));
        runImmediate();
        assertEquals(0L, core.getSearchHistoryCount());
        runDelayed(30_000L);
        assertEquals("تابلو برق", searches.get(1).query);
    }

    @Test public void unavailableTelegramWaitsWithoutConsumingStage() {
        when(telegram.isReadyForSending()).thenReturn(false);
        core.startWordSearch();
        runImmediate();
        assertTrue(searches.isEmpty());
        assertTrue(core.isWordSearchWaitingForTelegram());
        when(telegram.isReadyForSending()).thenReturn(true);
        runDelayed(30_000L);
        assertEquals("برق ساختمان", searches.get(0).query);
    }

    @Test public void enablingCorePreservesAlreadyRunningSearchAndItsResponse() {
        core.startWordSearch();
        runImmediate();
        Request active = searches.get(0);
        assertTrue(core.start());
        runImmediate();
        assertEquals(1, searches.size());
        active.callback.onDetailedResult(true, 1, 1, "", List.of(-1L));
        runImmediate();
        assertEquals(1L, core.getSearchHistoryCount());
        assertEquals("برق ساختمان", core.getSearchHistory(0, 1).get(0).query);
    }

    @Test public void bankMutationAndCoreStartPreserveExistingSearchPacing() {
        core.startWordSearch();
        runImmediate();
        searches.get(0).callback.onResult(true, 0, 0, "");
        runImmediate();
        assertTrue(words.add("تابلو برق"));
        core.onWordBankChanged();
        assertTrue(core.start());
        runImmediate();
        assertEquals(1, searches.size());
        assertTrue(delayed.stream().allMatch(task -> task.delay >= 29_000L));
    }

    private void assertRateLimitRetainsStage(String error) {
        core.startWordSearch();
        runImmediate();
        searches.get(0).callback.onResult(false, 0, 0, error);
        runImmediate();
        assertTrue(delayed.stream().anyMatch(task -> task.delay >= 290_000L));
        assertFalse(core.getSearchHistory(0, 1).get(0).success);
        core.restartWordSearch();
        runImmediate();
        assertEquals(1, searches.size());
        assertTrue(core.isWordSearchWaitingForTelegram());
        assertEquals(0, core.completedSearchWordCount());
    }

    private CentralCore newCore() {
        return new CentralCore(storage.context, telegram, words, mock(CentralCore.Listener.class));
    }

    private void runImmediate() {
        for (int i = 0; !immediate.isEmpty() && i < 30; i++) immediate.remove().run();
        assertTrue("Unexpected immediate loop", immediate.isEmpty());
    }

    private void runDelayed(long delay) {
        Scheduled next = delayed.stream().filter(task -> task.delay == delay).findFirst()
                .orElseThrow(() -> new AssertionError("No task scheduled at " + delay));
        delayed.remove(next);
        next.runnable.run();
        runImmediate();
    }
}
