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

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TelegramAppConnectorTest {
    private Context context;
    private PackageManager packageManager;

    @Before
    public void setUp() {
        context = mock(Context.class);
        packageManager = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(packageManager);
    }

    @Test
    public void domesticPhoneIsNormalizedBeforeLaunchingSelectedApp() {
        assertPhoneLaunch("0912 345 6789", "+989123456789", "org.telegram.messenger");
    }

    @Test
    public void localizedPhoneIsNormalizedBeforeLaunchingSelectedApp() {
        assertPhoneLaunch("\u200f۰۹۱۲ ۳۴۵ ۶۷۸۹\u200e", "+989123456789", "org.thunderdog.challegram");
    }

    @Test
    public void internationalPhoneCanLaunchWithoutSelectedPackage() {
        assertPhoneLaunch("0012025550100", "+12025550100", null);
        assertPhoneLaunch("+1 (202) 555-0100", "+12025550100", "");
    }

    @Test
    public void invalidPhoneNeverBuildsOrLaunchesAnIntent() {
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class)) {
            for (String phone : new String[] {
                    null, "", "  ", "123", "02025550123", "++12025550100",
                    "+12025550100&domain=other", "+1234567890123456"
            }) {
                assertFalse(String.valueOf(phone),
                        TelegramAppConnector.openPhone(context, phone, "org.telegram.messenger"));
            }

            assertTrue(intents.constructed().isEmpty());
            uris.verifyNoInteractions();
            verifyNoInteractions(context, packageManager);
        }
    }

    @Test
    public void normalizedPhoneWithNoActivityHandlerDoesNotLaunch() {
        Uri target = mock(Uri.class);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                     (intent, construction) -> assertEquals(
                             Arrays.asList(Intent.ACTION_VIEW, target), construction.arguments()))) {
            uris.when(() -> Uri.parse("tg://resolve?phone=+989123456789")).thenReturn(target);

            assertFalse(TelegramAppConnector.openPhone(context, "0912 345 6789", "org.telegram.messenger"));

            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            verify(intent).setPackage("org.telegram.messenger");
            verify(intent).resolveActivity(packageManager);
            verify(context, never()).startActivity(any(Intent.class));
        }
    }

    private void assertPhoneLaunch(String input, String normalized, String selectedPackage) {
        Uri target = mock(Uri.class);
        ComponentName activity = mock(ComponentName.class);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, construction) -> {
                 assertEquals(Arrays.asList(Intent.ACTION_VIEW, target), construction.arguments());
                 when(intent.resolveActivity(packageManager)).thenReturn(activity);
             })) {
            uris.when(() -> Uri.parse("tg://resolve?phone=" + normalized)).thenReturn(target);

            assertTrue(TelegramAppConnector.openPhone(context, input, selectedPackage));

            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            uris.verify(() -> Uri.parse("tg://resolve?phone=" + normalized));
            if (selectedPackage == null || selectedPackage.isEmpty()) {
                verify(intent, never()).setPackage(any());
            } else {
                verify(intent).setPackage(selectedPackage);
            }
            verify(intent).resolveActivity(packageManager);
            verify(context).startActivity(intent);
        }
    }
}
