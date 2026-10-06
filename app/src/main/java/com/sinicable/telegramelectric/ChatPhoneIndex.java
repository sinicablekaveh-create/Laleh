package com.sinicable.telegramelectric;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps phone numbers discovered from chat messages available for Laleh search.
 * The index is intentionally independent from TDLib session management.
 */
public final class ChatPhoneIndex {
    private final Set<String> phones = new LinkedHashSet<>();

    public synchronized boolean addMessageText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        List<String> found = ChatPhoneNumberExtractor.extract(text);
        return phones.addAll(found);
    }

    public synchronized Set<String> getPhones() {
        return new LinkedHashSet<>(phones);
    }

    public synchronized void clear() {
        phones.clear();
    }

    public synchronized int size() {
        return phones.size();
    }
}
