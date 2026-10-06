package com.applyflow.mail.classifier;

import java.util.regex.Pattern;

/** A weighted indicator phrase matched on word boundaries against lower-cased text. */
public record Phrase(String text, double weight, Pattern pattern) {

    public static Phrase of(String text, double weight) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        // Word-ish boundaries that also work for phrases starting/ending with punctuation.
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(lower) + "(?![a-z0-9])");
        return new Phrase(lower, weight, p);
    }

    /** Phrase defined by a raw regex (already lower-case). */
    public static Phrase regex(String label, String regex, double weight) {
        return new Phrase(label, weight, Pattern.compile(regex));
    }

    public boolean matches(String lowerText) {
        return lowerText != null && !lowerText.isEmpty() && pattern.matcher(lowerText).find();
    }
}
