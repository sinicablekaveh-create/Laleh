package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TelegramGroupQueryBuilder {
    public List<String> build(String keyword) {
        Set<String> queries = new LinkedHashSet<>();
        String value = normalize(keyword);
        if (!value.isEmpty()) queries.add(value);
        if (value.contains(" ")) {
            queries.add(value.replace(" ", ""));
        }
        return new ArrayList<>(queries);
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.trim()
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
    }
}
