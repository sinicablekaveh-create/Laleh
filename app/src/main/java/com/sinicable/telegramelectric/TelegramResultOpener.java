package com.sinicable.telegramelectric;

import android.content.Context;

/**
 * Bridge between Laleh search results and the user's selected Telegram app.
 */
public final class TelegramResultOpener {
    private TelegramResultOpener() {}

    public static boolean openUsername(Context context, String username) {
        TelegramConnectionSettings settings = new TelegramConnectionSettings(context);
        return TelegramAppConnector.openUsername(
                context,
                clean(username),
                settings.getSelectedPackage()
        );
    }

    public static boolean openPhone(Context context, String phone) {
        TelegramConnectionSettings settings = new TelegramConnectionSettings(context);
        return TelegramAppConnector.openPhone(
                context,
                phone,
                settings.getSelectedPackage()
        );
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("@", "").trim();
    }
}
