package com.sinicable.telegramelectric;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

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
        try (MockedConstruction<Uri.Builder> builders = mockConstruction(Uri.Builder.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class)) {
            for (String phone : new String[] {
                    null, "", "  ", "123", "02025550123", "++12025550100",
                    "+12025550100&domain=other", "+1234567890123456"
            }) {
                assertFalse(String.valueOf(phone),
                        TelegramAppConnector.openPhone(context, phone, "org.telegram.messenger"));
            }

            assertTrue(intents.constructed().isEmpty());
            assertTrue(builders.constructed().isEmpty());
            verifyNoInteractions(context, packageManager);
        }
    }

    @Test
    public void normalizedPhoneWithNoActivityHandlerDoesNotLaunch() {
        Uri target = mock(Uri.class);
        try (MockedConstruction<Uri.Builder> builders = mockResolveBuilder("phone", "+989123456789", target);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                     (intent, construction) -> assertEquals(
                             Arrays.asList(Intent.ACTION_VIEW, target), construction.arguments()))) {

            assertFalse(TelegramAppConnector.openPhone(context, "0912 345 6789", "org.telegram.messenger"));

            assertEquals(1, builders.constructed().size());
            verifyResolveBuilder(builders.constructed().get(0), "phone", "+989123456789");
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
        try (MockedConstruction<Uri.Builder> builders = mockResolveBuilder("phone", normalized, target);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, construction) -> {
                 assertEquals(Arrays.asList(Intent.ACTION_VIEW, target), construction.arguments());
                 when(intent.resolveActivity(packageManager)).thenReturn(activity);
             })) {

            assertTrue(TelegramAppConnector.openPhone(context, input, selectedPackage));

            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            assertEquals(1, builders.constructed().size());
            verifyResolveBuilder(builders.constructed().get(0), "phone", normalized);
            if (selectedPackage == null || selectedPackage.isEmpty()) {
                verify(intent, never()).setPackage(any());
            } else {
                verify(intent).setPackage(selectedPackage);
            }
            verify(intent).resolveActivity(packageManager);
            verify(context).startActivity(intent);
        }
    }

    @Test
    public void usernameIsPassedAsOneEncodedQueryParameter() {
        String username = "example&phone=+989123456789";
        Uri target = mock(Uri.class);
        ComponentName activity = mock(ComponentName.class);
        try (MockedConstruction<Uri.Builder> builders = mockResolveBuilder("domain", username, target);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, construction) -> {
                 assertEquals(Arrays.asList(Intent.ACTION_VIEW, target), construction.arguments());
                 when(intent.resolveActivity(packageManager)).thenReturn(activity);
             })) {
            assertTrue(TelegramAppConnector.openUsername(context, username, "org.telegram.messenger"));
            assertEquals(1, builders.constructed().size());
            verifyResolveBuilder(builders.constructed().get(0), "domain", username);
            Intent intent = intents.constructed().get(0);
            verify(intent).setPackage("org.telegram.messenger");
            verify(intent).resolveActivity(packageManager);
            verify(context).startActivity(intent);
        }
    }

    private MockedConstruction<Uri.Builder> mockResolveBuilder(String parameter, String value, Uri target) {
        return mockConstruction(Uri.Builder.class, (builder, construction) -> {
            when(builder.scheme("tg")).thenReturn(builder);
            when(builder.authority("resolve")).thenReturn(builder);
            when(builder.appendQueryParameter(parameter, value)).thenReturn(builder);
            when(builder.build()).thenReturn(target);
        });
    }

    private void verifyResolveBuilder(Uri.Builder builder, String parameter, String value) {
        verify(builder).scheme("tg");
        verify(builder).authority("resolve");
        verify(builder).appendQueryParameter(parameter, value);
        verify(builder).build();
    }
}
