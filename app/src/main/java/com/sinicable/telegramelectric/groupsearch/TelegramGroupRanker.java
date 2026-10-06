package com.sinicable.telegramelectric.groupsearch;

public class TelegramGroupRanker {
    public int score(String title, String username, String keyword) {
        int score = 0;
        String t = title == null ? "" : title.toLowerCase();
        String u = username == null ? "" : username.toLowerCase();
        String k = keyword == null ? "" : keyword.toLowerCase();

        if (!k.isEmpty() && t.contains(k)) score += 50;
        if (!k.isEmpty() && u.contains(k.replace(" ", ""))) score += 30;
        if (!k.isEmpty() && t.contains(k.replace(" ", ""))) score += 20;
        return score;
    }
}
