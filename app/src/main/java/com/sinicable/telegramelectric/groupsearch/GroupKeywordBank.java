package com.sinicable.telegramelectric.groupsearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sinicable.telegramelectric.SearchQuery;

public final class GroupKeywordBank {
    private final Map<String, List<String>> words = new HashMap<>();

    public GroupKeywordBank() {
        words.put("برق", Arrays.asList("برق صنعتی", "تابلو برق", "مهندسی برق", "PLC", "الکتریکی"));
        words.put("کابل", Arrays.asList("سیم و کابل", "کابل برق", "کابل فشار ضعیف"));
        words.put("انرژی خورشیدی", Arrays.asList("پنل خورشیدی", "نیروگاه خورشیدی", "برق خورشیدی"));
    }

    public List<String> related(String keyword) {
        SearchQuery query = SearchQuery.parse(keyword);
        String key = query.category.startsWith("برق") || query.category.equals("تابلو برق")
                ? "برق" : query.category.equals("سیم و کابل") ? "کابل" : query.category;
        List<String> result = new java.util.ArrayList<>();
        for (String word : words.getOrDefault(key, Collections.emptyList())) {
            result.add(query.location.isEmpty() ? word : word + " " + query.location);
        }
        return Collections.unmodifiableList(result);
    }
}
