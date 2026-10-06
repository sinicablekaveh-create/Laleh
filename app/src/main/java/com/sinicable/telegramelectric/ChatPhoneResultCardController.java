package com.sinicable.telegramelectric;

/**
 * Controller bridge for the phone search result card.
 * Keeps UI actions separated from TDLib and Telegram connector layers.
 */
public class ChatPhoneResultCardController {

    public interface Listener {
        void onOpenMessage(String chatId, String messageId);
        void onOpenTelegram(String phone, String username);
    }

    private final Listener listener;

    public ChatPhoneResultCardController(Listener listener) {
        this.listener = listener;
    }

    public void showPhoneResult(String phone,
                                String chatName,
                                String messageTime,
                                String chatId,
                                String messageId,
                                String username) {
        // UI layer can bind:
        // phone -> phone text
        // chatName -> source chat
        // messageTime -> message date/time
        // buttons -> listener callbacks
    }

    public void clickViewMessage(String chatId, String messageId) {
        if (listener != null) {
            listener.onOpenMessage(chatId, messageId);
        }
    }

    public void clickOpenTelegram(String phone, String username) {
        if (listener != null) {
            listener.onOpenTelegram(phone, username);
        }
    }
}
