package com.sinicable.telegramelectric.groupsearch;

public final class GroupRanker {
    public int score(String title, String username, String keyword) {
        int score = 0;
        if (contains(title, keyword)) score += 50;
        if (contains(username, keyword)) score += 30;
        return score;
    }

    private boolean contains(String source, String value) {
        return source != null && value != null && source.toLowerCase().contains(value.toLowerCase());
    }
}
