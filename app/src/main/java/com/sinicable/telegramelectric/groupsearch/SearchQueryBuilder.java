package com.sinicable.telegramelectric.groupsearch;

import java.util.List;

public final class SearchQueryBuilder {
    public List<String> build(String query) { return new GroupSuggestionEngine().suggest(query); }
    public String publicQuery(String query) {
        String clean = PersianNormalizer.normalize(query);
        if (PersianNormalizer.topic(clean).length() < 2) return "";
        return PersianNormalizer.contains(clean, "گروه") ? clean : build(clean).get(0);
    }
}
