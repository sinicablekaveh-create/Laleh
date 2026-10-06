package com.sinicable.telegramelectric.groupsearch;

public final class GroupRankingEngine {
    /** Independent signals: exact topic 40, all keywords 30, query location 20, sector 10. */
    public int score(String title, String username, String query) {
        String topic = PersianNormalizer.topic(query);
        if (topic.isEmpty()) return 0;
        String normalizedTitle = PersianNormalizer.topic(title);
        String searchable = PersianNormalizer.normalize(title + " " + (username == null ? "" : username));
        int score = normalizedTitle.equals(topic) ? 40 : 0;
        boolean all = true;
        for (String token : topic.split(" ")) all &= PersianNormalizer.contains(searchable, token);
        if (all) score += 30;
        for (String location : GroupKeywordBank.LOCATIONS) {
            if (PersianNormalizer.contains(topic, location) && PersianNormalizer.contains(searchable, location)) {
                score += 20; break;
            }
        }
        for (String category : new GroupKeywordBank().related(topic)) {
            if (!GroupKeywordBank.LOCATIONS.contains(category) && PersianNormalizer.contains(searchable, category)) {
                score += 10; break;
            }
        }
        return score;
    }
}
