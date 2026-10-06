package com.sinicable.telegramelectric.groupsearch;

import java.text.Normalizer;
import java.util.Locale;

public final class PersianNormalizer {
    private PersianNormalizer() { }
    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace('\u200c', ' ').replace('\u200d', ' ')
                .replaceAll("[\\p{M}ـ]", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    public static boolean contains(String text, String phrase) {
        String needle = normalize(phrase);
        return !needle.isEmpty() && (" " + normalize(text) + " ").contains(" " + needle + " ");
    }
    public static String topic(String value) {
        return normalize(value).replaceAll("(?:^| )گروه(?= |$)", " ").trim().replaceAll(" +", " ");
    }
}