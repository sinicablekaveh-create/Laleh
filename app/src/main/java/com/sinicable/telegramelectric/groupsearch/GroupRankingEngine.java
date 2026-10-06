package com.sinicable.telegramelectric.groupsearch;

public class GroupRankingEngine {

    public int calculateScore(String title, String query) {
        if (title == null || query == null) {
            return 0;
        }

        int score = 0;
        String normalizedTitle = title.trim();
        String normalizedQuery = query.trim();

        if (normalizedTitle.equalsIgnoreCase(normalizedQuery)) {
            score += 40;
        }

        if (normalizedTitle.contains(normalizedQuery)) {
            score += 30;
        }

        return score;
    }
}
