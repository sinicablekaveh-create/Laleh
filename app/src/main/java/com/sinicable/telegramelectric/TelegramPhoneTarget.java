package com.sinicable.telegramelectric;

/**
 * Normalizes a phone number before building a Telegram phone deep link.
 */
final class TelegramPhoneTarget {
    private TelegramPhoneTarget() { }

    static String normalize(String phone) {
        try {
            return PhoneNumberNormalizer.normalize(phone);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
