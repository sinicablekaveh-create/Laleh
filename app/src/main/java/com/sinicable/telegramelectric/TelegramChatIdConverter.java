package com.sinicable.telegramelectric;

/**
 * Converts TDLib/Bot API dialog IDs to the bare peer IDs used by Telegram
 * message deep links.
 */
final class TelegramChatIdConverter {
    private static final long CHANNEL_OFFSET = 1_000_000_000_000L;

    private TelegramChatIdConverter() { }

    /**
     * Converts a dialog ID for a Telegram message deep link. IDs at or below
     * -1,000,000,000,000 have their sign reversed and that offset subtracted; other
     * negative IDs have their sign reversed. Zero and positive IDs are unchanged.
     * No ID validity checks are performed.
     */
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
