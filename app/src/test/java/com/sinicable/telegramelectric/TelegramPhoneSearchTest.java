package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramPhoneSearchTest {
    private TelegramClientManager manager;
    private TelegramClientManager.Listener listener;
    private Client transport;
    private List<TdApi.Function> requests;
    private TdApi.Object response;

    @Before
    public void setUp() throws Exception {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(preferences.getString(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(preferences.edit()).thenReturn(editor);
        listener = mock(TelegramClientManager.Listener.class);
        manager = new TelegramClientManager(context, listener);
        transport = mock(Client.class);
        requests = new ArrayList<>();
        response = user();
        doAnswer(call -> {
            requests.add(call.getArgument(0));
            ((Client.ResultHandler) call.getArgument(1)).onResult(response);
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        setField("client", transport);
        setField("currentStep", TelegramClientManager.AuthStep.READY);
        setField("connectionReady", true);
    }

    @After
    public void tearDown() throws Exception {
        for (String name : new String[] {"runtimeExecutor", "storageExecutor"}) {
            ((ExecutorService) getField(name)).shutdownNow();
        }
    }

    @Test
    public void domesticPhonePerformsActualServerLookupAndStoresResult() {
        AtomicReference<TelegramClientManager.ContactInfo> found = new AtomicReference<>();
        manager.searchContactByPhone("۰۹۱۲ ۳۴۵ ۶۷۸۹", (success, contact, message) -> {
            assertTrue(message, success);
            found.set(contact);
        });
        assertEquals(1, requests.size());
        assertTrue(requests.get(0) instanceof TdApi.SearchUserByPhoneNumber);
        TdApi.SearchUserByPhoneNumber request = (TdApi.SearchUserByPhoneNumber) requests.get(0);
        assertEquals("+989123456789", request.phoneNumber);
        assertFalse("Lookup must reach the server, not only the cache", request.onlyLocal);
        assertEquals(44L, found.get().id);
        assertEquals("لاله برق", found.get().name);
        assertEquals("+989123456789", found.get().phone);
        assertEquals(1, manager.getTelegramContacts().size());
        verify(listener).onObservedUsersChanged();
    }

    @Test
    public void hiddenPhoneRetainsTheUserSuppliedSuccessfulLookupNumberOnMetadataUpdates() throws Exception {
        TdApi.User hidden = user();
        hidden.phoneNumber = "";
        response = hidden;
        manager.searchContactByPhone("+989123456789", (success, contact, message) -> {
            assertTrue(message, success);
            assertEquals("+989123456789", contact.phone);
        });
        hidden.firstName = "نام تازه";
        update(new TdApi.UpdateUser(hidden));
        assertEquals("+989123456789", manager.getTelegramContacts().get(0).phone);
        assertEquals("نام تازه برق", manager.getTelegramContacts().get(0).name);
    }

    @Test
    public void invalidNumberNeverReachesTelegram() {
        manager.searchContactByPhone("abc", (success, contact, message) -> {
            assertFalse(success);
            assertNull(contact);
            assertFalse(message.isEmpty());
        });
        assertTrue(requests.isEmpty());
    }

    @Test
    public void missingAuthorizationIsExplainedWithoutSending() throws Exception {
        setField("currentStep", TelegramClientManager.AuthStep.PHONE);
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertTrue(message, message.contains("وارد"));
        });
        assertTrue(requests.isEmpty());
    }

    @Test
    public void disconnectedTransportIsExplainedWithoutSending() throws Exception {
        setField("connectionReady", false);
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertTrue(message, message.contains("اتصال"));
        });
        assertTrue(requests.isEmpty());
    }

    @Test
    public void unavailablePhoneReportsNotFoundAndPrivacyLimit() {
        response = new TdApi.Error(404, "Not Found");
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertNull(contact);
            assertTrue(message, message.contains("404"));
            assertTrue(message, message.contains("حریم خصوصی"));
        });
        assertTrue(manager.getTelegramContacts().isEmpty());
    }

    @Test
    public void permissionAndRateLimitFailuresAreVisibleAndNumbersAreRedacted() {
        response = new TdApi.Error(403, "PHONE_NUMBER_FORBIDDEN +989123456789");
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertTrue(message, message.contains("403"));
            assertFalse(message, message.contains("989123456789"));
        });
        response = new TdApi.Error(429, "FLOOD_WAIT_90");
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertTrue(message, message.contains("429"));
            assertTrue(message, message.contains("FLOOD_WAIT_90"));
        });
    }

    @Test
    public void unexpectedResponseFailsWithoutInventingContact() {
        response = new TdApi.Ok();
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            assertFalse(success);
            assertNull(contact);
            assertTrue(message, message.contains("معتبر"));
        });
        assertTrue(manager.getTelegramContacts().isEmpty());
    }

    @Test
    public void transportExceptionCompletesOnceWithDiagnosticMessage() {
        doThrow(new IllegalStateException("private phone +989123456789"))
                .when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        AtomicInteger calls = new AtomicInteger();
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            calls.incrementAndGet();
            assertFalse(success);
            assertTrue(message, message.contains("503"));
            assertTrue(message, message.contains("IllegalStateException"));
            assertFalse(message.contains("989123456789"));
        });
        assertEquals(1, calls.get());
    }

    @Test
    public void duplicateResultsCompleteCallbackOnce() {
        doAnswer(call -> {
            Client.ResultHandler handler = call.getArgument(1);
            handler.onResult(user());
            handler.onResult(new TdApi.Error(500, "duplicate"));
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        AtomicInteger calls = new AtomicInteger();
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            calls.incrementAndGet();
            assertTrue(message, success);
        });
        assertEquals(1, calls.get());
    }

    @Test
    public void timedOutSearchCompletesOnceAndIgnoresLateResult() throws Exception {
        Field timerField = TelegramClientManager.class.getDeclaredField("REQUEST_TIMER");
        timerField.setAccessible(true);
        ScheduledThreadPoolExecutor timer = (ScheduledThreadPoolExecutor) timerField.get(null);
        List<Runnable> previousDeadlines = new ArrayList<>(timer.getQueue());
        AtomicReference<Client.ResultHandler> pending = new AtomicReference<>();
        doAnswer(call -> {
            pending.set(call.getArgument(1));
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        AtomicInteger calls = new AtomicInteger();
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            calls.incrementAndGet();
            assertFalse(success);
            assertTrue(message, message.contains("408"));
        });
        List<Runnable> addedDeadlines = new ArrayList<>(timer.getQueue());
        addedDeadlines.removeAll(previousDeadlines);
        assertEquals(1, addedDeadlines.size());
        Runnable deadline = addedDeadlines.get(0);
        deadline.run(); // Execute this request's real deadline without waiting 30 seconds.
        timer.remove(deadline);
        pending.get().onResult(user());
        assertEquals(1, calls.get());
        assertTrue(manager.getTelegramContacts().isEmpty());
    }

    @Test
    public void closingSessionRejectsLateUserAndKeepsItOutOfContacts() throws Exception {
        AtomicReference<Client.ResultHandler> pending = new AtomicReference<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof TdApi.SearchUserByPhoneNumber) pending.set(call.getArgument(1));
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        AtomicInteger calls = new AtomicInteger();
        manager.searchContactByPhone("09123456789", (success, contact, message) -> {
            calls.incrementAndGet();
            assertFalse(success);
            assertTrue(message, message.contains("401"));
        });
        manager.close();
        pending.get().onResult(user());
        assertEquals(1, calls.get());
        assertTrue(manager.getTelegramContacts().isEmpty());
    }

    @Test
    public void contactRefreshReadsActualAccountContactsFromUpdatesWithoutRedundantGetUser() throws Exception {
        TdApi.User contact = user();
        update(new TdApi.UpdateUser(contact));
        response = new TdApi.Users(1, new long[] {contact.id});
        manager.refreshTelegramContacts();
        assertEquals(1, requests.size());
        assertTrue(requests.get(0) instanceof TdApi.GetContacts);
        assertFalse(manager.isLoadingTelegramContacts());
        assertEquals(1, manager.getTelegramContacts().size());
        assertTrue(manager.getContactsLoadMessage().contains("1"));
    }

    @Test
    public void missingContactCacheIsRecoveredAndHiddenPhoneContactsRemainVisible() throws Exception {
        TdApi.User contact = user();
        contact.phoneNumber = "";
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            requests.add(request);
            ((Client.ResultHandler) call.getArgument(1)).onResult(request instanceof TdApi.GetContacts
                    ? new TdApi.Users(1, new long[] {44L}) : contact);
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        CountDownLatch completed = new CountDownLatch(1);
        doAnswer(call -> {
            if (!manager.isLoadingTelegramContacts()) completed.countDown();
            return null;
        }).when(listener).onContactsLoadChanged();
        manager.refreshTelegramContacts();
        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertEquals(2, requests.size());
        assertTrue(requests.get(1) instanceof TdApi.GetUser);
        assertEquals(1, manager.getTelegramContacts().size());
        assertEquals("", manager.getTelegramContacts().get(0).phone);
    }

    @Test
    public void hiddenContactsAndSuppliedLookupNumbersSurviveManagerRecreation() throws Exception {
        tearDown();
        TestPreferences preferences = new TestPreferences();
        manager = new TelegramClientManager(preferences.context, listener);
        setField("client", transport);
        setField("currentStep", TelegramClientManager.AuthStep.READY);
        setField("connectionReady", true);
        TdApi.User hiddenContact = user();
        hiddenContact.isContact = true;
        hiddenContact.phoneNumber = "";
        update(new TdApi.UpdateUser(hiddenContact));
        response = new TdApi.Users(1, new long[] {44L});
        manager.refreshTelegramContacts();
        TdApi.User hiddenLookup = user();
        hiddenLookup.id = 45L;
        hiddenLookup.phoneNumber = "";
        response = hiddenLookup;
        manager.searchContactByPhone("+989223456789", (success, contact, message) -> assertTrue(message, success));
        ((ExecutorService) getField("storageExecutor")).submit(() -> {}).get(3, TimeUnit.SECONDS);
        tearDown();
        manager = new TelegramClientManager(preferences.context, listener);
        assertEquals(2, manager.getTelegramContacts().size());
        assertEquals("", manager.getTelegramContacts().get(0).phone);
        assertEquals("+989223456789", manager.getTelegramContacts().get(1).phone);
        hiddenLookup.firstName = "نام تازه";
        update(new TdApi.UpdateUser(hiddenLookup));
        assertEquals("+989223456789", manager.getTelegramContacts().get(1).phone);
        assertEquals("نام تازه برق", manager.getTelegramContacts().get(1).name);
    }

    @Test
    public void detailedDiscoveryKeepsIdsEvenWhenSameGroupAppearsInLaterStage() throws Exception {
        update(new TdApi.UpdateNewChat(group(-55L)));
        TdApi.Chat privateChat = new TdApi.Chat();
        privateChat.id = 44L;
        privateChat.type = new TdApi.ChatTypePrivate();
        update(new TdApi.UpdateNewChat(privateChat));
        response = chats(-55L, -55L, 44L);
        DetailedResult first = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق ساختمان", first);
        assertTrue(first.completed.await(3, TimeUnit.SECONDS));
        assertTrue(first.message, first.success);
        assertEquals(List.of(-55L), first.ids);
        assertEquals(1, first.newItems);
        DetailedResult second = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق ساختما", second);
        assertTrue(second.completed.await(3, TimeUnit.SECONDS));
        assertTrue(second.success);
        assertEquals(List.of(-55L), second.ids);
        assertEquals(0, second.newItems);
    }

    @Test
    public void groupSearchCollectionReportsARepeatedGroupOnlyOnce() throws Exception {
        Class<?> resultsClass = Class.forName(
                "com.sinicable.telegramelectric.TelegramClientManager$GroupSearchResults");
        Method start = TelegramClientManager.class.getDeclaredMethod(
                "startGroupSearchOperation", TelegramClientManager.DiscoveryCallback.class);
        start.setAccessible(true);
        Object collected = start.invoke(manager, new Object[] {null});
        Method collect = TelegramClientManager.class.getDeclaredMethod("collectGroupSearchResult",
                TdApi.Chat.class, boolean.class, resultsClass);
        collect.setAccessible(true);
        TdApi.Chat repeatedGroup = group(-55L);
        collect.invoke(manager, repeatedGroup, true, collected);
        collect.invoke(manager, repeatedGroup, true, collected);

        Method finish = TelegramClientManager.class.getDeclaredMethod("finishGroupSearch",
                resultsClass, TelegramClientManager.DiscoveryCallback.class, boolean.class, String.class);
        finish.setAccessible(true);
        DetailedResult result = new DetailedResult();
        finish.invoke(null, collected, result, true, "جستجو کامل شد.");

        assertTrue(result.completed.await(3, TimeUnit.SECONDS));
        assertTrue(result.success);
        assertEquals(List.of(-55L), result.ids);
        assertEquals(1, result.newItems);
    }

    @Test
    public void discoveryRecoversMissingChatBeforeRecordingActualGroupIds() throws Exception {
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            requests.add(request);
            ((Client.ResultHandler) call.getArgument(1)).onResult(request instanceof TdApi.GetChat
                    ? group(-55L) : chats(-55L));
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        DetailedResult result = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق", result);
        assertTrue(result.completed.await(3, TimeUnit.SECONDS));
        assertTrue(result.message, result.success);
        assertEquals(List.of(-55L), result.ids);
        assertEquals(2, requests.size());
        assertTrue(requests.get(1) instanceof TdApi.GetChat);
        assertEquals(-55L, manager.getFoundGroups().get(0).id);
    }

    @Test
    public void discoveryUnexpectedResponsesAndTransportFailuresAreFailures() throws Exception {
        response = new TdApi.Ok();
        DetailedResult unexpected = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق", unexpected);
        assertTrue(unexpected.completed.await(3, TimeUnit.SECONDS));
        assertFalse(unexpected.success);
        assertTrue(unexpected.ids.isEmpty());
        doThrow(new IllegalStateException("private query")).when(transport)
                .send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        DetailedResult failed = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق", failed);
        assertTrue(failed.completed.await(3, TimeUnit.SECONDS));
        assertFalse(failed.success);
        assertTrue(failed.message, failed.message.contains("503"));
    }

    @Test
    public void wholeDiscoveryDeadlinePreservesPartialIdsAndStopsFurtherCacheRecovery() throws Exception {
        update(new TdApi.UpdateNewChat(group(-50L)));
        Field timerField = TelegramClientManager.class.getDeclaredField("REQUEST_TIMER");
        timerField.setAccessible(true);
        ScheduledThreadPoolExecutor timer = (ScheduledThreadPoolExecutor) timerField.get(null);
        List<Runnable> previousDeadlines = new ArrayList<>(timer.getQueue());
        AtomicReference<Client.ResultHandler> delayedChat = new AtomicReference<>();
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            requests.add(request);
            Client.ResultHandler handler = call.getArgument(1);
            if (request instanceof TdApi.GetChat) delayedChat.set(handler);
            else handler.onResult(chats(-50L, -55L, -56L));
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        DetailedResult result = new DetailedResult();
        manager.discoverPublicGroupsForReview("برق", result);
        List<Runnable> addedDeadlines = new ArrayList<>(timer.getQueue());
        addedDeadlines.removeAll(previousDeadlines);
        Runnable wholeDeadline = addedDeadlines.stream()
                .filter(task -> ((ScheduledFuture<?>) task).getDelay(TimeUnit.SECONDS) > 40)
                .findFirst().orElseThrow();
        wholeDeadline.run();
        timer.remove(wholeDeadline);
        assertTrue(result.completed.await(3, TimeUnit.SECONDS));
        assertFalse(result.success);
        assertEquals(List.of(-50L), result.ids);
        assertTrue(result.message, result.message.contains("408"));
        delayedChat.get().onResult(group(-55L));
        ((ExecutorService) getField("runtimeExecutor")).submit(() -> {}).get(3, TimeUnit.SECONDS);
        assertEquals("Must not fetch the next missing chat after the operation ended", 2, requests.size());
        assertEquals(1, result.calls.get());
        assertEquals(1, manager.getFoundGroups().size());
    }

    private static final class DetailedResult implements TelegramClientManager.DiscoveryCallback {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        boolean success;
        int newItems;
        String message;
        List<Long> ids;

        @Override
        public void onResult(boolean success, int newItems, int totalItems, String message) {
            fail("Discovery must provide the detailed result IDs");
        }

        @Override
        public void onDetailedResult(boolean success, int newItems, int totalItems, String message, List<Long> ids) {
            this.success = success;
            this.newItems = newItems;
            this.message = message;
            this.ids = ids;
            assertEquals(ids.size(), totalItems);
            calls.incrementAndGet();
            completed.countDown();
        }
    }

    private static TdApi.Chat group(long id) {
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = id;
        chat.title = "گروه برق";
        chat.type = new TdApi.ChatTypeSupergroup();
        return chat;
    }

    private static TdApi.Chats chats(long... ids) {
        TdApi.Chats chats = new TdApi.Chats();
        chats.chatIds = ids;
        return chats;
    }

    private static TdApi.User user() {
        TdApi.User user = new TdApi.User();
        user.id = 44L;
        user.firstName = "لاله";
        user.lastName = "برق";
        user.phoneNumber = "989123456789";
        return user;
    }

    private void update(TdApi.Object update) throws Exception {
        Method method = TelegramClientManager.class.getDeclaredMethod("onUpdate", TdApi.Object.class);
        method.setAccessible(true);
        method.invoke(manager, update);
    }

    private Object getField(String name) throws Exception {
        Field field = TelegramClientManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = TelegramClientManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
