package com.sinicable.telegramelectric.groupsearch;

import org.json.JSONObject;

/** Portable sync payload. Timestamp is UTC epoch milliseconds; groupId is a TDLib chat ID. */
public final class GroupSearchRecord {
    public enum Source {
        ANDROID("Android"), WEB("Web"), BACKGROUND_INDEXER("Background Indexer");
        public final String label;
        Source(String label) { this.label = label; }
    }
    public final String query;
    public final long groupId;
    public final String title;
    public final String username;
    public final Source source;
    public final int score;
    public final long timestamp;

    public GroupSearchRecord(String query, long groupId, String title, String username,
                             Source source, int score, long timestamp) {
        if (source == null || score < 0 || score > 100 || timestamp < 0) throw new IllegalArgumentException();
        this.query = PersianNormalizer.normalize(query);
        this.groupId = groupId;
        this.title = title == null ? "" : title;
        this.username = username == null ? "" : username;
        this.source = source;
        this.score = score;
        this.timestamp = timestamp;
    }

    public JSONObject toJson() {
        JSONObject value = new JSONObject();
        try {
            value.put("query", query);
            // String encoding preserves 64-bit IDs in JavaScript consumers.
            value.put("groupId", Long.toString(groupId));
            value.put("title", title);
            value.put("username", username);
            value.put("source", source.label);
            value.put("score", score);
            value.put("timestamp", timestamp);
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        return value;
    }

    public static GroupSearchRecord fromJson(JSONObject value) throws org.json.JSONException {
        Source source = null;
        for (Source candidate : Source.values()) if (candidate.label.equals(value.getString("source"))) source = candidate;
        return new GroupSearchRecord(value.getString("query"), Long.parseLong(value.getString("groupId")),
                value.getString("title"), value.getString("username"), source,
                value.getInt("score"), value.getLong("timestamp"));
    }
}
