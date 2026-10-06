package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates the intelligent group discovery pipeline while keeping TDLib behind a gateway.
 */
public final class GroupSearchService {

    public interface Gateway {
        void search(String query, GatewayCallback callback);
    }

    public interface GatewayCallback {
        void onResults(List<GroupSearchResult> results);
        void onError(String message);
    }

    public interface Callback {
        void onResults(List<GroupSearchResult> results);
        void onError(String message);
    }

    private final GroupSuggestionEngine suggestionEngine;
    private final GroupFilter groupFilter;
    private final GroupRankingEngine rankingEngine;
    private final QueryNormalizer normalizer;
    private final Gateway gateway;

    public GroupSearchService() {
        this(null);
    }

    public GroupSearchService(Gateway gateway) {
        this.suggestionEngine = new GroupSuggestionEngine();
        this.groupFilter = new GroupFilter();
        this.rankingEngine = new GroupRankingEngine();
        this.normalizer = new QueryNormalizer();
        this.gateway = gateway;
    }

    public List<String> buildQueries(String input) {
        return new ArrayList<>(suggestionEngine.generate(input));
    }

    public boolean shouldKeep(Object chatType) {
        return groupFilter.isGroup(chatType);
    }

    public void search(String input, Callback callback) {
        if (callback == null) {
            return;
        }

        String normalized = normalizer.normalize(input);
        List<String> queries = buildQueries(normalized);
        if (normalized.isEmpty() || queries.isEmpty()) {
            callback.onResults(java.util.Collections.emptyList());
            return;
        }

        if (gateway == null) {
            callback.onError("لایه اتصال جستجوی تلگرام پیکربندی نشده است.");
            return;
        }

        Map<String, GroupSearchResult> merged =
                java.util.Collections.synchronizedMap(new LinkedHashMap<>());
        AtomicInteger pending = new AtomicInteger(queries.size());
        AtomicReference<String> firstError = new AtomicReference<>("");

        for (String query : queries) {
            gateway.search(query, new GatewayCallback() {
                @Override
                public void onResults(List<GroupSearchResult> results) {
                    if (results != null) {
                        synchronized (merged) {
                            for (GroupSearchResult result : results) {
                                if (result == null) continue;
                                int intentScore = rankingEngine.calculateScore(result.getTitle(), normalized);
                                int score = Math.max(result.getScore(), intentScore);
                                GroupSearchResult ranked = new GroupSearchResult(
                                        result.getChatId(),
                                        result.getTitle(),
                                        result.getUsername(),
                                        result.getLink(),
                                        result.getMemberCount(),
                                        score
                                );
                                String key = result.getChatId() != 0L
                                        ? "id:" + result.getChatId()
                                        : "text:" + result.getTitle() + "|" + result.getUsername();
                                GroupSearchResult previous = merged.get(key);
                                if (previous == null || ranked.getScore() > previous.getScore()) {
                                    merged.put(key, ranked);
                                }
                            }
                        }
                    }
                    finishOne(callback, merged, pending, firstError);
                }

                @Override
                public void onError(String message) {
                    if (message != null && !message.trim().isEmpty()) {
                        firstError.compareAndSet("", message.trim());
                    }
                    finishOne(callback, merged, pending, firstError);
                }
            });
        }
    }

    private static void finishOne(
            Callback callback,
            Map<String, GroupSearchResult> merged,
            AtomicInteger pending,
            AtomicReference<String> firstError
    ) {
        if (pending.decrementAndGet() != 0) {
            return;
        }

        List<GroupSearchResult> results;
        synchronized (merged) {
            results = new ArrayList<>(merged.values());
        }
        results.sort(
                Comparator.comparingInt(GroupSearchResult::getScore).reversed()
                        .thenComparing(GroupSearchResult::getTitle, String.CASE_INSENSITIVE_ORDER)
        );

        if (results.isEmpty() && !firstError.get().isEmpty()) {
            callback.onError(firstError.get());
        } else {
            callback.onResults(java.util.Collections.unmodifiableList(results));
        }
    }
}
