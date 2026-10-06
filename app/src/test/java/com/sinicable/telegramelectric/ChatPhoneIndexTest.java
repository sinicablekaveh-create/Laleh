package com.sinicable.telegramelectric;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatPhoneIndexTest {

    @Test
    public void addMessageTextAddsNormalizedUniqueNumbersInDiscoveryOrder() {
        ChatPhoneIndex index = new ChatPhoneIndex();

        assertTrue(index.addMessageText("Call +98 912 345 6789 or 021-1234-5678"));
        assertFalse(index.addMessageText("Duplicate: +98 912 345 6789"));

        assertEquals(
                new LinkedHashSet<>(Arrays.asList("+989123456789", "02112345678")),
                index.getPhones()
        );
        assertEquals(2, index.size());
    }

    @Test
    public void addMessageTextRejectsBlankAndClearRemovesIndexedNumbers() {
        ChatPhoneIndex index = new ChatPhoneIndex();

        assertFalse(index.addMessageText(null));
        assertFalse(index.addMessageText("   "));
        assertTrue(index.addMessageText("0912 345 6789"));

        index.clear();

        assertTrue(index.getPhones().isEmpty());
        assertEquals(0, index.size());
    }
}
