package com.sinicable.telegramelectric;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, persisted outcome of one actual Telegram search attempt. */
public final class WordSearchResult {
    public final String originalWord;
    public final String query;
    public final int stage;
    public final long timestamp;
    public final boolean success;
    public final int newGroups;
    public final int totalGroups;
    public final String error;
    public final List<Long> resultIds;

    WordSearchResult(String originalWord, String query, int stage, long timestamp,
                     boolean success, int newGroups, int totalGroups, String error,
                     List<Long> resultIds) {
        this.originalWord = originalWord;
        this.query = query;
        this.stage = stage;
        this.timestamp = timestamp;
        this.success = success;
        this.newGroups = Math.max(0, newGroups);
        this.totalGroups = Math.max(0, totalGroups);
        this.error = success || error == null ? "" : error;
        this.resultIds = Collections.unmodifiableList(new ArrayList<>(
                resultIds == null ? Collections.emptyList() : resultIds));
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("originalWord", originalWord);
        json.put("query", query);
        json.put("stage", stage);
        json.put("timestamp", timestamp);
        json.put("success", success);
        json.put("newGroups", newGroups);
        json.put("totalGroups", totalGroups);
        json.put("error", error);
        json.put("resultIds", new JSONArray(resultIds));
        return json;
    }

    static WordSearchResult fromJson(JSONObject json) {
        List<Long> ids = new ArrayList<>();
        JSONArray array = json.optJSONArray("resultIds");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) ids.add(array.optLong(i));
        }
        return new WordSearchResult(json.optString("originalWord"), json.optString("query"),
                json.optInt("stage", 1), json.optLong("timestamp"), json.optBoolean("success"),
                json.optInt("newGroups"), json.optInt("totalGroups"), json.optString("error"), ids);
    }
}
