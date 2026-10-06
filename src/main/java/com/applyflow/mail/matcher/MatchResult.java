package com.applyflow.mail.matcher;

import java.util.List;

/**
 * Outcome of matching an email against existing applications.
 *
 * @param exact the decision is backed by a thread, reference, posting URL or identical job title
 */
public record MatchResult(Decision decision, Long applicationId, double score, String reason,
                          List<ScoredMatch> suggestions, boolean exact) {

    public static final double AUTO_LINK_THRESHOLD = 0.8;
    public static final double SUGGEST_THRESHOLD = 0.5;

    public enum Decision {
        /** Confident: link the email to {@code applicationId}. */
        LINK,
        /** Plausible matches exist; ask the user (suggestions populated). */
        REVIEW,
        /** No plausible existing application. */
        NO_MATCH
    }

    public static MatchResult link(Long appId, double score, String reason, boolean exact) {
        return new MatchResult(Decision.LINK, appId, score, reason, List.of(), exact);
    }

    public static MatchResult review(List<ScoredMatch> suggestions) {
        ScoredMatch top = suggestions.get(0);
        return new MatchResult(Decision.REVIEW, null, top.score(), top.reason(), List.copyOf(suggestions), false);
    }

    public static MatchResult noMatch() {
        return new MatchResult(Decision.NO_MATCH, null, 0, null, List.of(), false);
    }
}
