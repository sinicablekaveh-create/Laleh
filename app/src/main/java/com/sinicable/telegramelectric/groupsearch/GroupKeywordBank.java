package com.sinicable.telegramelectric.groupsearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public final class GroupKeywordBank {
    public static final List<String> ELECTRICAL = Collections.unmodifiableList(Arrays.asList(
            "برق", "برق صنعتی", "برق ساختمان", "الکتریکی", "تابلو برق"));
    public static final List<String> INDUSTRY = Collections.unmodifiableList(Arrays.asList(
            "صنعت", "صنعت ساختمان", "اتوماسیون صنعتی"));
    public static final List<String> LOCATIONS = Collections.unmodifiableList(Arrays.asList("لاله زار", "بازار برق"));
    public static final List<String> BUSINESS = Collections.unmodifiableList(Arrays.asList(
            "فروشندگان برق", "تامین کنندگان برق", "خدمات برق"));

    /** Only phrases actually present in the query; no unrelated keyword expansion. */
    public List<String> related(String keyword) {
        List<String> result = new ArrayList<>();
        for (List<String> category : Arrays.asList(ELECTRICAL, INDUSTRY, LOCATIONS, BUSINESS)) {
            for (String phrase : category) if (PersianNormalizer.contains(keyword, phrase)) result.add(phrase);
        }
        return result;
    }
}
