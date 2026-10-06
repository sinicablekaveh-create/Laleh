package com.sinicable.telegramelectric.groupsearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GroupKeywordBank {
    private final Map<String, List<String>> words = new HashMap<>();

    public GroupKeywordBank() {
        words.put("برق", Arrays.asList("برق صنعتی", "تابلو برق", "مهندسی برق", "PLC", "الکتریکی"));
    }

    public List<String> related(String keyword) {
        return words.getOrDefault(keyword, Collections.emptyList());
    }
}
