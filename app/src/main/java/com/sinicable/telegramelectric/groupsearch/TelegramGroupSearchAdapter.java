package com.sinicable.telegramelectric.groupsearch;

import com.sinicable.telegramelectric.TelegramClientManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter between the intelligent group search layer and the existing TelegramClientManager.
 *
 * It deliberately reuses the application's current TDLib client and authentication lifecycle.
 */
public final class TelegramGroupSearchAdapter implements GroupSearchService.Gateway {

    private final TelegramClientManager manager;
    private final GroupRankingEngine rankingEngine = new GroupRankingEngine();

    public TelegramGroupSearchAdapter(TelegramClientManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("TelegramClientManager is required.");
        }
        this.manager = manager;
    }

    @Override
    public void search(String query, GroupSearchService.GatewayCallback callback) {
        if (callback == null) {
            return;
        }

        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) {
            callback.onResults(java.util.Collections.emptyList());
            return;
        }

        manager.discoverPublicGroupsForReview(clean, new TelegramClientManager.DiscoveryCallback() {
            @Override
            public void onResult(boolean success, int newItems, int totalItems, String message) {
                if (!success) {
                    callback.onError(message == null ? "جستجوی گروه ناموفق بود." : message);
                }
            }

            @Override
            public void onDetailedResult(
                    boolean success,
                    int newItems,
                    int totalItems,
                    String message,
                    List<Long> resultIds
            ) {
                if (!success) {
                    callback.onError(message == null ? "جستجوی گروه ناموفق بود." : message);
                    return;
                }

                Map<Long, TelegramClientManager.GroupInfo> snapshot = new HashMap<>();
                for (TelegramClientManager.GroupInfo info : manager.getFoundGroups()) {
                    snapshot.put(info.id, info);
                }

                List<GroupSearchResult> results = new ArrayList<>();
                if (resultIds != null) {
                    for (Long id : resultIds) {
                        if (id == null) continue;
                        TelegramClientManager.GroupInfo info = snapshot.get(id);
                        if (info == null) continue;
                        results.add(new GroupSearchResult(
                                info.id,
                                info.title,
                                usernameFromLink(info.link),
                                info.link,
                                info.memberCount,
                                rankingEngine.calculateScore(info.title, clean)
                        ));
                    }
                }

                callback.onResults(results);
            }
        });
    }

    private static String usernameFromLink(String link) {
        if (link == null) {
            return "";
        }
        String clean = link.trim();
        String[] prefixes = {
                "https://t.me/",
                "http://t.me/",
                "https://telegram.me/",
                "http://telegram.me/"
        };
        for (String prefix : prefixes) {
            if (!clean.startsWith(prefix)) continue;
            String username = clean.substring(prefix.length());
            int query = username.indexOf('?');
            if (query >= 0) username = username.substring(0, query);
            int slash = username.indexOf('/');
            if (slash >= 0) username = username.substring(0, slash);
            return username.startsWith("+") ? "" : username.trim();
        }
        return "";
    }
}
