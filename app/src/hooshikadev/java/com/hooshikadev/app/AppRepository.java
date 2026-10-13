package com.hooshikadev.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AppRepository {
    private static final String PREFS = "hooshikadev_data";
    private final SharedPreferences prefs;

    public AppRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void saveContacts(List<ContactRecord> contacts) {
        JSONArray array = new JSONArray();
        for (ContactRecord contact : contacts) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", contact.name);
                item.put("phone", contact.phone);
                item.put("consent", contact.consent);
                item.put("score", contact.score);
                array.put(item);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("contacts", array.toString()).apply();
    }

    public synchronized List<ContactRecord> contacts() {
        List<ContactRecord> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("contacts", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new ContactRecord(item.optString("name"), item.optString("phone"),
                        item.optBoolean("consent"), item.optInt("score")));
            }
        } catch (Exception ignored) { }
        result.sort(Comparator.comparingInt((ContactRecord item) -> item.score).reversed());
        return result;
    }

    public synchronized void addGroup(String url, String title) {
        JSONArray groups = groupJson();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject existing = groups.optJSONObject(i);
            if (existing != null && url.equals(existing.optString("url"))) return;
        }
        JSONObject item = new JSONObject();
        try {
            item.put("url", url);
            item.put("title", title);
            item.put("score", RelevanceEngine.score(title + " " + url));
            groups.put(item);
            prefs.edit().putString("groups", groups.toString()).apply();
        } catch (Exception ignored) { }
    }

    public synchronized List<GroupRecord> groups() {
        List<GroupRecord> result = new ArrayList<>();
        JSONArray groups = groupJson();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject item = groups.optJSONObject(i);
            if (item != null) result.add(new GroupRecord(item.optString("title"), item.optString("url"), item.optInt("score")));
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.score, a.score));
        return result;
    }

    private JSONArray groupJson() {
        try { return new JSONArray(prefs.getString("groups", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public static final class GroupRecord {
        public final String title;
        public final String url;
        public final int score;
        GroupRecord(String title, String url, int score) { this.title = title; this.url = url; this.score = score; }
    }
}
