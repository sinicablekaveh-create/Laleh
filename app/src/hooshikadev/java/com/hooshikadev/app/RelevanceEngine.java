package com.hooshikadev.app;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RelevanceEngine {
    private static final List<String> TERMS = Arrays.asList(
            "برق", "الکتریک", "الکتریکی", "لاله زار", "ساختمان", "صنعت", "کابل",
            "تابلو", "روشنایی", "کلید", "پریز", "electrical", "electric", "cable", "lighting"
    );

    private RelevanceEngine() {}

    public static int score(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('‌', ' ');
        int score = 10;
        for (String term : TERMS) if (normalized.contains(term)) score += 12;
        if (normalized.contains("خرید") || normalized.contains("فروش") || normalized.contains("پروژه")) score += 8;
        return Math.min(100, score);
    }

    public static String explain(String text) {
        int score = score(text);
        if (score >= 70) return "ارتباط زیاد با بازار برق و ساختمان";
        if (score >= 40) return "ارتباط متوسط؛ بهتر است دستی بررسی شود";
        return "ارتباط کم یا اطلاعات ناکافی";
    }
}
