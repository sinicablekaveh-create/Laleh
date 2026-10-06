package com.sinicable.telegramelectric.groupsearch;

public class QueryNormalizer {

    public String normalize(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .replace("ي", "ی")
                .replace("ك", "ک")
                .replaceAll("\\s+", " ");
    }
}
