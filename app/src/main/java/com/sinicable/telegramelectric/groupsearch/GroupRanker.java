package com.sinicable.telegramelectric.groupsearch;

import com.sinicable.telegramelectric.WordBank;
import com.sinicable.telegramelectric.SearchQuery;

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Pure relevance scoring; member counts never influence discovery order. */
public final class GroupRanker {
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N}_]+");
    public int score(String title, String username, String keyword) {
        SearchQuery context = SearchQuery.parse(keyword);
        String query = words(context.normalized);
        if (query.isEmpty()) return 0;
        String normalizedTitle = words(title);
        String normalizedUsername = WordBank.normalize(username);
        int score = 0;
        if (normalizedTitle.equals(query)) score += 1000;
        if (containsPhrase(normalizedTitle, query)) score += 500;
        if (normalizedUsername.equals(query.replace(' ', '_'))) score += 250;
        if (normalizedUsername.contains(query)) score += 100;
        if (!context.category.isEmpty() && containsPhrase(normalizedTitle, context.category)) score += 100;
        if (!context.location.isEmpty() && containsPhrase(normalizedTitle, context.location)) score += 40;
        for (String token : new LinkedHashSet<>(Arrays.asList(query.split(" ")))) {
            if (containsPhrase(normalizedTitle, token)) score += 20;
            if (normalizedUsername.contains(token)) score += 5;
        }
        return score;
    }

    private static String words(String raw) {
        return PUNCTUATION.matcher(WordBank.normalize(raw)).replaceAll(" ").trim();
    }

    private static boolean containsPhrase(String source, String value) {
        return (" " + source + " ").contains(" " + value + " ");
    }
}
