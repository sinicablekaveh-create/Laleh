package com.sinicable.telegramelectric;

/**
 * Stores source information for phone numbers discovered from Telegram chats.
 * Keeps chat/message references available for future UI navigation.
 */
public class ChatPhoneSourceLocator {
    private final String phone;
    private final long chatId;
    private final long messageId;
    private final String chatTitle;
    private final String messageTime;

    public ChatPhoneSourceLocator(String phone, long chatId, long messageId, String chatTitle) {
        this(phone, chatId, messageId, chatTitle, "");
    }

    public ChatPhoneSourceLocator(
            String phone,
            long chatId,
            long messageId,
            String chatTitle,
            String messageTime
    ) {
        this.phone = phone;
        this.chatId = chatId;
        this.messageId = messageId;
        this.chatTitle = chatTitle;
        this.messageTime = messageTime == null ? "" : messageTime;
    }

    public String getPhone() {
        return phone;
    }

    public long getChatId() {
        return chatId;
    }

    public long getMessageId() {
        return messageId;
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public String getMessageTime() {
        return messageTime;
    }
}
