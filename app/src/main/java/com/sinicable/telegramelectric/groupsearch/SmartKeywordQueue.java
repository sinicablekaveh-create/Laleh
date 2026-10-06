package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SmartKeywordQueue {
    public List<String> build(String keyword, List<String> related) {
        Set<String> queue = new LinkedHashSet<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queue.add(normalize(keyword));
        }
        if (related != null) {
            for (String item : related) {
                if (item != null && !item.trim().isEmpty()) {
                    queue.add(normalize(item));
                }
            }
        }
        return new ArrayList<>(queue);
    }

    private String normalize(String value) {
        return value.trim().replace('‌', ' ').replaceAll("\\s+", " ");
    }
}
