package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates group discovery pipeline.
 * TDLib connection is injected later through the existing TelegramClientManager.
 */
public final class GroupSearchService {

    private final GroupSuggestionEngine suggestionEngine;
    private final GroupFilter groupFilter;
    private final GroupRankingEngine rankingEngine;

    public GroupSearchService() {
        this.suggestionEngine = new GroupSuggestionEngine();
        this.groupFilter = new GroupFilter();
        this.rankingEngine = new GroupRankingEngine();
    }

    public List<String> buildQueries(String input) {
        return new ArrayList<>(suggestionEngine.generate(input));
    }

    public boolean shouldKeep(Object chatType) {
        return groupFilter.isGroup(chatType);
    }
}
