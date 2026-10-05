package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SmartSearchQueue {
    private static final String PREFS = "smart_search_queue";
    private static final String KEY_STATE = "state_json";
    private static final long COOLDOWN_MS = 20L * 60L * 1000L;

    private final SharedPreferences prefs;
    private final WordBank wordBank;
    private final Map<String, State> states = new HashMap<>();
    private int syncedWordCount = -1;

    public SmartSearchQueue(Context context, WordBank wordBank) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.wordBank = wordBank;
        load();
        syncWords();
    }

    public synchronized String nextQuery() {
        syncWords();
        if (states.isEmpty()) return null;

        long now = System.currentTimeMillis();
        String best = null;
        double bestValue = -Double.MAX_VALUE;
        boolean foundOutsideCooldown = false;

        for (Map.Entry<String, State> entry : states.entrySet()) {
            String word = entry.getKey();
            State state = entry.getValue();
            boolean cooling = state.lastUsedAt > 0 && now - state.lastUsedAt < COOLDOWN_MS;

            double value = state.score
                    + Math.min(80, wordBank.searchSignal(word) * 2.0)
                    + Math.min(30, state.successes * 3.0)
                    - Math.min(50, state.failures * 2.0);

            if (cooling) value -= 250;

            if (!cooling) {
                if (!foundOutsideCooldown || value > bestValue) {
                    foundOutsideCooldown = true;
                    best = word;
                    bestValue = value;
                }
            } else if (!foundOutsideCooldown && value > bestValue) {
                best = word;
                bestValue = value;
            }
        }

        if (best != null) {
            State state = states.get(best);
            state.lastUsedAt = now;
        }

        return best;
    }

    public synchronized void recordResult(String query, int newGroups, int totalGroups) {
        if (query == null || query.trim().isEmpty()) return;
        syncWords();

        State state = states.computeIfAbsent(query, ignored -> new State());
        if (newGroups > 0) {
            state.successes += newGroups;
        } else {
            state.failures++;
        }

        int delta = Math.min(120, Math.max(0, newGroups) * 18)
                + Math.min(25, Math.max(0, totalGroups) * 3)
                - (newGroups <= 0 ? 12 : 0);

        state.score = clamp(state.score + delta, 10, 1000);
        wordBank.recordSearchFeedback(query, newGroups, totalGroups);
        // Persist the selection timestamp and its result together. Serializing in
        // nextQuery() as well would cause two full JSON writes per search cycle.
        persist();
    }

    public synchronized int size() {
        syncWords();
        return states.size();
    }

    public synchronized int scoreFor(String query) {
        State state = states.get(query);
        if (state == null) return 0;
        return state.score + wordBank.searchSignal(query) * 2;
    }

    private void syncWords() {
        if (syncedWordCount == wordBank.size()) return;
        List<String> words = wordBank.allWords();
        for (String word : words) {
            if (word == null || word.trim().isEmpty()) continue;
            states.computeIfAbsent(word, ignored -> {
                State state = new State();
                state.score = 100 + Math.min(50, wordBank.searchSignal(word));
                return state;
            });
        }
        syncedWordCount = words.size();
    }

    private void load() {
        String raw = prefs.getString(KEY_STATE, "");
        if (raw == null || raw.isEmpty()) return;

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String word = item.optString("word", "").trim();
                if (word.isEmpty()) continue;

                State state = new State();
                state.score = clamp(item.optInt("score", 100), 10, 1000);
                state.lastUsedAt = item.optLong("lastUsedAt", 0L);
                state.successes = Math.max(0, item.optInt("successes", 0));
                state.failures = Math.max(0, item.optInt("failures", 0));
                states.put(word, state);
            }
        } catch (Throwable ignored) {
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        try {
            for (Map.Entry<String, State> entry : states.entrySet()) {
                State state = entry.getValue();
                JSONObject item = new JSONObject();
                item.put("word", entry.getKey());
                item.put("score", state.score);
                item.put("lastUsedAt", state.lastUsedAt);
                item.put("successes", state.successes);
                item.put("failures", state.failures);
                array.put(item);
            }
            prefs.edit().putString(KEY_STATE, array.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        int score = 100;
        long lastUsedAt = 0L;
        int successes = 0;
        int failures = 0;
    }
}
