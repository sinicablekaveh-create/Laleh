package com.sinicable.telegramelectric;

/**
 * Normalizes a phone number before building a Telegram phone deep link.
 */
final class TelegramPhoneTarget {
    private TelegramPhoneTarget() { }

    /**
     * Returns a plus-prefixed number with 7 to 15 ASCII digits, converting Iranian
     * domestic mobile numbers to country code 98 and removing supported formatting.
     * Delegates to {@link PhoneNumberNormalizer#normalize(String)} and converts its
     * {@link IllegalArgumentException} to an empty string, including for null, blank,
     * invalid, or ambiguous domestic input.
     */
    static String normalize(String phone) {
        try {
            return PhoneNumberNormalizer.normalize(phone);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
