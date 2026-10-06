package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WordBank {
    private static final String PREFS = "electrical_word_bank";
    private static final String KEY_WORDS = "words";
    private static final String KEY_REMOVED_WORDS = "removed_words";
    private static final String KEY_IRAN_SEED_VERSION = "iran_seed_version";
    private static final int IRAN_SEED_VERSION = 1;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "این", "اون", "آن", "برای", "با", "از", "به", "در", "رو", "را", "که", "یک",
            "روی", "های", "است", "هست", "بود", "شود", "شده", "کرد", "کن", "اگر", "اما",
            "یا", "تا", "هم", "من", "تو", "ما", "شما", "او", "خود", "خیلی", "مثل",
            "the", "and", "for", "with", "from", "this", "that", "are", "was", "were",
            "have", "has", "will", "you", "your", "our", "not", "but", "into", "out",
            "can", "could", "should", "would", "there", "here", "what", "when"
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

    private static final int AUTO_ADD_SCORE = 8;

    private final SharedPreferences prefs;
    private final Set<String> words = new HashSet<>();
    private final Set<String> removedWords = new HashSet<>();
    private final OfflineWordAI offlineAI;

    public WordBank(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        offlineAI = new OfflineWordAI(context);

        Set<String> removed = prefs.getStringSet(KEY_REMOVED_WORDS, Collections.emptySet());
        if (removed != null) {
            for (String value : removed) removedWords.add(normalize(value));
        }

        Set<String> saved = prefs.getStringSet(KEY_WORDS, null);
        if (saved != null) {
            for (String value : saved) {
                String word = normalize(value);
                if (isCandidate(word)) words.add(word);
            }
        }

        boolean changed = false;
        // A saved empty bank is intentional. Never restore words deleted by the user.
        if (saved == null) {
            for (String seed : SEED_WORDS) {
                String word = normalize(seed);
                if (!removedWords.contains(word)) changed |= words.add(word);
            }
        }

        int installedSeedVersion = prefs.getInt(KEY_IRAN_SEED_VERSION, 0);
        if (installedSeedVersion < IRAN_SEED_VERSION) {
            if (saved == null || !saved.isEmpty()) {
                for (String seed : IranElectricalSearchSeeds.build()) {
                    String normalized = normalize(seed);
                    if (isCandidate(normalized) && !removedWords.contains(normalized)) {
                        changed |= words.add(normalized);
                    }
                }
            }
            prefs.edit().putInt(KEY_IRAN_SEED_VERSION, IRAN_SEED_VERSION).apply();
        }

        if (changed || saved == null || !words.equals(saved)) {
            persist();
        }
    }

    public synchronized boolean add(String rawWord) {
        String word = normalize(rawWord);
        if (!isCandidate(word)) return false;
        boolean added = words.add(word);
        if (added) {
            removedWords.remove(word);
            persist();
        }
        return added;
    }

    public synchronized boolean remove(String rawWord) {
        String word = normalize(rawWord);
        boolean removed = words.remove(word);
        if (removed) {
            removedWords.add(word);
            persist();
        }
        return removed;
    }

    public synchronized boolean edit(String oldWord, String newWord) {
        String oldValue = normalize(oldWord);
        String newValue = normalize(newWord);
        if (!words.contains(oldValue) || !isCandidate(newValue)) return false;
        if (oldValue.equals(newValue)) return true;
        if (words.contains(newValue)) return false;
        words.remove(oldValue);
        words.add(newValue);
        removedWords.add(oldValue);
        removedWords.remove(newValue);
        persist();
        return true;
    }

    public synchronized int learnFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) return 0;

        OfflineWordAI.Result result = offlineAI.analyze(
                message,
                new HashSet<>(words)
        );

        if (result.suggestions.isEmpty()) {
            return 0;
        }

        int added = 0;
        for (OfflineWordAI.Suggestion suggestion : result.suggestions) {
            if (suggestion.score < AUTO_ADD_SCORE) {
                continue;
            }

            String candidate = normalize(suggestion.word);
            if (isCandidate(candidate) && !removedWords.contains(candidate) && words.add(candidate)) {
                added++;
            }
        }

        if (added > 0) {
            persist();
        }

        return added;
    }

    public synchronized List<String> search(String query) {
        String q = normalize(query == null ? "" : query);
        List<String> result = new java.util.ArrayList<>();

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

    public synchronized int learnedSignalCount() {
        return offlineAI.learnedSignalCount();
    }

    public synchronized List<String> allWords() {
        List<String> result = new java.util.ArrayList<>(words);
        Collections.sort(result);
        return result;
    }

    public synchronized int searchSignal(String word) {
        return offlineAI.searchSignal(normalize(word));
    }

    public synchronized void recordSearchFeedback(String query, int newGroups, int totalGroups) {
        offlineAI.recordSearchFeedback(normalize(query), newGroups, totalGroups);
    }

    static boolean isStopWord(String value) {
        return STOP_WORDS.contains(normalize(value));
    }

    private boolean isCandidate(String word) {
        if (word == null || word.codePointCount(0, word.length()) < 2
                || word.codePointCount(0, word.length()) > 96) return false;
        if (isStopWord(word)) return false;
        return word.codePoints().anyMatch(Character::isLetter);
    }

    private void persist() {
        prefs.edit().putStringSet(KEY_WORDS, new HashSet<>(words))
                .putStringSet(KEY_REMOVED_WORDS, new HashSet<>(removedWords)).apply();
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("[\\s\\p{Z}]+", " ")
                .trim();
    }
}
