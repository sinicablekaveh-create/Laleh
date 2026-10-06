package com.sinicable.telegramelectric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts phone numbers from Telegram message text.
 * Designed to feed the Laleh search index without changing TDLib sessions.
 */
public final class ChatPhoneNumberExtractor {
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?\\d[\\d\\s\\-()]{7,18}\\d)(?!\\d)"
    );

    private ChatPhoneNumberExtractor() {}

    public static List<String> extract(String messageText) {
        Set<String> numbers = new LinkedHashSet<>();
        if (messageText == null || messageText.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Matcher matcher = PHONE_PATTERN.matcher(messageText);
        while (matcher.find()) {
            String normalized = normalize(matcher.group());
            if (normalized.length() >= 8) {
                numbers.add(normalized);
            }
        }

        return new ArrayList<>(numbers);
    }

    private static String normalize(String value) {
        return value.replaceAll("[^0-9+]", "");
    }
}
