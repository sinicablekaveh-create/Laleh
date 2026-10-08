package com.sinicable.telegramelectric;

/**
 * Normalizes a phone number before building a Telegram phone deep link.
 */
final class TelegramPhoneTarget {
    private TelegramPhoneTarget() { }

    /**
     * Normalizes Unicode digits and phone formatting through {@link PhoneNumberNormalizer},
     * adding country code 98 to Iranian domestic mobile numbers.
     *
     * @return a leading plus followed by 7 to 15 ASCII digits, or an empty string when
     *         normalization rejects the input (including null, blank, or ambiguous local numbers)
     */
    static String normalize(String phone) {
        try {
            return PhoneNumberNormalizer.normalize(phone);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
