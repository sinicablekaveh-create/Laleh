package com.sinicable.telegramelectric;

import org.json.JSONException;
import org.json.JSONObject;

/** Explicitly approved public catalog metadata. Never includes account/session/contact fields. */
public final class DiscoveryMetadata {
    public final long groupId;
    public final String title;
    public final String username;
    public final String category;
    public final String location;
    public final long revision;

    public DiscoveryMetadata(long groupId, String title, String username,
                             String category, String location, long revision) {
        if (groupId >= 0 || revision <= 0) throw new IllegalArgumentException("Invalid group/version");
        String name = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z][a-z0-9_]{4,31}")) throw new IllegalArgumentException("Public username required");
        String label = title == null ? "" : title.trim();
        if (label.isEmpty() || label.codePointCount(0, label.length()) > 256)
            throw new IllegalArgumentException("Invalid public title");
        this.groupId = groupId;
        this.title = label;
        this.username = name;
        this.category = boundedLabel(category);
        this.location = boundedLabel(location);
        this.revision = revision;
    }

    private static String boundedLabel(String value) {
        String clean = WordBank.normalize(value);
        if (clean.codePointCount(0, clean.length()) > 96) throw new IllegalArgumentException("Label too long");
        return clean;
    }

    public String publicLink() { return "https://t.me/" + username; }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("schemaVersion", 1).put("groupId", groupId)
                .put("title", title).put("username", username).put("category", category)
                .put("location", location).put("revision", revision);
    }

    static DiscoveryMetadata fromJson(JSONObject json) throws JSONException {
        if (json.getInt("schemaVersion") != 1) throw new IllegalArgumentException("Unsupported schema");
        return new DiscoveryMetadata(json.getLong("groupId"), json.getString("title"),
                json.getString("username"), json.getString("category"),
                json.getString("location"), json.getLong("revision"));
    }
}
