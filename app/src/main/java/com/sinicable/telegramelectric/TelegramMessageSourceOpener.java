package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Opens the original Telegram message source when available.
 */
public final class TelegramMessageSourceOpener {
    private TelegramMessageSourceOpener() {}

    /**
     * Launches a Telegram message deep link through an available handler.
     *
     * @param context context capable of starting an activity without a new-task flag
     * @param chatId dialog ID converted to a bare peer ID by {@link TelegramChatIdConverter}
     * @param messageId message ID included unchanged, without validation or conversion
     * @return true after starting the activity, or false if no handler resolves;
     *         successful launch does not confirm the message is accessible
     * @throws android.content.ActivityNotFoundException if the resolved activity can no
     *         longer be launched
     * @throws SecurityException if the activity cannot be launched with the caller's permissions
     */
    public static boolean openMessage(Context context, long chatId, long messageId) {
        long openMessageChatId = TelegramChatIdConverter.toOpenMessageChatId(chatId);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String uri = "tg://openmessage?chat_id=" + openMessageChatId + "&message_id=" + messageId;
        intent.setData(Uri.parse(uri));

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }

        context.startActivity(intent);
        return true;
    }
}
