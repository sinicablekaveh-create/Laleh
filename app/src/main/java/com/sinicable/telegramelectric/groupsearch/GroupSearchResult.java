package com.sinicable.telegramelectric.groupsearch;

public final class GroupSearchResult {
    private final long chatId;
    private final String title;
    private final String username;
    private final String link;
    private final int memberCount;
    private final int score;

    public GroupSearchResult(String title, String username, int score) {
        this(0L, title, username, "", 0, score);
    }

    public GroupSearchResult(
            long chatId,
            String title,
            String username,
            String link,
            int memberCount,
            int score
    ) {
        this.chatId = chatId;
        this.title = title == null ? "" : title;
        this.username = username == null ? "" : username;
        this.link = link == null ? "" : link;
        this.memberCount = Math.max(0, memberCount);
        this.score = Math.max(0, score);
    }

    public long getChatId() { return chatId; }
    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public String getLink() { return link; }
    public int getMemberCount() { return memberCount; }
    public int getScore() { return score; }
}
