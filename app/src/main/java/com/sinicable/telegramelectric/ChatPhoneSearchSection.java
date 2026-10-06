package com.sinicable.telegramelectric;

import java.util.List;

/**
 * Integration bridge for displaying phone numbers discovered from chat content.
 * Keeps search UI independent from TDLib synchronization.
 */
public final class ChatPhoneSearchSection {
    private final ChatPhoneResultAdapter adapter;

    public ChatPhoneSearchSection(ChatPhoneResultAdapter adapter) {
        this.adapter = adapter;
    }

    public void updateResults(List<ChatPhoneResultViewModel> results) {
        if (adapter != null) {
            adapter.update(results);
        }
    }
}
