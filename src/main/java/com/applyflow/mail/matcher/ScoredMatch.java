package com.applyflow.mail.matcher;

/**
 * @param exact true when the match rests on a hard identifier (same reference, same posting URL or the same job
 *              title) rather than on company-level similarity
 */
public record ScoredMatch(Long applicationId, String companyName, String jobTitle, double score, String reason,
                          boolean exact) {
}
