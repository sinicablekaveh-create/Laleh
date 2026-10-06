package com.sinicable.telegramelectric;

import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CentralCorePanelTest {
    private CentralCorePanel panel;
    private TelegramClientManager telegram;
    private EditText input;
    private Button searchButton;
    private TextView status;
    private TextView result;
    private final ArrayDeque<Runnable> ui = new ArrayDeque<>();

    @Before
    public void setUp() throws Exception {
        panel = mock(CentralCorePanel.class, CALLS_REAL_METHODS);
        telegram = mock(TelegramClientManager.class);
        input = mock(EditText.class);
        searchButton = mock(Button.class);
        status = mock(TextView.class);
        result = mock(TextView.class);
        setField("telegram", telegram);
        setField("contactPhoneInput", input);
        setField("contactSearchButton", searchButton);
        setField("contactSearchStatusText", status);
        setField("contactSearchResultText", result);
        setField("contactsText", mock(TextView.class));
        setField("summaryText", mock(TextView.class));
        setField("startButton", mock(Button.class));
        setField("stopButton", mock(Button.class));
        setField("core", mock(CentralCore.class));
        when(telegram.getTelegramContacts()).thenReturn(List.of());
        doAnswer(call -> {
            ui.add(call.getArgument(0));
            return true;
        }).when(panel).post(any(Runnable.class));
        doReturn(true).when(panel).removeCallbacks(nullable(Runnable.class));
    }

    @Test
    public void successfulPhoneLookupDisplaysServerResultOnUiThread() throws Exception {
        setInput("۰۹۱۲۳۴۵۶۷۸۹");
        invokeSearch();
        TelegramClientManager.PhoneSearchCallback callback = captureCallback();
        callback.onResult(true,
                new TelegramClientManager.ContactInfo(1, 42L, "مخاطب پیدا شده", "+989123456789"),
                "مخاطب پیدا شد.");

        verify(searchButton).setEnabled(false);
        verify(result, never()).setText(contains("مخاطب پیدا شده"));
        ui.remove().run();
        verify(result).setText(contains("مخاطب پیدا شده"));
        verify(result).setText(contains("+989123456789"));
        verify(status).setText("مخاطب پیدا شد.");
        verify(searchButton).setEnabled(true);
    }

    @Test
    public void telegramFailureIsVisibleAndAllowsRetry() throws Exception {
        setInput("+989123456789");
        invokeSearch();
        captureCallback().onResult(false, null, "خطای تلگرام: 429 FLOOD_WAIT_30");
        ui.remove().run();

        verify(status).setText("خطای تلگرام: 429 FLOOD_WAIT_30");
        verify(searchButton).setEnabled(true);
        verify(result).setText("");
    }

    @Test
    public void detachedActivityIgnoresLateLookupCompletion() throws Exception {
        setInput("+989123456789");
        invokeSearch();
        TelegramClientManager.PhoneSearchCallback callback = captureCallback();
        panel.detachUiCallbacks();
        callback.onResult(true,
                new TelegramClientManager.ContactInfo(42L, "late result", "+989123456789"),
                "late status");
        ui.remove().run();

        verify(status, never()).setText("late status");
        verify(result, never()).setText(contains("late result"));
        verify(searchButton, never()).setEnabled(true);
    }

    @Test
    public void emptyInputShowsValidationWithoutSendingRequest() throws Exception {
        setInput("  ");
        invokeSearch();

        verify(input).setError(anyString());
        verify(telegram, never()).searchContactByPhone(anyString(), any());
    }

    private TelegramClientManager.PhoneSearchCallback captureCallback() {
        ArgumentCaptor<TelegramClientManager.PhoneSearchCallback> callback =
                ArgumentCaptor.forClass(TelegramClientManager.PhoneSearchCallback.class);
        verify(telegram).searchContactByPhone(anyString(), callback.capture());
        return callback.getValue();
    }

    private void setInput(String value) {
        Editable editable = mock(Editable.class);
        when(editable.toString()).thenReturn(value);
        when(input.getText()).thenReturn(editable);
    }

    private void invokeSearch() throws Exception {
        Method method = CentralCorePanel.class.getDeclaredMethod("searchContact");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = CentralCorePanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }
}
