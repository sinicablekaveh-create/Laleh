package com.sinicable.telegramelectric;

import android.content.Context;

/**
 * Unified bridge between Laleh search results and the selected Telegram app.
 */
public final class TelegramSearchResultAction {
    private TelegramSearchResultAction() {}

    public static boolean open(Context context, String value) {
        if (value == null) {
            return false;
        }

        String target = value.trim();
        if (target.isEmpty()) {
            return false;
        }

        TelegramConnectionSettings settings = new TelegramConnectionSettings(context);
        String packageName = settings.getSelectedPackage();

        if (target.startsWith("+")) {
            return TelegramAppConnector.openPhone(context, target, packageName);
        }

        target = target.replace("@", "");
        return TelegramAppConnector.openUsername(context, target, packageName);
    }
}
