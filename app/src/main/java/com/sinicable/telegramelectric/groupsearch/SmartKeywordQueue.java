package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.sinicable.telegramelectric.SearchQuery;

public final class SmartKeywordQueue {
    public static final int MAX_SUGGESTIONS = 8;
    public List<String> build(String keyword, List<String> related) {
        Set<String> queue = new LinkedHashSet<>();
        add(queue, keyword);
        if (related != null) {
            for (String item : related) {
                if (queue.size() >= MAX_SUGGESTIONS) break;
                add(queue, item);
            }
        }
        return new ArrayList<>(queue);
    }

    private void add(Set<String> values, String raw) {
        String value = SearchQuery.parse(raw).normalized;
        if (value.codePointCount(0, value.length()) >= 2
                && value.codePoints().anyMatch(Character::isLetter)) values.add(value);
    }
}
