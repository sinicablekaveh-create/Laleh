package com.sinicable.telegramelectric;

/**
 * Converts TDLib/Bot API dialog IDs to the bare peer IDs used by Telegram
 * message deep links.
 */
final class TelegramChatIdConverter {
    private static final long CHANNEL_OFFSET = 1_000_000_000_000L;

    private TelegramChatIdConverter() { }

    static long toOpenMessageChatId(long chatId) {
        if (chatId <= -CHANNEL_OFFSET) {
            return -chatId - CHANNEL_OFFSET;
        }
        if (chatId < 0 && chatId != Long.MIN_VALUE) {
            return -chatId;
        }
        return chatId;
    }
}
