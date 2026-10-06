package com.applyflow.intelligence;

import com.applyflow.mail.classifier.CompanyNames;
import com.applyflow.mail.parser.ParsedEmail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private final RuleBasedEmailIntelligenceService service =
            new RuleBasedEmailIntelligenceService(null, ZoneId.of("Asia/Kolkata"));

    private ExtractedJobDetails details(String name, String from, String subject, String body) {
        return service.extractJobDetails(ParsedEmail.simple(name, from, subject, body, NOW));
    }

    @Test
    void companyFromAtsDisplayNameNeverUsesAtsItself() {
        ExtractedJobDetails d = details("Amazon Recruiting", "no-reply@greenhouse.io", "Your application",
                "Thanks for your application.");
        assertThat(d.companyName()).isEqualTo("Amazon");
        assertThat(d.companyDomain()).isNull();
        assertThat(d.source()).isEqualTo("Greenhouse");
    }

    @Test
    void companyFromSubjectPhrase() {
        ExtractedJobDetails d = details("No Reply", "noreply@notifications.example-ats.com",
                "Thank you for applying to Razorpay", "We received your application for Backend Engineer.");
        assertThat(d.companyName()).isEqualTo("Razorpay");
    }

    @Test
    void companyFromGreenhouseAndLeverSlugs() {
        assertThat(details("Notifications", "no-reply@greenhouse.io", "Application received",
                "View the job: https://boards.greenhouse.io/acme-labs/jobs/12345").companyName()).isEqualTo("Acme Labs");
        assertThat(details("Notifications", "no-reply@hire.lever.co", "Application received",
                "https://jobs.lever.co/zepto/1b2c3d4e-aaaa-bbbb-cccc-1234567890ab").companyName()).isEqualTo("Zepto");
        assertThat(details("Workday", "noreply@myworkday.com", "Application received",
                "Sign in: https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite").companyName())
                .isEqualTo("Nvidia");
    }

    @Test
    void companyFromSenderDomainStripsSubdomainsAndIgnoresGenericProviders() {
        assertThat(details("Careers", "jobs@mail.careers.swiggy.in", "Thanks for applying", "Hi")
                .companyName()).isEqualTo("Swiggy");
        assertThat(details("John Smith", "john.smith@gmail.com", "Hello", "Hi there")
                .companyName()).isNull();
    }

    @Test
    void jobTitleFromCommonPatterns() {
        assertThat(details("Stripe", "no-reply@greenhouse.io", "Thank you for applying to Stripe",
                "We have received your application for the Backend Engineer, Payments role.").jobTitle())
                .isEqualTo("Backend Engineer, Payments");
        assertThat(details("Google Careers", "careers-noreply@google.com",
                "Interview Invitation: Backend Software Engineer", "Hi").jobTitle())
                .isEqualTo("Backend Software Engineer");
        assertThat(details("Deloitte Careers", "careers@deloitte.com",
                "Application received - Data Analyst (R-12345)", "Thanks").jobTitle()).isEqualTo("Data Analyst");
        assertThat(details("Acme", "jobs@acme.io", "Hello", "Position: Senior Platform Engineer\nLocation: Remote")
                .jobTitle()).isEqualTo("Senior Platform Engineer");
        assertThat(details("Recruiter", "r@acme.io", "Re: Senior Java Developer role at Acme", "Hi").jobTitle())
                .isEqualTo("Senior Java Developer");
        assertThat(details("Microsoft Careers", "no-reply@greenhouse.io", "Thank you for your application",
                "Thank you for your application for the Data Engineer position at Microsoft.").jobTitle())
                .isEqualTo("Data Engineer");
        ExtractedJobDetails linkedIn = details("LinkedIn", "jobs-noreply@linkedin.com",
                "Abhinay, your application was sent to Atlassian",
                "Your application was sent to Atlassian\n\nSenior Java Developer\nAtlassian · Bengaluru, India\n");
        assertThat(linkedIn.companyName()).isEqualTo("Atlassian");
        assertThat(linkedIn.jobTitle()).isEqualTo("Senior Java Developer");
        assertThat(linkedIn.location()).isEqualTo("Bengaluru, India");
    }

    @Test
    void referenceLocationSalaryAndEmploymentType() {
        ExtractedJobDetails d = details("Acme Careers", "careers@acme.io", "Application received",
                """
                Thank you for applying for the Software Engineer position (Requisition ID: REQ-48213).
                Location: Bengaluru, India
                This is a full-time role with a salary range of $120,000 - $150,000.
                https://boards.greenhouse.io/acme/jobs/998877?gh_src=abc
                """);
        assertThat(d.applicationRef()).isEqualTo("REQ-48213");
        assertThat(d.location()).isEqualTo("Bengaluru, India");
        assertThat(d.employmentType()).isEqualTo("Full-time");
        assertThat(d.salaryMin()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(d.salaryMax()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(d.salaryCurrency()).isEqualTo("USD");
        assertThat(d.jobUrl()).isEqualTo("https://boards.greenhouse.io/acme/jobs/998877");

        ExtractedJobDetails inr = details("HR", "hr@acme.in", "Offer", "CTC offered: ₹ 18 LPA");
        assertThat(inr.salaryCurrency()).isEqualTo("INR");
        assertThat(inr.salaryMin()).isEqualByComparingTo(new BigDecimal("1800000"));
        assertThat(details("x", "x@acme.io", "Ref", "Your Job ID is JR123456").applicationRef()).isEqualTo("JR123456");
    }

    @Test
    void scheduledDates() {
        ExtractedJobDetails interview = details("Acme", "r@acme.io", "Interview",
                "Your interview is on Monday, October 12, 2026 at 10:00 AM PST.");
        assertThat(interview.scheduledAt()).isEqualTo(Instant.parse("2026-10-12T17:00:00Z"));
        assertThat(interview.scheduledIsDeadline()).isFalse();

        ExtractedJobDetails relative = details("HackerRank", "support@hackerrank.com", "Assessment",
                "Please complete within 7 days.");
        assertThat(relative.scheduledIsDeadline()).isTrue();
        assertThat(relative.scheduledAt()).isAfter(NOW.plusSeconds(6 * 86400));

        ExtractedJobDetails noYear = details("Acme", "r@acme.io", "Interview", "Let's meet Oct 14 at 3pm IST.");
        assertThat(noYear.scheduledAt()).isEqualTo(Instant.parse("2026-10-14T09:30:00Z"));
    }

    @Test
    void summariesAndActions() {
        ParsedEmail invite = ParsedEmail.simple("Amazon Recruiting", "recruiting@amazon.jobs",
                "Interview Invitation: Software Development Engineer",
                "We would like to invite you to interview. Your interview is scheduled for October 12, 2026 at "
                        + "10:00 AM IST.", NOW);
        ClassificationResult cr = service.classifyEmail(invite);
        EmailSummary s = service.summarizeEmail(invite, cr);
        assertThat(s.summary()).isEqualTo("Amazon invited you to an interview for Software Development Engineer on "
                + "Oct 12, 2026 at 10:00 AM.");
        assertThat(s.actionRequired()).isTrue();

        ParsedEmail rejection = ParsedEmail.simple("Microsoft Careers", "no-reply@greenhouse.io",
                "Your application for Java Developer",
                "Unfortunately, we have decided not to move forward with your application.", NOW);
        EmailSummary r = service.summarizeEmail(rejection, service.classifyEmail(rejection));
        assertThat(r.actionRequired()).isFalse();
        assertThat(r.actionText()).isEqualTo("No action required.");
    }

    @Test
    void companyNormalization() {
        assertThat(CompanyNames.normalize("Amazon.com, Inc.")).isEqualTo("amazon");
        assertThat(CompanyNames.normalize("Infosys Limited")).isEqualTo("infosys");
        assertThat(CompanyNames.registrableDomain("mail.careers.amazon.co.uk")).isEqualTo("amazon.co.uk");
    }
}
