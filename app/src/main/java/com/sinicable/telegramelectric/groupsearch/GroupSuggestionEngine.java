package com.sinicable.telegramelectric.groupsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GroupSuggestionEngine {
    public List<String> suggest(String query) {
        String topic = PersianNormalizer.topic(query);
        Set<String> result = new LinkedHashSet<>();
        if (topic.length() < 2) return new ArrayList<>();
        result.add("گروه " + topic);
        result.add(topic + " گروه");
        for (String phrase : new GroupKeywordBank().related(topic)) result.add(phrase + " گروه");
        for (String location : GroupKeywordBank.LOCATIONS) {
            if (!PersianNormalizer.contains(topic, location)) continue;
            String category = (" " + topic + " ").replace(" " + location + " ", " ").trim();
            if (!category.isEmpty()) {
                result.add("گروه " + location + " " + category);
                result.add(location + " گروه " + category);
            }
        }
        String[] words = topic.split(" ");
        if (words.length > 2) result.add("گروه " + words[0] + " " + words[1]);
        return new ArrayList<>(result).subList(0, Math.min(12, result.size()));
    }
}
