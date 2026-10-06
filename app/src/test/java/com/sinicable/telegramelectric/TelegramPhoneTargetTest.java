package com.sinicable.telegramelectric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TelegramPhoneTargetTest {
    @Test
    public void domesticIranianMobileIsNormalizedForTelegramDeepLink() {
        assertEquals(
                "+989123456789",
                TelegramPhoneTarget.normalize("0912 345 6789")
        );
    }

    @Test
    public void explicitForeignCountryCodeIsPreserved() {
        assertEquals(
                "+994501234567",
                TelegramPhoneTarget.normalize("994501234567")
        );
    }

    @Test
    public void ambiguousDomesticNumberIsRejected() {
        assertEquals("", TelegramPhoneTarget.normalize("02025550123"));
    }
}
