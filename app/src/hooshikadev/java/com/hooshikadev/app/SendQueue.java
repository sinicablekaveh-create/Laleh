package com.hooshikadev.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SendQueue {
    private final SharedPreferences prefs;

    public SendQueue(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("hooshikadev_queue", Context.MODE_PRIVATE);
    }

    public synchronized void start(List<ContactRecord> contacts, String message, int intervalMinutes) {
        JSONArray array = new JSONArray();
        for (ContactRecord contact : contacts) {
            try {
                JSONObject item = new JSONObject();
                item.put("name", contact.name);
                item.put("phone", contact.phone);
                array.put(item);
            } catch (Exception ignored) { }
        }
        prefs.edit()
                .putString("items", array.toString())
                .putString("message", message == null ? "" : message.trim())
                .putInt("interval", Math.max(1, intervalMinutes))
                .putInt("index", 0)
                .putLong("next_at", System.currentTimeMillis())
                .putBoolean("active", array.length() > 0)
                .apply();
    }

    public synchronized QueueItem current() {
        if (!isActive()) return null;
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            int index = prefs.getInt("index", 0);
            if (index < 0 || index >= array.length()) return null;
            JSONObject item = array.getJSONObject(index);
            return new QueueItem(item.optString("name"), item.optString("phone"),
                    prefs.getString("message", ""), index, array.length(), prefs.getLong("next_at", 0L));
        } catch (Exception ignored) { return null; }
    }

    public synchronized boolean advance() {
        QueueItem current = current();
        if (current == null) { stop(); return false; }
        int next = current.index + 1;
        if (next >= current.total) { stop(); return false; }
        long nextAt = System.currentTimeMillis() + prefs.getInt("interval", 1) * 60_000L;
        prefs.edit().putInt("index", next).putLong("next_at", nextAt).apply();
        return true;
    }

    public synchronized void stop() { prefs.edit().putBoolean("active", false).apply(); }
    public synchronized boolean isActive() { return prefs.getBoolean("active", false); }
    public synchronized int intervalMinutes() { return prefs.getInt("interval", 1); }

    public synchronized List<ContactRecord> allItems() {
        List<ContactRecord> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new ContactRecord(item.optString("name"), item.optString("phone"), true, 0));
            }
        } catch (Exception ignored) { }
        return result;
    }

    public static final class QueueItem {
        public final String name;
        public final String phone;
        public final String message;
        public final int index;
        public final int total;
        public final long nextAt;

        QueueItem(String name, String phone, String message, int index, int total, long nextAt) {
            this.name = name;
            this.phone = phone;
            this.message = message;
            this.index = index;
            this.total = total;
            this.nextAt = nextAt;
        }
    }
}
