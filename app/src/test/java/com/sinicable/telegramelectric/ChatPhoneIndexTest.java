package com.sinicable.telegramelectric;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatPhoneIndexTest {

    @Test
    public void extractionKeepsDomesticAndInternationalFormsDistinct() {
        ChatPhoneIndex index = new ChatPhoneIndex();

        assertTrue(index.addMessageText("Domestic: 0912-345-6789; international: +98 912 345 6789"));
        assertFalse(index.addMessageText("Domestic again: 09123456789; international again: +989123456789"));

        // Indexing removes formatting but must not infer a country code.
        assertEquals(Arrays.asList("09123456789", "+989123456789"),
                new ArrayList<>(index.getPhones()));
        assertEquals(2, index.size());
    }

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

    @Test
    public void duplicatesWithinAndAcrossMessagesKeepFirstDiscoveryOrder() {
        ChatPhoneIndex index = new ChatPhoneIndex();

        assertTrue(index.addMessageText("First: +1 (202) 555-0100; again: +12025550100"));
        assertFalse(index.addMessageText("Only a duplicate: +1-202-555-0100"));
        assertTrue(index.addMessageText("Old: +12025550100; new: +1 (202) 555-0101"));

        // Set equality alone would not detect a regression in discovery order.
        assertEquals(Arrays.asList("+12025550100", "+12025550101"),
                new ArrayList<>(index.getPhones()));
        assertEquals(2, index.size());
    }

    @Test
    public void textWithoutPhoneCandidatesDoesNotChangeExistingIndex() {
        ChatPhoneIndex index = new ChatPhoneIndex();
        index.addMessageText("Contact: +1 (202) 555-0100");

        for (String text : new String[] {null, "", " \t\n", "No contact information", "Code: 12345"}) {
            assertFalse(index.addMessageText(text));
        }

        assertEquals(Arrays.asList("+12025550100"), new ArrayList<>(index.getPhones()));
        assertEquals(1, index.size());
    }

    @Test
    public void returnedPhonesAreAnIndependentSnapshot() {
        ChatPhoneIndex index = new ChatPhoneIndex();
        index.addMessageText("Contact: +1 (202) 555-0100");
        Set<String> snapshot = index.getPhones();

        index.addMessageText("Second: +1 (202) 555-0101");
        assertEquals(Arrays.asList("+12025550100"), new ArrayList<>(snapshot));

        snapshot.clear();
        assertEquals(Arrays.asList("+12025550100", "+12025550101"),
                new ArrayList<>(index.getPhones()));
        assertEquals(2, index.size());
    }

    @Test
    public void clearingAllowsPreviouslySeenNumbersToBeDiscoveredAgain() {
        ChatPhoneIndex index = new ChatPhoneIndex();
        index.addMessageText("First: +12025550100; second: +12025550101");

        index.clear();
        index.clear();

        assertTrue(index.addMessageText("Second: +12025550101; first: +12025550100"));
        assertEquals(Arrays.asList("+12025550101", "+12025550100"),
                new ArrayList<>(index.getPhones()));
    }
}
