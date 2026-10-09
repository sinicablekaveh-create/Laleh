package com.sinicable.telegramelectric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure UI selection preserves TDLib discovery relevance order and exact public-link boundaries. */
public final class DiscoveryResults {
    private static final Pattern PUBLIC_LINK = Pattern.compile("https://t\\.me/[a-zA-Z][a-zA-Z0-9_]{4,31}");
    private DiscoveryResults() { }
    public static List<TelegramClientManager.GroupInfo> select(List<TelegramClientManager.GroupInfo> all,
            List<Long> rankedIds, Set<Long> favorites, boolean favoritesOnly, String rawFilter) {
        List<TelegramClientManager.GroupInfo> ordered = all;
        if (rankedIds != null) {
            Map<Long, TelegramClientManager.GroupInfo> byId = new HashMap<>();
            for (TelegramClientManager.GroupInfo group : all) byId.put(group.id, group);
            ordered = new ArrayList<>();
            for (Long id : new LinkedHashSet<>(rankedIds)) {
                TelegramClientManager.GroupInfo group = byId.get(id);
                if (group != null) ordered.add(group);
            }
        }
        String filter = SearchQuery.parse(rawFilter).normalized;
        List<TelegramClientManager.GroupInfo> result = new ArrayList<>();
        for (TelegramClientManager.GroupInfo group : ordered) {
            if (group.link == null || !PUBLIC_LINK.matcher(group.link).matches()) continue;
            if (favoritesOnly && !favorites.contains(group.id)) continue;
            if (!filter.isEmpty() && !(" " + WordBank.normalize(group.title) + " ").contains(" " + filter + " ")) continue;
            result.add(group);
        }
        return result;
    }
}
