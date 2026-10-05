package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class OfflineWordAI {
    static final class Suggestion {
        final String word;
        final int score;

        Suggestion(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }

    static final class Result {
        final int confidence;
        final List<Suggestion> suggestions;

        Result(int confidence, List<Suggestion> suggestions) {
            this.confidence = confidence;
            this.suggestions = suggestions;
        }
    }

    private static final String PREFS = "offline_word_ai";
    private static final String PREFIX = "w_";

    private final SharedPreferences prefs;
    private final Map<String, Integer> learnedWeights = new HashMap<>();

    OfflineWordAI(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    synchronized Result analyze(String text, Set<String> knownWords) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return new Result(0, Collections.emptyList());
        }

        Set<Integer> anchors = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (knownWords.contains(tokens.get(i))) {
                anchors.add(i);
            }
        }

        if (anchors.isEmpty()) {
            return new Result(0, Collections.emptyList());
        }

        Map<String, Integer> scores = new HashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!isCandidate(token) || knownWords.contains(token)) {
                continue;
            }

            int bestDistance = Integer.MAX_VALUE;
            int nearbyAnchors = 0;

            for (int anchor : anchors) {
                int distance = Math.abs(anchor - i);
                if (distance <= 5) {
                    nearbyAnchors++;
                    bestDistance = Math.min(bestDistance, distance);
                }
            }

            if (nearbyAnchors == 0) {
                continue;
            }

            int score = Math.max(1, 7 - bestDistance);
            score += Math.min(4, nearbyAnchors - 1);
            score += Math.min(6, learnedWeights.getOrDefault(token, 0) / 2);
            score += morphologyBonus(token, knownWords);

            int phraseFrequency = 0;
            for (String other : tokens) {
                if (token.equals(other)) phraseFrequency++;
            }
            score += Math.min(3, Math.max(0, phraseFrequency - 1));

            scores.put(token, Math.max(scores.getOrDefault(token, 0), score));

            int updatedWeight = Math.min(
                    50,
                    learnedWeights.getOrDefault(token, 0) + 1 + nearbyAnchors
            );
            learnedWeights.put(token, updatedWeight);
        }

        persistTouched(scores.keySet());

        List<Suggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() >= 5) {
                suggestions.add(new Suggestion(entry.getKey(), entry.getValue()));
            }
        }

        suggestions.sort(Comparator
                .comparingInt((Suggestion s) -> s.score)
                .reversed()
                .thenComparing(s -> s.word));

        if (suggestions.size() > 20) {
            suggestions = new ArrayList<>(suggestions.subList(0, 20));
        }

        int anchorPercent = Math.min(70, anchors.size() * 12);
        int suggestionPercent = Math.min(30, suggestions.size() * 4);
        int confidence = Math.min(100, anchorPercent + suggestionPercent);

        return new Result(confidence, suggestions);
    }

    synchronized int learnedSignalCount() {
        return learnedWeights.size();
    }

    private int morphologyBonus(String token, Set<String> knownWords) {
        int bonus = 0;
        for (String known : knownWords) {
            if (known.length() < 3 || token.length() < 3) continue;

            if (token.startsWith(known) || token.endsWith(known)
                    || known.startsWith(token) || known.endsWith(token)) {
                bonus = Math.max(bonus, 3);
            }

            int common = commonPrefix(token, known);
            if (common >= 4) {
                bonus = Math.max(bonus, 2);
            }
        }

        if (token.matches(".*\\d+(v|kv|a|ma|w|kw|hz|ohm).*")) {
            bonus += 2;
        }

        return Math.min(5, bonus);
    }

    private static int commonPrefix(String a, String b) {
        int length = Math.min(a.length(), b.length());
        int i = 0;
        while (i < length && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private List<String> tokenize(String text) {
        String normalized = normalize(text);
        String[] raw = normalized.split("[^\\p{L}\\p{N}_-]+");
        List<String> result = new ArrayList<>();
        for (String item : raw) {
            String token = normalize(item);
            if (!token.isEmpty()) result.add(token);
        }
        return result;
    }

    private boolean isCandidate(String value) {
        if (value == null || value.length() < 2 || value.length() > 32) return false;
        if (value.matches("\\d+")) return false;
        return !WordBank.isStopWord(value);
    }

    private void load() {
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX) || !(entry.getValue() instanceof Integer)) {
                continue;
            }
            try {
                String encoded = entry.getKey().substring(PREFIX.length());
                String word = new String(
                        Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP),
                        StandardCharsets.UTF_8
                );
                learnedWeights.put(word, (Integer) entry.getValue());
            } catch (Throwable ignored) {
            }
        }
    }

    private void persistTouched(Set<String> words) {
        if (words.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String word : words) {
            String encoded = Base64.encodeToString(
                    word.getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_WRAP
            );
            editor.putInt(PREFIX + encoded, learnedWeights.getOrDefault(word, 0));
        }
        editor.apply();
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
