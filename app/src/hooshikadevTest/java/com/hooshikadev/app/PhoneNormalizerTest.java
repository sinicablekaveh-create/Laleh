package com.hooshikadev.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PhoneNormalizerTest {
    @Test public void normalizesIranianFormatsAndDigits() {
        assertEquals("+989121234567", PhoneNormalizer.normalize("۰۹۱۲ ۱۲۳ ۴۵۶۷"));
        assertEquals("+989121234567", PhoneNormalizer.normalize("0098-912-123-4567"));
        assertEquals("+989121234567", PhoneNormalizer.normalize("9121234567"));
    }

    @Test public void rejectsTooShortValues() {
        assertEquals("", PhoneNormalizer.normalize("12345"));
    }
}
