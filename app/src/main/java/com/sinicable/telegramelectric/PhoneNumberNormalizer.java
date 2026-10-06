package com.sinicable.telegramelectric;

/** Normalizes explicit international numbers and Iranian domestic mobile numbers. */
public final class PhoneNumberNormalizer {
    private PhoneNumberNormalizer() { }

    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("شماره تلفن را وارد کنید.");
        }
        StringBuilder digits = new StringBuilder();
        boolean explicitInternational = false;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int digit = Character.digit(codePoint, 10);
            if (digit >= 0) {
                digits.append((char) ('0' + digit));
            } else if (codePoint == '+' && digits.length() == 0 && !explicitInternational) {
                explicitInternational = true;
            } else if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                    || codePoint == '-' || codePoint == '(' || codePoint == ')' || codePoint == '.'
                    || codePoint == 0x200e || codePoint == 0x200f || codePoint == 0x061c) {
                // Formatting and direction marks aren't part of the telephone number.
            } else {
                throw new IllegalArgumentException("شماره فقط باید شامل رقم، کد کشور و نشانه‌های معمول باشد.");
            }
        }

        String value = digits.toString();
        if (!explicitInternational && value.startsWith("00")) {
            value = value.substring(2);
            explicitInternational = true;
        }
        if (!explicitInternational) {
            if (value.matches("09[0-9]{9}")) {
                value = "98" + value.substring(1);
            } else if (value.matches("9[0-9]{9}")) {
                value = "98" + value;
            } else if (value.startsWith("0")) {
                throw new IllegalArgumentException("این شمارهٔ محلی به کد کشور نیاز دارد؛ مثال: +98 یا +994.");
            }
        }
        // E.164 permits at most 15 digits; shorter identifiers aren't account phone numbers.
        if (!value.matches("[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("شماره معتبر با کد کشور وارد کنید (۷ تا ۱۵ رقم).");
        }
        return "+" + value;
    }
}
