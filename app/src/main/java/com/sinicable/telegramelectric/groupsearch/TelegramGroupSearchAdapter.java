package com.sinicable.telegramelectric.groupsearch;

/**
 * Adapter boundary between the intelligent group search layer and Telegram TDLib.
 *
 * This class intentionally does not create a new Telegram session.
 * It is designed to receive the existing TelegramClientManager integration.
 */
public final class TelegramGroupSearchAdapter {

    public interface Callback {
        void onResults(java.util.List<GroupSearchResult> results);
        void onError(String message);
    }

    public void search(String query, Callback callback) {
        if (callback == null) {
            return;
        }

        if (query == null || query.trim().isEmpty()) {
            callback.onResults(java.util.Collections.emptyList());
            return;
        }

        // TDLib SearchPublicChats integration is connected in the next step.
        // This adapter keeps the existing TelegramClientManager lifecycle intact.
        callback.onResults(java.util.Collections.emptyList());
    }
}
