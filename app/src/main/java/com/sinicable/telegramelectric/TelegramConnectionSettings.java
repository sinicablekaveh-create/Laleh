package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the user's selected Telegram application. */
public final class TelegramConnectionSettings {
    private static final String PREFS = "telegram_connection_settings";
    private static final String KEY_PACKAGE = "selected_package";

    private final SharedPreferences preferences;

    public TelegramConnectionSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setSelectedPackage(String packageName) {
        preferences.edit().putString(KEY_PACKAGE, packageName).apply();
    }

    public String getSelectedPackage() {
        return preferences.getString(KEY_PACKAGE, "");
    }

    public void clear() {
        preferences.edit().remove(KEY_PACKAGE).apply();
    }
}
