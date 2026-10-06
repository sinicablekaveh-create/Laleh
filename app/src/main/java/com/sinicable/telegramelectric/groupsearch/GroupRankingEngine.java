package com.sinicable.telegramelectric.groupsearch;

public class GroupRankingEngine {

    private final QueryNormalizer normalizer = new QueryNormalizer();

    public int calculateScore(String title, String query) {
        String normalizedTitle = normalizer.normalize(title);
        String normalizedQuery = normalizer.normalize(query);

        if (normalizedTitle.isEmpty() || normalizedQuery.isEmpty()) {
            return 0;
        }

        int score = 0;

        if (normalizedTitle.equalsIgnoreCase(normalizedQuery)) {
            score += 40;
        }

        if (normalizedTitle.toLowerCase(java.util.Locale.ROOT)
                .contains(normalizedQuery.toLowerCase(java.util.Locale.ROOT))) {
            score += 30;
        }

        return score;
    }
}
