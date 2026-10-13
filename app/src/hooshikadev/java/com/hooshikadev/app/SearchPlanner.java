package com.hooshikadev.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class SearchPlanner {
    private SearchPlanner() {}

    public static List<String> queries(String input) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String seed = input == null ? "" : input.trim();
        if (!seed.isEmpty()) terms.add(seed);
        terms.add("برق ساختمان");
        terms.add("لاله زار الکتریکی");
        terms.add("صنعت برق کابل روشنایی");
        List<String> result = new ArrayList<>();
        for (String term : terms) {
            result.add("site:rubika.ir " + term + " گروه");
            result.add("site:rubika.ir " + term + " کانال");
        }
        return result;
    }
}
