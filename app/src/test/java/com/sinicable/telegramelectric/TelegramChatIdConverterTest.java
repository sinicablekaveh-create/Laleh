package com.sinicable.telegramelectric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TelegramChatIdConverterTest {
    @Test
    public void supergroupDialogIdConvertsToBareChannelId() {
        assertEquals(
                1234567890L,
                TelegramChatIdConverter.toOpenMessageChatId(-1001234567890L)
        );
    }

    @Test
    public void basicGroupDialogIdConvertsToPositiveChatId() {
        assertEquals(
                123456789L,
                TelegramChatIdConverter.toOpenMessageChatId(-123456789L)
        );
    }

    @Test
    public void userDialogIdIsPreserved() {
        assertEquals(42L, TelegramChatIdConverter.toOpenMessageChatId(42L));
    }
}
