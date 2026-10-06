package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A finite, resumable pass through every word and its shortening stages. */
public final class SmartSearchQueue {
    private static final String PREFS = "smart_search_queue";
    private static final String KEY_STATE = "state_json";
    private static final String KEY_MAX_STAGES = "max_stages";
    private static final String KEY_MIN_LENGTH = "min_trailing_length";
    private static final String KEY_NEXT_RESULT = "next_result";
    private static final String KEY_CURRENT_WORD = "current_word";
    private static final String RESULT_PREFIX = "result_";
    private static final Logger LOG = Logger.getLogger("WordSearchQueue");

    public static final class SearchTask {
        public final String originalWord;
        public final String query;
        public final int stage;
        public final int totalStages;

        SearchTask(String originalWord, String query, int stage, int totalStages) {
            this.originalWord = originalWord;
            this.query = query;
            this.stage = stage;
            this.totalStages = totalStages;
        }
    }

    private static final class State {
        int completedStages;
        int successes;
        int failures;
        int score = 100;
    }

    private final SharedPreferences prefs;
    private final WordBank wordBank;
    private final Map<String, State> states = new HashMap<>();
    private List<String> orderedWords;
    private SmartSearchQueue.SearchTask active;
    private int maxStages;
    private int minTrailingLength;
    private long nextResult;
    private String currentWord;

    public SmartSearchQueue(Context context, WordBank wordBank) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.wordBank = wordBank;
        maxStages = clamp(prefs.getInt(KEY_MAX_STAGES, 5), 1, 100);
        minTrailingLength = clamp(prefs.getInt(KEY_MIN_LENGTH, 3), 2, 96);
        nextResult = Math.max(0, prefs.getLong(KEY_NEXT_RESULT, 0));
        currentWord = WordBank.normalize(prefs.getString(KEY_CURRENT_WORD, ""));
        load();
        syncWords();
    }

    public synchronized SearchTask nextTask() {
        syncWords();
        if (active != null) return active;
        if (!currentWord.isEmpty()) {
            active = taskFor(currentWord);
            if (active != null) return active;
            currentWord = "";
        }
        for (String word : orderedWords) {
            active = taskFor(word);
            if (active == null) continue;
            currentWord = word;
            // Pin this word across edits, automatic additions, retries and process recreation.
            persist(null);
            return active;
        }
        return null;
    }

    private SearchTask taskFor(String word) {
        State state = states.get(word);
        if (state == null) return null;
        List<String> stages = stagesFor(word, maxStages, minTrailingLength);
        if (state.completedStages >= stages.size()) return null;
        return new SearchTask(word, stages.get(state.completedStages),
                state.completedStages + 1, stages.size());
    }

    public synchronized boolean isCurrent(SearchTask task) {
        syncWords();
        return active == task && task != null;
    }

    /** Invalidates late callbacks, leaving the interrupted stage pending for resume. */
    public synchronized void cancelPending() { active = null; }

    public synchronized boolean recordResult(SearchTask task, boolean success, int newGroups,
                                             int totalGroups, String error, List<Long> resultIds,
                                             boolean retryStage) {
        if (!isCurrent(task)) return false;
        State state = states.get(task.originalWord);
        if (success) {
            state.successes++;
            state.score = clamp(state.score + Math.max(0, newGroups) * 18, 10, 1000);
            wordBank.recordSearchFeedback(task.query, newGroups, totalGroups);
        } else {
            state.failures++;
        }
        if (!retryStage) state.completedStages = task.stage;
        currentWord = state.completedStages >= task.totalStages ? "" : task.originalWord;
        WordSearchResult result = new WordSearchResult(task.originalWord, task.query, task.stage,
                System.currentTimeMillis(), success, newGroups, totalGroups, error, resultIds);
        active = null;
        persist(result);
        return true;
    }

    public synchronized void restart() {
        syncWords();
        active = null;
        currentWord = "";
        for (State state : states.values()) state.completedStages = 0;
        persist(null);
    }

    public synchronized void configure(int stages, int minLength) {
        if (stages < 1 || stages > 100 || minLength < 2 || minLength > 96) {
            throw new IllegalArgumentException("مراحل باید ۱ تا ۱۰۰ و حد طول باید ۲ تا ۹۶ باشد.");
        }
        if (stages == maxStages && minLength == minTrailingLength) return;
        maxStages = stages;
        minTrailingLength = minLength;
        restart();
    }

    public synchronized int getMaxStages() { return maxStages; }
    public synchronized int getMinimumLength() { return minTrailingLength; }

    public synchronized int size() {
        syncWords();
        return states.size();
    }

    public synchronized int completedWordCount() {
        syncWords();
        int count = 0;
        for (String word : orderedWords) {
            if (states.get(word).completedStages >= stagesFor(word, maxStages, minTrailingLength).size()) count++;
        }
        return count;
    }

    public synchronized List<WordSearchResult> getHistory() { return getHistory(0, Integer.MAX_VALUE); }

    /** Newest-first history page. Every result is retained, including failed attempts. */
    public synchronized List<WordSearchResult> getHistory(int offset, int limit) {
        List<WordSearchResult> results = new ArrayList<>();
        if (offset < 0 || limit <= 0) return results;
        for (long index = nextResult - 1L - offset; index >= 0 && results.size() < limit; index--) {
            String raw = prefs.getString(RESULT_PREFIX + index, null);
            if (raw == null) continue;
            try {
                results.add(WordSearchResult.fromJson(new JSONObject(raw)));
            } catch (JSONException error) {
                LOG.log(Level.WARNING, "Could not load search result " + index, error);
            }
        }
        return results;
    }

    public synchronized long getHistoryCount() { return nextResult; }

    public static List<String> stagesFor(String rawWord, int maxStages, int minTrailingLength) {
        String word = WordBank.normalize(rawWord);
        if (word.isEmpty() || maxStages <= 0) return Collections.emptyList();
        int split = word.lastIndexOf(' ');
        String prefix = split < 0 ? "" : word.substring(0, split + 1);
        String tail = split < 0 ? word : word.substring(split + 1);
        List<String> stages = new ArrayList<>();
        for (int i = 0; i < maxStages; i++) {
            stages.add(prefix + tail);
            int count = tail.codePointCount(0, tail.length());
            if (count <= Math.max(2, minTrailingLength)) break;
            tail = tail.substring(0, tail.offsetByCodePoints(0, count - 1));
        }
        return stages;
    }

    private void syncWords() {
        List<String> current = new ArrayList<>(wordBank.allWords());
        Collections.sort(current);
        if (current.equals(orderedWords)) return;
        orderedWords = current;
        Set<String> wanted = new HashSet<>(current);
        states.keySet().retainAll(wanted);
        for (String word : current) states.computeIfAbsent(word, ignored -> new State());
        if (active != null && !wanted.contains(active.originalWord)) active = null;
        if (!wanted.contains(currentWord)) currentWord = "";
        persist(null);
    }

    private void load() {
        String raw = prefs.getString(KEY_STATE, "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String word = WordBank.normalize(item.optString("word"));
                if (word.isEmpty()) continue;
                State state = new State();
                // Legacy score-only state starts a staged pass without losing prior feedback.
                state.completedStages = Math.max(0, item.optInt("completedStages", 0));
                state.successes = Math.max(0, item.optInt("successes", 0));
                state.failures = Math.max(0, item.optInt("failures", 0));
                state.score = clamp(item.optInt("score", 100), 10, 1000);
                states.put(word, state);
            }
        } catch (JSONException error) {
            LOG.log(Level.WARNING, "Could not load staged queue; restarting saved bank", error);
        }
    }

    private void persist(WordSearchResult result) {
        try {
            JSONArray array = new JSONArray();
            for (String word : orderedWords) {
                State state = states.get(word);
                JSONObject item = new JSONObject();
                item.put("word", word);
                item.put("completedStages", state.completedStages);
                item.put("successes", state.successes);
                item.put("failures", state.failures);
                item.put("score", state.score);
                array.put(item);
            }
            SharedPreferences.Editor editor = prefs.edit().putString(KEY_STATE, array.toString())
                    .putInt(KEY_MAX_STAGES, maxStages).putInt(KEY_MIN_LENGTH, minTrailingLength)
                    .putString(KEY_CURRENT_WORD, currentWord);
            if (result != null) {
                editor.putString(RESULT_PREFIX + nextResult, result.toJson().toString());
                nextResult++;
                editor.putLong(KEY_NEXT_RESULT, nextResult);
            }
            editor.apply();
        } catch (JSONException error) {
            LOG.log(Level.SEVERE, "Could not persist search progress", error);
            throw new IllegalStateException("ذخیره پیشرفت جستجو ناموفق بود.", error);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
