package com.sinicable.telegramelectric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PhoneNumberNormalizerTest {
    @Test
    public void internationalFormatsResolveToTheSameNumber() {
        for (String value : new String[] {"+98 912-345-6789", "989123456789", "00989123456789", "(+98) 912.345.6789"}) {
            assertEquals("+989123456789", PhoneNumberNormalizer.normalize(value));
        }
    }

    @Test
    public void domesticIranianMobileNumbersResolveWithCountryCode() {
        assertEquals("+989123456789", PhoneNumberNormalizer.normalize("09123456789"));
        assertEquals("+989123456789", PhoneNumberNormalizer.normalize("9123456789"));
    }

    @Test
    public void persianAndArabicDigitsAndDirectionMarksAreSupported() {
        assertEquals("+989123456789", PhoneNumberNormalizer.normalize("\u200f۰۹۱۲ ۳۴۵ ۶۷۸۹\u200e"));
        assertEquals("+989123456789", PhoneNumberNormalizer.normalize("+٩٨٩١٢٣٤٥٦٧٨٩"));
    }

    @Test
    public void explicitForeignCountryCodesArePreserved() {
        assertEquals("+994501234567", PhoneNumberNormalizer.normalize("994501234567"));
        assertEquals("+12025550123", PhoneNumberNormalizer.normalize("+1 (202) 555-0123"));
    }

    @Test
    public void ambiguousForeignDomesticNumbersNeedCountryCode() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberNormalizer.normalize("02025550123"));
    }

    @Test
    public void lettersExtensionsAndMisplacedPlusAreRejected() {
        for (String value : new String[] {"+989123456789 ext 5", "abc09123456789", "98+9123456789", "++989123456789"}) {
            assertThrows(value, IllegalArgumentException.class, () -> PhoneNumberNormalizer.normalize(value));
        }
    }

    @Test
    public void emptyShortTooLongAndInternationalLeadingZeroAreRejected() {
        for (String value : new String[] {"", "   ", "123", "+1234567890123456", "+09123456789", "0009123456789"}) {
            assertThrows(value, IllegalArgumentException.class, () -> PhoneNumberNormalizer.normalize(value));
        }
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberNormalizer.normalize(null));
    }
}
