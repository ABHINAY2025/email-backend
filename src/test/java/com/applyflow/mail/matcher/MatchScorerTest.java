package com.applyflow.mail.matcher;

import com.applyflow.mail.classifier.TitleExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private static MatchCandidate app(long id, String company, String title, String ref, boolean active, int daysAgo) {
        return new MatchCandidate(id, company, company.toLowerCase(), company.toLowerCase() + ".com", title,
                TitleExtractor.normalize(title), ref, null, null, NOW.minusSeconds(daysAgo * 86400L), active);
    }

    private static MatchQuery query(String company, String title, String ref) {
        return new MatchQuery(company == null ? null : company.toLowerCase(), null,
                title == null ? null : TitleExtractor.normalize(title), ref, null, "noreply@x.com", NOW);
    }

    @Test
    void sameCompanyAndTitleAutoLinks() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Google", "Re: Senior Backend Software Engineer", null),
                List.of(app(1, "Google", "Backend Software Engineer", null, true, 5)));
        assertThat(s.get(0).score()).isGreaterThanOrEqualTo(MatchResult.AUTO_LINK_THRESHOLD);
        assertThat(s.get(0).exact()).isTrue();
    }

    @Test
    void referenceMatchWinsEvenAcrossCompanies() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query(null, null, "REQ-48213"),
                List.of(app(7, "Acme", "Engineer", "REQ-48213", true, 200)));
        assertThat(s.get(0).score()).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void identicalTitleDoesNotOverrideDifferentReferences() {
        // Two JPMorgan rejections for "Software Engineer I" under different job numbers are two applications.
        List<ScoredMatch> s = MatchScorer.scoreAll(query("JPMorgan", "Software Engineer I", "210742917"),
                List.of(app(3, "JPMorgan", "Software Engineer I", "210721822", false, 34)));
        assertThat(s.get(0).score()).isLessThan(MatchResult.SUGGEST_THRESHOLD);
        assertThat(s.get(0).exact()).isFalse();
    }

    @Test
    void differentReferencesNeverAutoLink() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Amazon", "Software Development Engineer", "2865412"),
                List.of(app(2, "Amazon", "Software Development Engineer II, AWS", "2799013", true, 10)));
        assertThat(s.get(0).score()).isLessThan(MatchResult.SUGGEST_THRESHOLD);
    }

    @Test
    void singleActiveApplicationWithoutTitleIsAutoLinkedWhenRecent() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Stripe", null, null),
                List.of(app(3, "Stripe", "Backend Engineer", null, true, 7),
                        app(4, "Stripe", "Infra Engineer", null, false, 100)));
        assertThat(s.get(0).applicationId()).isEqualTo(3L);
        assertThat(s.get(0).score()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void severalActiveApplicationsWithoutTitleNeedReview() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Amazon", null, null),
                List.of(app(5, "Amazon", "SDE", null, true, 2), app(6, "Amazon", "SDE II", null, true, 20)));
        assertThat(s).hasSize(2);
        assertThat(s.get(0).score()).isBetween(MatchResult.SUGGEST_THRESHOLD, MatchResult.AUTO_LINK_THRESHOLD - 0.01);
    }

    @Test
    void differentTitleAtSameCompanyIsNotAMatch() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Deloitte", "Analyst, Technology Consulting", null),
                List.of(app(8, "Deloitte", "Software Engineer", null, true, 20)));
        assertThat(s.get(0).score()).isLessThan(MatchResult.SUGGEST_THRESHOLD);
    }

    @Test
    void genericTitleOverlapWithClosedApplicationIsNotAMatch() {
        List<ScoredMatch> s = MatchScorer.scoreAll(query("Uber", "Software Engineer II", null),
                List.of(app(10, "Uber", "Backend Engineer", null, false, 50)));
        assertThat(s.get(0).score()).isLessThan(MatchResult.SUGGEST_THRESHOLD);
    }

    @Test
    void otherCompanyScoresZero() {
        assertThat(MatchScorer.scoreAll(query("Netflix", "Software Engineer", null),
                List.of(app(9, "Uber", "Software Engineer", null, true, 1)))).isEmpty();
    }

    @Test
    void titleSimilarity() {
        assertThat(MatchScorer.titleSimilarity("software engineer", "software engineer")).isEqualTo(1.0);
        assertThat(MatchScorer.titleSimilarity("backend engineer", "backend engineer payments")).isGreaterThanOrEqualTo(0.85);
        assertThat(MatchScorer.titleSimilarity("data analyst", "software engineer")).isZero();
    }
}
