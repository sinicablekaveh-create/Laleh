package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.List;

public class GroupSuggestionEngine {

    private final QueryNormalizer normalizer = new QueryNormalizer();

    public List<String> generate(String query) {
        String normalized = normalizer.normalize(query);
        List<String> suggestions = new ArrayList<>();

        if (normalized.isEmpty()) {
            return suggestions;
        }

        suggestions.add(normalized + " گروه");
        suggestions.add("گروه " + normalized);

        String[] parts = normalized.split(" ");
        if (parts.length > 1) {
            suggestions.add("گروه " + String.join(" ", parts));
        }

        return suggestions;
    }
}
