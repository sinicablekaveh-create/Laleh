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

    @Test
    public void equivalentInternationalFormatsProduceOneDeepLinkTarget() {
        for (String input : new String[] {
                "+1 (202) 555-0100", "0012025550100", "12025550100", "+12025550100"
        }) {
            assertEquals(input, "+12025550100", TelegramPhoneTarget.normalize(input));
        }
    }

    @Test
    public void localizedDigitsAndDirectionMarksProduceAsciiTarget() {
        for (String input : new String[] {
                "\u200f۰۹۱۲ ۳۴۵ ۶۷۸۹\u200e", "\u061c+٩٨٩١٢٣٤٥٦٧٨٩", "9123456789"
        }) {
            assertEquals(input, "+989123456789", TelegramPhoneTarget.normalize(input));
        }
    }

    @Test
    public void acceptedLengthBoundariesKeepAllDigits() {
        assertEquals("+1234567", TelegramPhoneTarget.normalize("+1234567"));
        assertEquals("+123456789012345", TelegramPhoneTarget.normalize("+123456789012345"));
    }

    @Test
    public void invalidInputReturnsEmptyInsteadOfThrowing() {
        for (String input : new String[] {
                null, "", " \t\n", "+", "() - .", "+123456", "+1234567890123456",
                "+0123456789", "0009123456789", "++12025550100", "1+2025550100",
                "call +12025550100", "+12025550100 ext 5", "+12025550100&domain=other"
        }) {
            assertEquals(String.valueOf(input), "", TelegramPhoneTarget.normalize(input));
        }
    }
}
