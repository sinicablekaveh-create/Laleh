package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Device-local discovery choices, never part of the public metadata outbox. */
public final class DiscoveryPreferences {
    private final SharedPreferences prefs;
    public DiscoveryPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("discovery_preferences_v1", Context.MODE_PRIVATE);
    }
    public synchronized boolean remembersHistory() { return prefs.getBoolean("rememberHistory", false); }
    public synchronized void setRememberHistory(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean("rememberHistory", enabled);
        if (!enabled) editor.remove("history");
        editor.apply();
    }
    public synchronized List<String> history() {
        List<String> values = new ArrayList<>();
        if (!remembersHistory()) return values;
        try {
            JSONArray rows = boundedArray("history");
            for (int i = 0; i < Math.min(20, rows.length()); i++) {
                Object raw = rows.get(i);
                if (!(raw instanceof String)) continue;
                String query = SearchQuery.parse((String) raw).normalized;
                if (!query.isEmpty() && !values.contains(query)) values.add(query);
            }
        } catch (JSONException ignored) { /* Discard corrupt local preferences. */ }
        return values;
    }
    public synchronized void remember(String raw) {
        String query = SearchQuery.parse(raw).normalized;
        if (!remembersHistory() || query.isEmpty()) return;
        List<String> values = history(); values.remove(query); values.add(0, query);
        if (values.size() > 20) values = values.subList(0, 20);
        prefs.edit().putString("history", new JSONArray(values).toString()).apply();
    }
    public synchronized Set<Long> favorites() {
        Set<Long> values = new LinkedHashSet<>();
        try {
            JSONArray rows = boundedArray("favorites");
            for (int i = 0; i < Math.min(100, rows.length()); i++) {
                Object raw = rows.get(i);
                if (!(raw instanceof String) || !((String) raw).matches("-[1-9][0-9]{0,18}")) continue;
                try { values.add(Long.parseLong((String) raw)); } catch (NumberFormatException ignored) { }
            }
        } catch (JSONException ignored) { /* No account/private data is restored. */ }
        return values;
    }
    public synchronized void toggleFavorite(long id) {
        if (id >= 0) return;
        Set<Long> values = favorites();
        if (!values.remove(id)) {
            if (values.size() >= 100) values.remove(values.iterator().next());
            values.add(id);
        }
        JSONArray rows = new JSONArray();
        for (Long value : values) rows.put(Long.toString(value));
        prefs.edit().putString("favorites", rows.toString()).apply();
    }
    public synchronized String theme() {
        String value = prefs.getString("theme", "system");
        return "dark".equals(value) || "light".equals(value) ? value : "system";
    }
    public synchronized void setTheme(String value) {
        if (!"light".equals(value) && !"dark".equals(value) && !"system".equals(value)) return;
        prefs.edit().putString("theme", value).apply();
    }
    public synchronized String category() { return SearchQuery.parse(prefs.getString("category", "")).normalized; }
    public synchronized void setCategory(String value) { prefs.edit().putString("category", SearchQuery.parse(value).normalized).apply(); }
    public synchronized void clear() {
        prefs.edit().remove("history").remove("favorites").remove("category").remove("theme")
                .putBoolean("rememberHistory", false).apply();
    }
    private JSONArray boundedArray(String key) throws JSONException {
        String raw = prefs.getString(key, "[]");
        return new JSONArray(raw != null && raw.length() <= 128_000 ? raw : "[]");
    }
}
