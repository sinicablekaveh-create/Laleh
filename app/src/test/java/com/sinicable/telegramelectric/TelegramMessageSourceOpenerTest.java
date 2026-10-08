package com.sinicable.telegramelectric;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramMessageSourceOpenerTest {
    private Context context;
    private PackageManager packageManager;

    @Before
    public void setUp() {
        context = mock(Context.class);
        packageManager = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(packageManager);
    }

    @Test
    public void supergroupLinkUsesBarePeerIdAndPreservesLongMessageId() {
        assertMessageLaunch(-1001234567890L, 12345678901L,
                "tg://openmessage?chat_id=1234567890&message_id=12345678901");
    }

    @Test
    public void basicGroupLinkUsesPositivePeerId() {
        assertMessageLaunch(-123456789L, 99L,
                "tg://openmessage?chat_id=123456789&message_id=99");
    }

    @Test
    public void userLinkPreservesPeerId() {
        assertMessageLaunch(42L, 99L, "tg://openmessage?chat_id=42&message_id=99");
    }

    @Test
    public void unavailableActivityReturnsFalseWithoutLaunching() {
        Uri target = mock(Uri.class);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class)) {
            uris.when(() -> Uri.parse("tg://openmessage?chat_id=42&message_id=99")).thenReturn(target);

            assertFalse(TelegramMessageSourceOpener.openMessage(context, -1000000000042L, 99L));

            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            verify(intent).setData(target);
            verify(intent).resolveActivity(packageManager);
            verify(context, never()).startActivity(any(Intent.class));
        }
    }

    private void assertMessageLaunch(long chatId, long messageId, String expectedLink) {
        Uri target = mock(Uri.class);
        ComponentName activity = mock(ComponentName.class);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, construction) -> {
                 assertEquals(Collections.singletonList(Intent.ACTION_VIEW), construction.arguments());
                 when(intent.resolveActivity(packageManager)).thenReturn(activity);
             })) {
            uris.when(() -> Uri.parse(expectedLink)).thenReturn(target);

            assertTrue(TelegramMessageSourceOpener.openMessage(context, chatId, messageId));

            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            uris.verify(() -> Uri.parse(expectedLink));
            verify(intent).setData(target);
            verify(intent).resolveActivity(packageManager);
            verify(context).startActivity(intent);
        }
    }
}
