package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramAuthenticationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private TelegramClientManager manager;
    private TelegramClientManager.Listener listener;
    private Client transport;
    private Client.ResultHandler updates;
    private Client.ExceptionHandler updateErrors;
    private Client.ResultHandler authResponse;
    private MockedStatic<Client> tdlib;
    private Field loaded;
    private boolean previouslyLoaded;

    @Before
    public void setUp() throws Exception {
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getFilesDir()).thenReturn(folder.getRoot());
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs);
        when(prefs.getString(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(prefs.edit()).thenReturn(mock(SharedPreferences.Editor.class, RETURNS_SELF));
        listener = mock(TelegramClientManager.Listener.class);
        manager = new TelegramClientManager(context, listener);
        Field executor = TelegramClientManager.class.getDeclaredField("runtimeExecutor");
        executor.setAccessible(true);
        ((ExecutorService) executor.get(manager)).shutdownNow();
        ExecutorService inline = mock(ExecutorService.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(inline).execute(any(Runnable.class));
        executor.set(manager, inline);
        loaded = TelegramClientManager.class.getDeclaredField("tdjniLoaded");
        loaded.setAccessible(true);
        previouslyLoaded = loaded.getBoolean(null);
        loaded.setBoolean(null, true);
        transport = mock(Client.class);
        doAnswer(call -> { authResponse = call.getArgument(1); return null; })
                .when(transport).send(any(TdApi.Function.class), any(Client.ResultHandler.class));
        tdlib = mockStatic(Client.class);
        tdlib.when(() -> Client.create(any(), any(), any())).thenAnswer(call -> {
            updates = call.getArgument(0);
            updateErrors = call.getArgument(1);
            return transport;
        });
    }

    @After
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
            for (String name : new String[] {"runtimeExecutor", "storageExecutor"}) {
                Field field = TelegramClientManager.class.getDeclaredField(name);
                field.setAccessible(true);
                ((ExecutorService) field.get(manager)).shutdownNow();
            }
        }
        if (tdlib != null) tdlib.close();
        if (loaded != null) loaded.setBoolean(null, previouslyLoaded);
    }

    @Test
    public void initialParametersUpdateBeforeCreateReturnsIsDeliveredToCreatedClient() {
        tdlib.when(() -> Client.create(any(), any(), any())).thenAnswer(call -> {
            Client.ResultHandler handler = call.getArgument(0);
            handler.onResult(new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateWaitTdlibParameters()));
            return transport;
        });
        manager.start(123, "test-api-hash");
        ArgumentCaptor<TdApi.SetTdlibParameters> request = ArgumentCaptor.forClass(TdApi.SetTdlibParameters.class);
        verify(transport).send(request.capture(), any(Client.ResultHandler.class));
        assertEquals(123, request.getValue().apiId);
        assertEquals("test-api-hash", request.getValue().apiHash);
        verify(listener, never()).onError(anyString());
    }

    @Test
    public void twoFactorPasswordPreservesLeadingAndTrailingSpaces() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitPassword());
        manager.submitAuthValue("  valid password  ");
        ArgumentCaptor<TdApi.CheckAuthenticationPassword> request = ArgumentCaptor.forClass(TdApi.CheckAuthenticationPassword.class);
        verify(transport).send(request.capture(), any(Client.ResultHandler.class));
        assertEquals("  valid password  ", request.getValue().password);
    }

    @Test
    public void codeStillTrimsAccidentalWhitespace() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitCode());
        manager.submitAuthValue(" 12345 ");
        ArgumentCaptor<TdApi.CheckAuthenticationCode> request = ArgumentCaptor.forClass(TdApi.CheckAuthenticationCode.class);
        verify(transport).send(request.capture(), any(Client.ResultHandler.class));
        assertEquals("12345", request.getValue().code);
    }

    @Test
    public void lateCodeErrorCannotOverwritePasswordStep() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitCode());
        manager.submitAuthValue("12345");
        Client.ResultHandler previous = authResponse;
        authorize(new TdApi.AuthorizationStateWaitPassword());
        clearInvocations(listener);
        previous.onResult(new TdApi.Error(400, "PHONE_CODE_INVALID"));
        verify(listener, never()).onError(anyString());
        assertEquals(TelegramClientManager.AuthStep.PASSWORD, manager.getCurrentStep());
    }

    @Test
    public void lateAuthErrorAfterCloseIsIgnored() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitCode());
        manager.submitAuthValue("12345");
        Client.ResultHandler previous = authResponse;
        manager.close();
        clearInvocations(listener);
        previous.onResult(new TdApi.Error(400, "PHONE_CODE_INVALID"));
        verify(listener, never()).onError(anyString());
    }

    @Test
    public void currentAuthErrorRemainsVisibleForRetry() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitCode());
        manager.submitAuthValue("12345");
        authResponse.onResult(new TdApi.Error(400, "PHONE_CODE_INVALID"));
        verify(listener).onError("Telegram 400: PHONE_CODE_INVALID");
        assertEquals(TelegramClientManager.AuthStep.CODE, manager.getCurrentStep());
    }

    @Test
    public void repeatStartWithSameCredentialsKeepsCurrentLoginStep() {
        manager.start(123, "test-api-hash");
        authorize(new TdApi.AuthorizationStateWaitCode());
        manager.start(123, "test-api-hash");
        tdlib.verify(() -> Client.create(any(), any(), any()), times(1));
        verify(transport, never()).send(isA(TdApi.Close.class), any(Client.ResultHandler.class));
        assertEquals(TelegramClientManager.AuthStep.CODE, manager.getCurrentStep());
    }

    @Test
    public void nativeCallbackErrorAfterCloseIsIgnored() {
        manager.start(123, "test-api-hash");
        Client.ExceptionHandler previous = updateErrors;
        manager.close();
        clearInvocations(listener);
        previous.onException(new IllegalStateException("obsolete callback"));
        verify(listener, never()).onError(anyString());
    }

    @Test
    public void failureWhilePublishingClientClosesItAndResetsConnectionStatus() {
        doThrow(new IllegalStateException("callback failure"))
                .when(listener).onAuthStep(eq(TelegramClientManager.AuthStep.PHONE), anyString());
        tdlib.when(() -> Client.create(any(), any(), any())).thenAnswer(call -> {
            Client.ResultHandler handler = call.getArgument(0);
            handler.onResult(new TdApi.UpdateConnectionState(new TdApi.ConnectionStateReady()));
            handler.onResult(new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateWaitPhoneNumber()));
            return transport;
        });
        manager.start(123, "test-api-hash");
        verify(transport).send(isA(TdApi.Close.class), any(Client.ResultHandler.class));
        assertEquals(TelegramClientManager.AuthStep.IDLE, manager.getCurrentStep());
        clearInvocations(listener);
        manager.emitCurrentConnectionStatus();
        verify(listener).onConnectionStatus(contains("ناموفق"), eq(false));
    }

    @Test
    public void initializationFailureAfterCloseCannotReportObsoleteError() {
        tdlib.when(() -> Client.create(any(), any(), any())).thenAnswer(call -> {
            manager.close();
            clearInvocations(listener);
            throw new IllegalStateException("obsolete initialization");
        });
        manager.start(123, "test-api-hash");
        verify(listener, never()).onError(anyString());
        assertEquals(TelegramClientManager.AuthStep.IDLE, manager.getCurrentStep());
    }

    private void authorize(TdApi.AuthorizationState state) {
        updates.onResult(new TdApi.UpdateAuthorizationState(state));
    }
}
