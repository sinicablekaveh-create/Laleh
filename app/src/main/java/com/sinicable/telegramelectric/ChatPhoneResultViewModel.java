package com.sinicable.telegramelectric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presentation model for phone numbers discovered from Telegram messages.
 * Keeps UI independent from TDLib and indexing layers.
 */
public class ChatPhoneResultViewModel {
    private final List<ChatPhoneSourceLocator> results = new ArrayList<>();

    public void addResult(ChatPhoneSourceLocator result) {
        if (result != null) {
            results.add(result);
        }
    }

    public List<ChatPhoneSourceLocator> getResults() {
        return Collections.unmodifiableList(results);
    }

    public void clear() {
        results.clear();
    }
}
