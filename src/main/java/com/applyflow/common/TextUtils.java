package com.applyflow.common;

import java.util.Locale;

public final class TextUtils {

    private TextUtils() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    /** Hard cut without ellipsis (for DB column limits). */
    public static String cut(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Collapses whitespace runs to a single space. */
    public static String collapseWhitespace(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    public static String capitalizeWords(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                up = true;
                sb.append(c);
            } else if (up) {
                sb.append(Character.toUpperCase(c));
                up = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
