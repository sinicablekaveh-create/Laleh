package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WordBank {
    private static final String PREFS = "electrical_word_bank";
    private static final String KEY_WORDS = "words";

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "این", "اون", "آن", "برای", "با", "از", "به", "در", "رو", "را", "که", "یک",
            "روی", "های", "است", "هست", "بود", "شود", "شده", "کرد", "کن", "اگر", "اما",
            "the", "and", "for", "with", "from", "this", "that", "are", "was", "were",
            "have", "has", "will", "you", "your", "our", "not"
    ));

    private static final String[] SEED_WORDS = {
            "برق", "برقکار", "برقی", "سیم", "کابل", "فیوز", "کلید", "پریز", "تابلو",
            "تابلوبرق", "ولتاژ", "جریان", "آمپر", "وات", "توان", "مقاومت", "اهم", "رله",
            "کنتاکتور", "ترانس", "ترانسفورماتور", "موتور", "الکتروموتور", "ارت", "زمین",
            "نول", "فاز", "مدار", "روشنایی", "لامپ", "چراغ", "داکت", "لوله", "سنسور",
            "اینورتر", "درایو", "ژنراتور", "ups", "plc", "mcb", "mccb", "rcd", "rcbo",
            "wire", "cable", "fuse", "breaker", "switch", "socket", "outlet", "panel",
            "voltage", "current", "ampere", "watt", "power", "resistance", "relay",
            "contactor", "transformer", "motor", "ground", "grounding", "earth",
            "neutral", "phase", "circuit", "lighting", "sensor", "inverter", "generator"
    };

    private final SharedPreferences prefs;
    private final Set<String> words = new HashSet<>();

    public WordBank(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_WORDS, null);
        if (saved != null && !saved.isEmpty()) {
            words.addAll(new HashSet<>(saved));
        } else {
            for (String seed : SEED_WORDS) {
                words.add(normalize(seed));
            }
            persist();
        }
    }

    public synchronized boolean add(String rawWord) {
        String word = normalize(rawWord);
        if (!isCandidate(word)) return false;
        boolean added = words.add(word);
        if (added) persist();
        return added;
    }

    public synchronized int learnFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) return 0;

        String normalized = normalize(message);
        String[] rawTokens = normalized.split("[^\\p{L}\\p{N}_-]+");
        List<String> tokens = new ArrayList<>();

        for (String token : rawTokens) {
            String value = normalize(token);
            if (!value.isEmpty()) tokens.add(value);
        }
        if (tokens.isEmpty()) return 0;

        Set<Integer> anchors = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (words.contains(tokens.get(i))) anchors.add(i);
        }
        if (anchors.isEmpty()) return 0;

        int added = 0;
        for (int anchor : anchors) {
            int start = Math.max(0, anchor - 2);
            int end = Math.min(tokens.size() - 1, anchor + 2);
            for (int i = start; i <= end; i++) {
                String candidate = tokens.get(i);
                if (isCandidate(candidate) && words.add(candidate)) {
                    added++;
                }
            }
        }

        if (added > 0) persist();
        return added;
    }

    public synchronized List<String> search(String query) {
        String q = normalize(query == null ? "" : query);
        List<String> result = new ArrayList<>();

        for (String word : words) {
            if (q.isEmpty() || word.contains(q)) {
                result.add(word);
            }
        }

        Collections.sort(result);
        return result;
    }

    public synchronized int size() {
        return words.size();
    }

    private boolean isCandidate(String word) {
        if (word == null || word.length() < 2 || word.length() > 32) return false;
        if (STOP_WORDS.contains(word)) return false;
        return !word.matches("\\d+");
    }

    private void persist() {
        prefs.edit().putStringSet(KEY_WORDS, new HashSet<>(words)).apply();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replaceAll("[\\u064B-\\u065F\\u0670]", "");
    }
}
