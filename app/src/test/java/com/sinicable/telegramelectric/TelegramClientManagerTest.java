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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramClientManagerTest {
    private TelegramClientManager manager;
    private TelegramClientManager.Listener listener;

    @Before
    public void setUp() {
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs);
        when(prefs.getString(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(prefs.edit()).thenReturn(editor);
        listener = mock(TelegramClientManager.Listener.class);
        manager = new TelegramClientManager(context, listener);
    }

    @After
    public void stopTestExecutors() throws Exception {
        for (String name : new String[] {"runtimeExecutor", "storageExecutor"}) {
            Field field = TelegramClientManager.class.getDeclaredField(name);
            field.setAccessible(true);
            ((ExecutorService) field.get(manager)).shutdownNow();
        }
    }

    @Test
    public void leftGroupIsNotAnAccountGroup() throws Exception {
        inspect(chat(), metadata(new TdApi.ChatMemberStatusLeft()));
        assertNull(manager.getTargetGroup(-1005L));
    }

    @Test
    public void bannedGroupIsNotAnAccountGroup() throws Exception {
        inspect(chat(), metadata(new TdApi.ChatMemberStatusBanned()));
        assertNull(manager.getTargetGroup(-1005L));
    }

    @Test
    public void ownerWhoHasLeftIsNotAnAccountGroup() throws Exception {
        TdApi.ChatMemberStatusCreator status = new TdApi.ChatMemberStatusCreator();
        status.isMember = false;
        inspect(chat(), metadata(status));
        assertNull(manager.getTargetGroup(-1005L));
    }

    @Test
    public void restrictedNonMemberIsNotAnAccountGroup() throws Exception {
        TdApi.ChatMemberStatusRestricted status = new TdApi.ChatMemberStatusRestricted();
        status.isMember = false;
        inspect(chat(), metadata(status));
        assertNull(manager.getTargetGroup(-1005L));
    }

    @Test
    public void ordinaryMemberCanSendWhenGroupAllowsText() throws Exception {
        inspect(chat(), metadata(new TdApi.ChatMemberStatusMember()));
        assertNotNull(manager.getTargetGroup(-1005L));
        assertTrue(manager.getTargetGroup(-1005L).canSend);
    }

    @Test
    public void metadataReceivedBeforeChatPopulatesAccountGroups() throws Exception {
        update(new TdApi.UpdateSupergroup(metadata(new TdApi.ChatMemberStatusAdministrator())));
        update(new TdApi.UpdateNewChat(chat()));
        assertNotNull(manager.getTargetGroup(-1005L));
        assertEquals("گروه برق", manager.getTargetGroup(-1005L).title);
        assertTrue(manager.getTargetGroup(-1005L).canSend);
    }

    @Test
    public void leavingGroupRemovesItImmediately() throws Exception {
        TdApi.Chat chat = chat();
        update(new TdApi.UpdateNewChat(chat));
        inspect(chat, metadata(new TdApi.ChatMemberStatusAdministrator()));
        assertNotNull(manager.getTargetGroup(chat.id));
        update(new TdApi.UpdateSupergroup(metadata(new TdApi.ChatMemberStatusLeft())));
        assertNull(manager.getTargetGroup(chat.id));
    }

    @Test
    public void adminDemotionKeepsSendingWhenMemberHasPermission() throws Exception {
        TdApi.Chat chat = chat();
        update(new TdApi.UpdateNewChat(chat));
        inspect(chat, metadata(new TdApi.ChatMemberStatusAdministrator()));
        update(new TdApi.UpdateSupergroup(metadata(new TdApi.ChatMemberStatusMember())));
        assertTrue(manager.getTargetGroup(chat.id).canSend);
    }

    @Test
    public void mutedMemberRemainsVisibleButCannotSend() throws Exception {
        TdApi.Chat chat = chat();
        chat.permissions.canSendBasicMessages = false;
        inspect(chat, metadata(new TdApi.ChatMemberStatusMember()));
        assertNotNull(manager.getTargetGroup(chat.id));
        assertFalse(manager.getTargetGroup(chat.id).canSend);
    }

    @Test
    public void restrictedMemberNeedsPersonalPermissionAsWellAsGroupPermission() throws Exception {
        TdApi.ChatMemberStatusRestricted status = new TdApi.ChatMemberStatusRestricted();
        status.isMember = true;
        status.permissions = new TdApi.ChatPermissions();
        inspect(chat(), metadata(status));
        assertFalse(manager.getTargetGroup(-1005L).canSend);
        status.permissions.canSendBasicMessages = true;
        inspect(chat(), metadata(status));
        assertTrue(manager.getTargetGroup(-1005L).canSend);
    }

    @Test
    public void changingChatPermissionsImmediatelyDisablesAndEnablesMemberSending() throws Exception {
        TdApi.Chat chat = chat();
        update(new TdApi.UpdateSupergroup(metadata(new TdApi.ChatMemberStatusMember())));
        update(new TdApi.UpdateNewChat(chat));
        assertTrue(manager.getTargetGroup(chat.id).canSend);
        TdApi.UpdateChatPermissions change = new TdApi.UpdateChatPermissions();
        change.chatId = chat.id;
        change.permissions = new TdApi.ChatPermissions();
        update(change);
        assertFalse(manager.getTargetGroup(chat.id).canSend);
        change.permissions.canSendBasicMessages = true;
        update(change);
        assertTrue(manager.getTargetGroup(chat.id).canSend);
    }

    @Test
    public void memberCannotSendPhotoWhenPhotoPermissionIsDisabled() throws Exception {
        TdApi.Chat chat = chat();
        chat.permissions.canSendPhotos = false;
        update(new TdApi.UpdateSupergroup(metadata(new TdApi.ChatMemberStatusMember())));
        update(new TdApi.UpdateNewChat(chat));
        List<TdApi.Function> requests = new ArrayList<>();
        Client transport = mock(Client.class);
        doAnswer(call -> {
            requests.add(call.getArgument(0));
            ((Client.ResultHandler) call.getArgument(1)).onResult(new TdApi.Message());
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        setField("client", transport);
        setField("currentStep", TelegramClientManager.AuthStep.READY);
        Path photo = Files.createTempFile("telegram-test-photo", ".jpg");
        try {
            Files.write(photo, new byte[] {1, 2, 3, 4});
            boolean[] success = {true};
            manager.sendPhotoToChat(chat.id, photo.toString(), "برق", (ok, message) -> success[0] = ok);
            assertFalse(success[0]);
            assertTrue(requests.isEmpty());
        } finally {
            Files.deleteIfExists(photo);
        }
    }

    @Test
    public void authorizationLoadsEveryPageOfMainAndArchiveWithoutGetChatRequests() throws Exception {
        Client transport = mock(Client.class);
        ConcurrentHashMap<String, Integer> pages = new ConcurrentHashMap<>();
        List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch finished = new CountDownLatch(2);
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            requests.add(request.getClass().getSimpleName());
            Client.ResultHandler handler = call.getArgument(1);
            if (request instanceof TdApi.LoadChats) {
                TdApi.LoadChats load = (TdApi.LoadChats) request;
                String list = load.chatList instanceof TdApi.ChatListArchive ? "archive" : "main";
                int page = pages.merge(list, 1, Integer::sum);
                if (page == 1) {
                    long groupId = list.equals("archive") ? 6L : 5L;
                    TdApi.Chat chat = chat();
                    chat.id = -1000L - groupId;
                    ((TdApi.ChatTypeSupergroup) chat.type).supergroupId = groupId;
                    TdApi.Supergroup meta = metadata(new TdApi.ChatMemberStatusMember());
                    meta.id = groupId;
                    update(new TdApi.UpdateSupergroup(meta));
                    update(new TdApi.UpdateNewChat(chat));
                    handler.onResult(new TdApi.Ok());
                } else {
                    handler.onResult(new TdApi.Error(404, "Not Found"));
                    finished.countDown();
                }
            } else {
                handler.onResult(new TdApi.Ok());
            }
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        setField("client", transport);
        TdApi.UpdateAuthorizationState ready = new TdApi.UpdateAuthorizationState();
        ready.authorizationState = new TdApi.AuthorizationStateReady();
        update(ready);
        assertTrue("Main and archive chat lists were not loaded", finished.await(3, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(2), pages.get("main"));
        assertEquals(Integer.valueOf(2), pages.get("archive"));
        assertEquals(2, manager.getTargetGroups().size());
        assertTrue(manager.getTargetGroup(-1006L).canSend);
        assertTrue(manager.getFoundGroups().isEmpty());
        assertFalse(requests.contains("GetChat"));
    }

    @Test
    public void unchangedGroupDoesNotTriggerAnotherScreenUpdate() throws Exception {
        TdApi.Chat chat = chat();
        TdApi.Supergroup meta = metadata(new TdApi.ChatMemberStatusAdministrator());
        inspect(chat, meta);
        clearInvocations(listener);
        inspect(chat, meta);
        verify(listener, never()).onTargetGroupChanged(anyLong());
    }

    @Test
    public void unchangedTitleDoesNotTriggerAnotherScreenUpdate() throws Exception {
        TdApi.Chat chat = chat();
        update(new TdApi.UpdateNewChat(chat));
        inspect(chat, metadata(new TdApi.ChatMemberStatusAdministrator()));
        Method store = TelegramClientManager.class.getDeclaredMethod(
                "storeFoundGroup", TelegramClientManager.GroupInfo.class);
        store.setAccessible(true);
        store.invoke(manager, new TelegramClientManager.GroupInfo(
                1, chat.id, chat.title, "https://t.me/electric", 40, "مدیر گروه ✅", false, true));
        clearInvocations(listener);
        TdApi.UpdateChatTitle title = new TdApi.UpdateChatTitle();
        title.chatId = chat.id;
        title.title = chat.title;
        update(title);
        verify(listener, never()).onTargetGroupChanged(anyLong());
        verify(listener, never()).onFoundGroupsChanged();
    }

    @Test
    public void publicSearchReadsResultsFromCacheWithoutPerChatRequests() throws Exception {
        List<TdApi.Function> requests = prepareSearchTransport();
        int[] counts = {-1, -1};
        manager.discoverPublicGroupsForReview("برق", (success, fresh, total, message) -> {
            assertTrue(success);
            counts[0] = fresh;
            counts[1] = total;
        });
        assertArrayEquals(new int[] {1, 1}, counts);
        assertEquals(1, manager.getFoundGroups().size());
        assertEquals(1, requests.size());
        assertEquals("SearchPublicChats", requests.get(0).getClass().getSimpleName());
    }

    @Test
    public void accountSearchReadsResultsFromCacheWithoutPerChatRequests() throws Exception {
        List<TdApi.Function> requests = prepareSearchTransport();
        boolean[] completed = {false};
        manager.searchKnownGroups("برق", (success, fresh, total, message) -> {
            assertTrue(success);
            assertEquals(1, total);
            completed[0] = true;
        });
        assertTrue(completed[0]);
        assertNotNull(manager.getTargetGroup(-1005L));
        assertEquals(1, requests.size());
        assertTrue(requests.get(0) instanceof TdApi.SearchChats);
    }

    private List<TdApi.Function> prepareSearchTransport() throws Exception {
        TdApi.Chat chat = chat();
        TdApi.Supergroup meta = metadata(new TdApi.ChatMemberStatusAdministrator());
        update(new TdApi.UpdateSupergroup(meta));
        update(new TdApi.UpdateNewChat(chat));
        Client transport = mock(Client.class);
        List<TdApi.Function> requests = new ArrayList<>();
        doAnswer(call -> {
            TdApi.Function request = call.getArgument(0);
            Client.ResultHandler handler = call.getArgument(1);
            requests.add(request);
            if (request instanceof TdApi.GetChat) {
                handler.onResult(chat);
            } else if (request instanceof TdApi.GetSupergroup) {
                handler.onResult(meta);
            } else {
                TdApi.Chats result = new TdApi.Chats();
                result.chatIds = new long[] {chat.id};
                handler.onResult(result);
            }
            return null;
        }).when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        setField("client", transport);
        setField("currentStep", TelegramClientManager.AuthStep.READY);
        return requests;
    }

    private static TdApi.Chat chat() {
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = -1005L;
        chat.title = "گروه برق";
        TdApi.ChatTypeSupergroup type = new TdApi.ChatTypeSupergroup();
        type.supergroupId = 5L;
        type.isChannel = false;
        chat.type = type;
        chat.permissions = new TdApi.ChatPermissions();
        chat.permissions.canSendBasicMessages = true;
        chat.permissions.canSendPhotos = true;
        return chat;
    }

    private static TdApi.Supergroup metadata(TdApi.ChatMemberStatus status) {
        TdApi.Supergroup group = new TdApi.Supergroup();
        group.id = 5L;
        group.status = status;
        group.memberCount = 40;
        return group;
    }

    private void inspect(TdApi.Chat chat, TdApi.Supergroup group) throws Exception {
        Method method = TelegramClientManager.class.getDeclaredMethod(
                "updateTargetFromMeta", TdApi.Chat.class, Object.class);
        method.setAccessible(true);
        method.invoke(manager, chat, group);
    }

    private void update(TdApi.Object update) throws Exception {
        Method method = TelegramClientManager.class.getDeclaredMethod("onUpdate", TdApi.Object.class);
        method.setAccessible(true);
        method.invoke(manager, update);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = TelegramClientManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
