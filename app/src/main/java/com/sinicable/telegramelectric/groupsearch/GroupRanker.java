package com.sinicable.telegramelectric.groupsearch;

import com.sinicable.telegramelectric.WordBank;

import java.util.LinkedHashSet;
import java.util.Arrays;

/** Pure relevance scoring; member counts never influence discovery order. */
public final class GroupRanker {
    public int score(String title, String username, String keyword) {
        String query = WordBank.normalize(keyword);
        if (query.isEmpty()) return 0;
        String normalizedTitle = WordBank.normalize(title);
        String normalizedUsername = WordBank.normalize(username);
        int score = 0;
        if (normalizedTitle.equals(query)) score += 1000;
        if (normalizedTitle.contains(query)) score += 500;
        if (normalizedUsername.equals(query)) score += 250;
        if (normalizedUsername.contains(query)) score += 100;
        for (String token : new LinkedHashSet<>(Arrays.asList(query.split(" ")))) {
            if (normalizedTitle.contains(token)) score += 20;
            if (normalizedUsername.contains(token)) score += 5;
        }
        return score;
    }
}
