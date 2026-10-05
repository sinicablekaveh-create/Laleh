package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

final class BackgroundModeStore {
    private static final String PREFS = "background_mode";
    private static final String KEY_ENABLED = "enabled";

    private final SharedPreferences prefs;

    BackgroundModeStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
}
