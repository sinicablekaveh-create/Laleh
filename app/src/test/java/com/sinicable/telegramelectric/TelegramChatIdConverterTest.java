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

    @Test
    public void channelOffsetBoundaryDistinguishesBasicGroupsFromChannels() {
        assertEquals(999_999_999_999L,
                TelegramChatIdConverter.toOpenMessageChatId(-999_999_999_999L));
        assertEquals(0L,
                TelegramChatIdConverter.toOpenMessageChatId(-1_000_000_000_000L));
        assertEquals(1L,
                TelegramChatIdConverter.toOpenMessageChatId(-1_000_000_000_001L));
    }

    @Test
    public void smallestBasicGroupIdBecomesPositive() {
        assertEquals(1L, TelegramChatIdConverter.toOpenMessageChatId(-1L));
    }

    @Test
    public void zeroAndLargePositivePeerIdsArePreserved() {
        for (long chatId : new long[] {0L, 1L, 1_000_000_000_001L, Long.MAX_VALUE}) {
            assertEquals(chatId, TelegramChatIdConverter.toOpenMessageChatId(chatId));
        }
    }

    @Test
    public void extremeNegativeDialogIdsRemainWithinLongRangeAfterOffsetRemoval() {
        assertEquals(9_223_371_036_854_775_808L,
                TelegramChatIdConverter.toOpenMessageChatId(Long.MIN_VALUE));
        assertEquals(9_223_371_036_854_775_807L,
                TelegramChatIdConverter.toOpenMessageChatId(Long.MIN_VALUE + 1));
    }
}
