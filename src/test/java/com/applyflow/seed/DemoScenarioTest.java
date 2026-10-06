package com.applyflow.seed;

import com.applyflow.intelligence.ClassificationResult;
import com.applyflow.intelligence.ExtractedJobDetails;
import com.applyflow.intelligence.RuleBasedEmailIntelligenceService;
import com.applyflow.mail.parser.ParsedEmail;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo mailbox must be understood by the real rule engine (it is seeded through the real pipeline). */
class DemoScenarioTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final RuleBasedEmailIntelligenceService service = new RuleBasedEmailIntelligenceService(null, ZONE);

    @Test
    void everyDemoMailClassifiesAsExpectedWithConfidenceAboveDefaultThreshold() {
        List<DemoScenario.DemoMail> mails = DemoScenario.build(Clock.fixed(Instant.parse("2026-10-05T06:00:00Z"),
                ZONE), ZONE);
        assertThat(mails).hasSizeGreaterThan(50);
        Set<String> seenThreads = new HashSet<>();
        for (DemoScenario.DemoMail m : mails) {
            ParsedEmail email = ParsedEmail.simple(m.fromName(), m.fromEmail(), m.subject(), m.body(), m.receivedAt());
            ClassificationResult r = service.classifyEmail(email);
            assertThat(r.classification()).as("%s '%s' reason=%s signals=%s", m.key(), m.subject(), r.reason(),
                    r.signals()).isEqualTo(m.expected());
            assertThat(r.confidence()).as("%s '%s' confidence %s signals=%s", m.key(), m.subject(), r.confidence(),
                    r.signals()).isGreaterThanOrEqualTo(0.75);

            if (m.thread() != null && seenThreads.add(m.thread())) {
                ExtractedJobDetails d = service.extractJobDetails(email);
                assertThat(d.companyName()).as("company of %s '%s'", m.key(), m.subject()).isNotNull();
                assertThat(d.jobTitle()).as("title of %s '%s'", m.key(), m.subject()).isNotNull();
            }
        }
    }

    @Test
    void anchorExtractionsAreAccurate() {
        List<DemoScenario.DemoMail> mails = DemoScenario.build(Clock.fixed(Instant.parse("2026-10-05T06:00:00Z"),
                ZONE), ZONE);
        DemoScenario.DemoMail amazonInvite = mails.stream()
                .filter(m -> "amazon-sde".equals(m.thread()) && m.subject().startsWith("Interview")).findFirst()
                .orElseThrow();
        ExtractedJobDetails d = service.extractJobDetails(ParsedEmail.simple(amazonInvite.fromName(),
                amazonInvite.fromEmail(), amazonInvite.subject(), amazonInvite.body(), amazonInvite.receivedAt()));
        assertThat(d.companyName()).isEqualTo("Amazon");
        assertThat(d.jobTitle()).isEqualTo("Software Development Engineer");
        assertThat(d.applicationRef()).isEqualTo("2865412");
        // 3 days after "today" (2026-10-05) at 10:30 IST.
        assertThat(d.scheduledAt()).isEqualTo(Instant.parse("2026-10-08T05:00:00Z"));
        assertThat(d.scheduledIsDeadline()).isFalse();

        DemoScenario.DemoMail accenture = mails.stream()
                .filter(m -> "accenture-ase".equals(m.thread()) && m.subject().startsWith("Online")).findFirst()
                .orElseThrow();
        ExtractedJobDetails a = service.extractJobDetails(ParsedEmail.simple(accenture.fromName(),
                accenture.fromEmail(), accenture.subject(), accenture.body(), accenture.receivedAt()));
        assertThat(a.scheduledIsDeadline()).isTrue();
        assertThat(a.scheduledAt()).isEqualTo(Instant.parse("2026-10-06T18:29:00Z"));
    }
}
