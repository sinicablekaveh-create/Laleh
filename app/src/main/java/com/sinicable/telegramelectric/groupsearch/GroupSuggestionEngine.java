package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GroupSuggestionEngine {

    private final QueryNormalizer normalizer = new QueryNormalizer();

    public List<String> generate(String query) {
        String normalized = normalizer.normalize(query);
        Set<String> suggestions = new LinkedHashSet<>();

        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        suggestions.add(normalized);
        suggestions.add(normalized + " گروه");
        suggestions.add("گروه " + normalized);

        String[] parts = normalized.split(" ");
        if (parts.length > 1) {
            suggestions.add(parts[0] + " گروه");
            String tail = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
            if (!tail.isEmpty()) {
                suggestions.add(tail + " گروه");
            }
        }

        return new ArrayList<>(suggestions);
    }
}
