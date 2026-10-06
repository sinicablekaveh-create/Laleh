package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Opens the original Telegram message source when available.
 */
public final class TelegramMessageSourceOpener {
    private TelegramMessageSourceOpener() {}

    public static boolean openMessage(Context context, long chatId, long messageId) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String uri = "tg://openmessage?chat_id=" + chatId + "&message_id=" + messageId;
        intent.setData(Uri.parse(uri));

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }

        context.startActivity(intent);
        return true;
    }
}
