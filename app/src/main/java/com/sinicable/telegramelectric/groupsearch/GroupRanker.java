package com.sinicable.telegramelectric.groupsearch;

/** Compatibility facade for existing consumers. */
public final class GroupRanker {
    public int score(String title, String username, String keyword) {
        return new GroupRankingEngine().score(title, username, keyword);
    }
}
