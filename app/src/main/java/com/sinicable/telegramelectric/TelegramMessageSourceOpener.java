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
     * Launches a Telegram message link after converting the dialog ID to a peer ID.
     * Activity launch failures propagate to the caller.
     *
     * @param context context suitable for starting an activity without a new-task flag
     * @param chatId dialog ID converted by {@link TelegramChatIdConverter#toOpenMessageChatId(long)}
     * @param messageId message identifier inserted unchanged into the link
     * @return true after starting the activity; false if no matching activity is available
     * @throws android.content.ActivityNotFoundException if the resolved activity cannot be started
     * @throws SecurityException if permission to launch the activity is denied
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
