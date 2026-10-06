package com.sinicable.telegramelectric;

import java.util.List;

/**
 * Synchronization layer between Telegram chat messages and Laleh phone index.
 * The TDLib message listener can feed new message text into this manager.
 */
public final class ChatPhoneSyncManager {
    private final ChatPhoneIndex index;

    public ChatPhoneSyncManager(ChatPhoneIndex index) {
        this.index = index;
    }

    public void processMessage(String messageText) {
        if (messageText == null || messageText.isEmpty()) {
            return;
        }

        List<String> numbers = ChatPhoneNumberExtractor.extract(messageText);
        for (String number : numbers) {
            index.add(number);
        }
    }

    public List<String> getIndexedNumbers() {
        return index.getAll();
    }
}
