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
        assertEquals("+989123456789", item.getPhone());
        assertEquals(-1001234567890L, item.getChatId());
        assertEquals(99L, item.getMessageId());
        assertEquals("گروه برق", item.getChatTitle());
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
        assertEquals("+989123456789", item.getPhone());
        assertEquals(1L, item.getChatId());
        assertEquals(2L, item.getMessageId());
        assertEquals("chat", item.getChatTitle());
    }

    @Test
    public void nullMessageTimeBecomesEmptyWithoutLosingSourceDetails() {
        ChatPhoneSourceLocator item = new ChatPhoneSourceLocator(
                "+12025550100", -1000000000042L, 12345678901L, "Source chat", null);

        assertEquals("", item.getMessageTime());
        assertEquals("+12025550100", item.getPhone());
        assertEquals(-1000000000042L, item.getChatId());
        assertEquals(12345678901L, item.getMessageId());
        assertEquals("Source chat", item.getChatTitle());
    }

    @Test
    public void callerFormattedMessageTimeIsPreservedVerbatim() {
        for (String time : new String[] {"", "  ۱۴۰۵/۰۷/۱۶ ۱۹:۳۰  ", "Yesterday"}) {
            ChatPhoneSourceLocator item = new ChatPhoneSourceLocator(
                    "+12025550100", 42L, 99L, "Source chat", time);

            assertEquals(time, item.getMessageTime());
        }
    }
}
