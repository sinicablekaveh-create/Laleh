package com.sinicable.telegramelectric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Small deterministic discovery query parser, bounded before normalization. */
public final class SearchQuery {
    public enum Intent { DISCOVERY, LEARNING, MARKET }
    private static final String[] CITIES = {"تهران", "مشهد", "اصفهان", "شیراز", "تبریز"};
    private static final String[] CATEGORIES = {
            "برق صنعتی", "برق ساختمان", "انرژی خورشیدی", "تابلو برق",
            "اتوماسیون صنعتی", "سیم و کابل", "برق", "کابل", "روشنایی"
    };
    public final String normalized;
    public final String category;
    public final String location;
    public final Intent intent;
    public final List<String> keywords;

    private SearchQuery(String normalized, String category, String location,
                        Intent intent, List<String> keywords) {
        this.normalized = normalized;
        this.category = category;
        this.location = location;
        this.intent = intent;
        this.keywords = Collections.unmodifiableList(keywords);
    }

    public static SearchQuery parse(String raw) {
        String bounded = raw == null ? "" : raw;
        int count = bounded.codePointCount(0, bounded.length());
        if (count > 96) bounded = bounded.substring(0, bounded.offsetByCodePoints(0, 96));
        String normalized = WordBank.normalize(bounded);
        String location = "";
        for (String city : CITIES) if (containsPhrase(normalized, city)) { location = city; break; }
        String category = "";
        for (String candidate : CATEGORIES) {
            if (containsPhrase(normalized, candidate)) { category = candidate; break; }
        }
        Intent intent = containsPhrase(normalized, "آموزش") ? Intent.LEARNING
                : containsPhrase(normalized, "خرید") || containsPhrase(normalized, "فروش")
                ? Intent.MARKET : Intent.DISCOVERY;
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (keywords.size() >= 16) break;
            if (!token.isEmpty() && !WordBank.isStopWord(token)
                    && !token.equals(location) && !token.equals("گروه") && !token.equals("تلگرام")) {
                keywords.add(token);
            }
        }
        return new SearchQuery(normalized, category, location, intent, new ArrayList<>(keywords));
    }

    private static boolean containsPhrase(String value, String phrase) {
        return (" " + value + " ").contains(" " + phrase + " ");
    }
}
