package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

final class AuthSessionStore {
    private static final String PREFS = "telegram_auto_login";
    private static final String KEY_API_ID = "api_id";
    private static final String KEY_API_HASH = "api_hash";

    private final SharedPreferences prefs;

    AuthSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(int apiId, String apiHash) {
        if (apiId <= 0 || apiHash == null || apiHash.trim().isEmpty()) return;
        prefs.edit()
                .putInt(KEY_API_ID, apiId)
                .putString(KEY_API_HASH, apiHash.trim())
                .apply();
    }

    boolean hasCredentials() {
        return getApiId() > 0 && !getApiHash().isEmpty();
    }

    int getApiId() {
        return prefs.getInt(KEY_API_ID, 0);
    }

    String getApiHash() {
        String value = prefs.getString(KEY_API_HASH, "");
        return value == null ? "" : value.trim();
    }

    void clear() {
        prefs.edit().clear().apply();
    }
}
