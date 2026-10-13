package com.hooshikadev.app;

public final class PhoneNormalizer {
    private PhoneNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '۰' && c <= '۹') c = (char) ('0' + c - '۰');
            if (c >= '٠' && c <= '٩') c = (char) ('0' + c - '٠');
            if (c >= '0' && c <= '9') digits.append(c);
        }
        String value = digits.toString();
        if (value.startsWith("0098")) value = value.substring(2);
        if (value.startsWith("09") && value.length() == 11) value = "98" + value.substring(1);
        if (value.startsWith("9") && value.length() == 10) value = "98" + value;
        if (value.length() < 10 || value.length() > 15) return "";
        return "+" + value;
    }
}
