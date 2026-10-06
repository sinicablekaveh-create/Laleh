package com.sinicable.telegramelectric.groupsearch;

public final class GroupSearchResult {
    private final String title;
    private final String username;
    private final int score;

    public GroupSearchResult(String title, String username, int score) {
        this.title = title;
        this.username = username;
        this.score = score;
    }

    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public int getScore() { return score; }
}
