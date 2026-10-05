package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CentralCoreTest {
    private final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
    private final List<Scheduled> delayed = new ArrayList<>();
    private final List<TdApi.Function> requests = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private MockedStatic<Looper> loopers;
    private MockedConstruction<Handler> handlers;
    private TelegramClientManager telegram;
    private WordBank words;
    private CentralCore core;
    private boolean deferDiscovery;
    private Client.ResultHandler pendingDiscovery;
    private String sendFailure;
    private String discoveryFailure;

    private static final class Scheduled {
        final Runnable runnable;
        final long delay;
        Scheduled(Runnable runnable, long delay) { this.runnable = runnable; this.delay = delay; }
    }

    @Before
    public void setUp() throws Exception {
        loopers = mockStatic(Looper.class);
        loopers.when(Looper::getMainLooper).thenReturn(mock(Looper.class));
        handlers = mockConstruction(Handler.class, (handler, context) -> {
            when(handler.post(any(Runnable.class))).thenAnswer(call -> {
                immediate.add(call.getArgument(0));
                return true;
            });
            when(handler.postDelayed(any(Runnable.class), anyLong())).thenAnswer(call -> {
                delayed.add(new Scheduled(call.getArgument(0), call.getArgument(1)));
                return true;
            });
            doAnswer(call -> {
                Runnable target = call.getArgument(0);
                immediate.removeIf(value -> value == target);
                delayed.removeIf(value -> value.runnable == target);
                return null;
            }).when(handler).removeCallbacks(any(Runnable.class));
        });
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs);
        when(prefs.getString(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(prefs.getStringSet(anyString(), anySet())).thenAnswer(call -> call.getArgument(1));
        when(prefs.edit()).thenReturn(editor);

        telegram = new TelegramClientManager(context, mock(TelegramClientManager.Listener.class));
        Client transport = mock(Client.class);
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            Client.ResultHandler response = call.getArgument(1);
            requests.add(request);
            if (request instanceof TdApi.SendMessage) {
                response.onResult(sendFailure == null ? new TdApi.Message() : new TdApi.Error(429, sendFailure));
            } else if (discoveryFailure != null) {
                response.onResult(new TdApi.Error(429, discoveryFailure));
            } else if (deferDiscovery) {
                pendingDiscovery = response;
            } else {
                TdApi.Chats result = new TdApi.Chats();
                result.chatIds = new long[0];
                response.onResult(result);
            }
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        setTelegramField("client", transport);
        setTelegramField("currentStep", TelegramClientManager.AuthStep.READY);
        setTelegramField("connectionReady", true);

        words = mock(WordBank.class);
        when(words.size()).thenReturn(1);
        when(words.allWords()).thenReturn(List.of("برق"));
        core = new CentralCore(context, telegram, words, new CentralCore.Listener() {
            @Override public void onStatus(String message) { statuses.add(message); }
            @Override public void onDataChanged() { }
        });
    }

    @After
    public void tearDown() throws Exception {
        if (core != null) core.shutdown();
        if (telegram != null) {
            for (String name : new String[] {"runtimeExecutor", "storageExecutor"}) {
                Field field = TelegramClientManager.class.getDeclaredField(name);
                field.setAccessible(true);
                ((ExecutorService) field.get(telegram)).shutdownNow();
            }
        }
        if (handlers != null) handlers.close();
        if (loopers != null) loopers.close();
    }

    @Test
    public void startWithoutMessageOrTargetsActivatesDiscoveryWithoutSending() {
        assertTrue(core.start());
        assertTrue(core.isEnabled());
        runImmediateTasks();
        assertEquals(1, requests.size());
        assertEquals("SearchPublicChats", requests.get(0).getClass().getSimpleName());
        assertEquals(0L, core.getSentCount());
    }

    @Test
    public void startSendsOnlyToSelectedJoinedMemberGroup() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusMember());
        joinedGroup(6L, new TdApi.ChatMemberStatusMember());
        core.setMessage(" پیام برق ");
        core.setGroupSelected(-1005L, true);
        assertTrue(core.start());
        runImmediateTasks();
        List<TdApi.SendMessage> sent = sentMessages();
        assertEquals(1, sent.size());
        assertEquals(-1005L, sent.get(0).chatId);
        TdApi.InputMessageText text = (TdApi.InputMessageText) sent.get(0).inputMessageContent;
        assertEquals("پیام برق", text.text.text);
        assertEquals(1L, core.getSentCount());
    }

    @Test
    public void startWithoutTextSearchesInsteadOfSendingToSelectedGroup() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusMember());
        core.setGroupSelected(-1005L, true);
        assertTrue(core.start());
        runImmediateTasks();
        assertTrue(sentMessages().isEmpty());
        assertEquals(1, requests.size());
        assertEquals("SearchPublicChats", requests.get(0).getClass().getSimpleName());
    }

    @Test
    public void discoveryResponseAfterStopCannotOverwriteStoppedStatus() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusAdministrator());
        core.setMessage("برق");
        core.setGroupSelected(-1005L, true);
        deferDiscovery = true;
        assertTrue(core.start());
        runImmediateTasks();
        assertNotNull(pendingDiscovery);
        core.stop();
        String stopped = statuses.get(statuses.size() - 1);
        int statusCount = statuses.size();
        TdApi.Chats result = new TdApi.Chats();
        result.chatIds = new long[0];
        pendingDiscovery.onResult(result);
        runImmediateTasks();
        assertFalse(core.isEnabled());
        assertEquals(stopped, statuses.get(statuses.size() - 1));
        assertEquals(statusCount, statuses.size());
        assertTrue(delayed.isEmpty());
    }

    @Test
    public void savingActiveSettingsPreservesHourlyIntervalInsteadOfSendingAgain() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusAdministrator());
        core.setMessage("برق");
        core.setGroupSelected(-1005L, true);
        core.setMode(CentralCore.ScheduleMode.EVERY_HOUR);
        assertTrue(core.start());
        runImmediateTasks();
        assertEquals(1, sentMessages().size());
        core.setMessage("متن جدید برق");
        assertTrue(core.start());
        runImmediateTasks();
        assertEquals(1, sentMessages().size());
        assertTrue(core.isEnabled());
        assertEquals("متن جدید برق", core.getMessage());
        assertTrue(delayed.stream().anyMatch(task -> task.delay >= 3_600_000L));
    }

    @Test
    public void changingAndSavingActiveSettingsCannotCancelTelegramFloodWait() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusAdministrator());
        core.setMessage("برق");
        core.setGroupSelected(-1005L, true);
        sendFailure = "FLOOD_WAIT_300";
        assertTrue(core.start());
        runImmediateTasks();
        assertEquals(1, sentMessages().size());
        core.setMode(CentralCore.ScheduleMode.EVERY_HOUR);
        assertTrue(core.start());
        runImmediateTasks();
        assertTrue(core.isEnabled());
        assertEquals(1, sentMessages().size());
        assertEquals(1, delayed.size());
        assertTrue(delayed.get(0).delay >= 300_000L);
    }

    @Test
    public void recognizesAllTelegramRateLimitWaitFormats() {
        assertEquals(300_000L, CentralCore.parseRetryWaitMillis("429: FLOOD_WAIT_300"));
        assertEquals(45_000L, CentralCore.parseRetryWaitMillis("FLOOD_PREMIUM_WAIT_45"));
        assertEquals(12_000L, CentralCore.parseRetryWaitMillis("SLOWMODE_WAIT_12"));
        assertEquals(9_000L, CentralCore.parseRetryWaitMillis("Too Many Requests: retry after 9"));
        assertEquals(0L, CentralCore.parseRetryWaitMillis("400: CHAT_WRITE_FORBIDDEN"));
    }

    @Test
    public void premiumFloodWaitDelaysTheNextSendInsteadOfRetryingEarly() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusAdministrator());
        core.setMessage("برق");
        core.setGroupSelected(-1005L, true);
        sendFailure = "FLOOD_PREMIUM_WAIT_45";

        assertTrue(core.start());
        runImmediateTasks();

        assertEquals(1, sentMessages().size());
        assertEquals(1, delayed.size());
        assertEquals(45_000L, delayed.get(0).delay);
    }

    @Test
    public void stoppingAndRestartingCannotBypassTelegramFloodWait() throws Exception {
        joinedGroup(5L, new TdApi.ChatMemberStatusAdministrator());
        core.setMessage("برق");
        core.setGroupSelected(-1005L, true);
        sendFailure = "FLOOD_WAIT_300";

        assertTrue(core.start());
        runImmediateTasks();
        assertEquals(1, sentMessages().size());

        core.stop();
        sendFailure = null;
        assertTrue(core.start());
        runImmediateTasks();

        assertEquals(1, sentMessages().size());
        assertTrue(delayed.stream().anyMatch(task -> task.delay > 299_000L));
    }

    @Test
    public void discoveryFloodWaitDoesNotRetryOrPenalizeTheSearchTerm() {
        discoveryFailure = "FLOOD_WAIT_180";

        assertTrue(core.start());
        runImmediateTasks();

        assertEquals(1, requests.size());
        verify(words, never()).recordSearchFeedback(anyString(), anyInt(), anyInt());
        assertFalse(delayed.stream().anyMatch(task -> task.delay == 30_000L));
        assertTrue(statuses.get(statuses.size() - 1).contains("موقتاً محدود"));
    }

    private void runImmediateTasks() {
        for (int i = 0; !immediate.isEmpty() && i < 20; i++) immediate.remove().run();
        assertTrue("Unexpected immediate task loop", immediate.isEmpty());
    }

    private List<TdApi.SendMessage> sentMessages() {
        List<TdApi.SendMessage> sent = new ArrayList<>();
        for (TdApi.Function request : requests) if (request instanceof TdApi.SendMessage) sent.add((TdApi.SendMessage) request);
        return sent;
    }

    private void joinedGroup(long id, TdApi.ChatMemberStatus status) throws Exception {
        TdApi.Supergroup group = new TdApi.Supergroup();
        group.id = id;
        group.status = status;
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = -1000L - id;
        chat.title = "گروه " + id;
        TdApi.ChatTypeSupergroup type = new TdApi.ChatTypeSupergroup();
        type.supergroupId = id;
        chat.type = type;
        chat.permissions = new TdApi.ChatPermissions();
        chat.permissions.canSendBasicMessages = true;
        chat.permissions.canSendPhotos = true;
        update(new TdApi.UpdateSupergroup(group));
        update(new TdApi.UpdateNewChat(chat));
    }

    private void update(TdApi.Object value) throws Exception {
        Method method = TelegramClientManager.class.getDeclaredMethod("onUpdate", TdApi.Object.class);
        method.setAccessible(true);
        method.invoke(telegram, value);
    }

    private void setTelegramField(String name, Object value) throws Exception {
        Field field = TelegramClientManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(telegram, value);
    }
}
