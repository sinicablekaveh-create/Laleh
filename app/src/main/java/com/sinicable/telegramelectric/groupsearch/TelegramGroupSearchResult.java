package com.sinicable.telegramelectric.groupsearch;

public class TelegramGroupSearchResult {
    private final long chatId;
    private final String title;
    private final String username;
    private final int score;

    public TelegramGroupSearchResult(long chatId, String title, String username, int score) {
        this.chatId = chatId;
        this.title = title;
        this.username = username;
        this.score = score;
    }

    public long getChatId() { return chatId; }
    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public int getScore() { return score; }
}
