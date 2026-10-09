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
        if (label.isEmpty() || label.codePointCount(0, label.length()) > 256
                || title.matches("(?s).*[\\x00-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069].*"))
            throw new IllegalArgumentException("Invalid public title");
        this.groupId = groupId;
        this.title = label;
        this.username = name;
        this.category = boundedLabel(category);
        this.location = boundedLabel(location);
        this.revision = revision;
    }

    private static String boundedLabel(String value) {
        if (value != null && value.matches("(?s).*[\\x00-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069].*"))
            throw new IllegalArgumentException("Invalid public label");
        String clean = WordBank.normalize(value);
        if (clean.codePointCount(0, clean.length()) > 96) throw new IllegalArgumentException("Label too long");
        return clean;
    }

    public String publicLink() { return "https://t.me/" + username; }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("schemaVersion", 2).put("groupId", Long.toString(groupId))
                .put("title", title).put("username", username).put("category", category)
                .put("location", location).put("revision", Long.toString(revision));
    }

    public static DiscoveryMetadata fromJson(JSONObject json) throws JSONException {
        java.util.List<String> allowed = java.util.Arrays.asList("schemaVersion", "groupId", "title",
                "username", "category", "location", "revision");
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) if (!allowed.contains(keys.next()))
            throw new IllegalArgumentException("Unknown public metadata field");
        Object schema = json.get("schemaVersion");
        if (!(schema instanceof Number) || (((Number) schema).doubleValue() != 1
                && ((Number) schema).doubleValue() != 2)) throw new IllegalArgumentException("Unsupported schema");
        int version = json.getInt("schemaVersion");
        if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported schema");
        if (version == 2 && (!(json.get("groupId") instanceof String)
                || !(json.get("revision") instanceof String))) {
            throw new IllegalArgumentException("Decimal string identities required");
        }
        if (version == 2 && !json.getString("username").matches("[a-z][a-z0-9_]{4,31}"))
            throw new IllegalArgumentException("Canonical public username required");
        Object id = json.get("groupId"), revision = json.get("revision");
        if (version == 1 && (!(id instanceof Number) || !(revision instanceof Number)))
            throw new IllegalArgumentException("Legacy numeric identities required");
        if (!id.toString().matches("-[1-9][0-9]{0,18}")
                || !revision.toString().matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid decimal identity");
        return new DiscoveryMetadata(Long.parseLong(id.toString()), json.getString("title"),
                json.getString("username"), json.getString("category"),
                json.getString("location"), Long.parseLong(revision.toString()));
    }

    boolean sameContent(DiscoveryMetadata other) {
        return groupId == other.groupId && revision == other.revision && title.equals(other.title)
                && username.equals(other.username) && category.equals(other.category) && location.equals(other.location);
    }
}
