package com.hub.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Shared normalization helpers for lexical search and configured filename patterns. */
public final class SearchText {
    private SearchText() {}

    public static List<String> terms(String query) {
        String normalized = UnicodeText.nfc(query == null ? "" : query).trim();
        if (normalized.isBlank()) return List.of();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(normalized);
        for (String token : normalized.split("[^\\p{L}\\p{N}_./#-]+")) {
            String value = token.trim();
            if (value.length() >= 2) terms.add(value);
            if (terms.size() >= 8) break;
        }
        return List.copyOf(terms);
    }

    public static String lower(String value) {
        return UnicodeText.nfc(value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    /** Converts a user/admin filename glob to a bound SQL LIKE value. Only '*' is a wildcard. */
    public static String globLike(String pattern) {
        String normalized = lower(pattern);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*') out.append('%');
            else if (c == '!') out.append("!!");
            else if (c == '%') out.append("!%");
            else if (c == '_') out.append("!_");
            else out.append(c);
        }
        return out.toString();
    }
}
