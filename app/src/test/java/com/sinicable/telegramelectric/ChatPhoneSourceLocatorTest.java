package com.sinicable.telegramelectric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatPhoneSourceLocatorTest {
    @Test
    public void messageTimeIsPreservedForResultCards() {
        ChatPhoneSourceLocator item = new ChatPhoneSourceLocator(
                "+989123456789",
                -1001234567890L,
                99L,
                "گروه برق",
                "2026-10-06 19:30"
        );

        assertEquals("2026-10-06 19:30", item.getMessageTime());
    }

    @Test
    public void legacyConstructorKeepsEmptyMessageTime() {
        ChatPhoneSourceLocator item = new ChatPhoneSourceLocator(
                "+989123456789",
                1L,
                2L,
                "chat"
        );

        assertEquals("", item.getMessageTime());
    }
}
