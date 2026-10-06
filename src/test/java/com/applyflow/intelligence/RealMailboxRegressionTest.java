package com.applyflow.intelligence;

import com.applyflow.common.EmailClassification;
import com.applyflow.mail.parser.ParsedEmail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Misclassifications observed on a real mailbox sync (personal details removed). */
class RealMailboxRegressionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private final RuleBasedEmailIntelligenceService service =
            new RuleBasedEmailIntelligenceService(null, ZoneId.of("Asia/Kolkata"));

    private static final String ACCENTURE_FRAUD_FOOTER = """
            Note:
            Accenture has not authorized any agency, company or individual to either collect money or arrive on any \
            monetary arrangement in exchange for a job at Accenture. Accenture's criterion for hiring candidates is \
            merit. Any agency, company or individual offering employment with Accenture in exchange for money is \
            misrepresenting their relationship with Accenture, which has not authorized any such action. If you are \
            approached by any entity or individuals who demand money or any other form of compensation in return for \
            a job offer at Accenture's even if they present themselves as representatives or employees of Accenture, \
            please send the details to our business ethics helpline.
            """;

    private static ParsedEmail mail(String name, String from, String subject, String body) {
        return ParsedEmail.simple(name, from, subject, body, NOW);
    }

    private EmailClassification classify(ParsedEmail m) {
        return service.classifyEmail(m).classification();
    }

    @Test
    void accentureConfirmationWithAntiFraudFooterIsNotAnOffer() {
        ParsedEmail m = mail("Accenture Careers", "accenture@myworkday.com",
                "It’s great that you’re interested in Accenture!", """
                Hi Candidate,
                Thanks for applying for the role of Full Stack Engineer. We'll get started on carefully reviewing your \
                application and keep you updated about next steps.
                You can also log in to your candidate home page. Here you can track the status of your application.
                Thanks again for applying to work with Accenture. We'll be in touch with you soon.
                Accenture Recruitment Team
                """ + ACCENTURE_FRAUD_FOOTER);
        assertThat(classify(m)).isEqualTo(EmailClassification.APPLICATION_CONFIRMATION);
        ExtractedJobDetails d = service.extractJobDetails(m);
        assertThat(d.companyName()).isEqualTo("Accenture");
        assertThat(d.jobTitle()).isEqualTo("Full Stack Engineer");
    }

    @Test
    void accentureRoleFilledIsRejection() {
        ParsedEmail m = mail("Accenture Careers", "accenture@myworkday.com", "News on your Accenture Application", """
                Hi Candidate,
                Thanks for your interest in the role of Application Support Engineer at Accenture. We have now filled \
                that role and are not moving forward with your application, but we hope you'll keep looking in the \
                future.
                Wishing you all the best in your hunt for the perfect role,
                Accenture Recruitment Team
                """ + ACCENTURE_FRAUD_FOOTER);
        assertThat(classify(m)).isEqualTo(EmailClassification.REJECTION);
        assertThat(service.extractJobDetails(m).jobTitle()).isEqualTo("Application Support Engineer");
    }

    @Test
    void oracleRecruitingCloudSenderIsAnAtsNotOracle() {
        ParsedEmail m = mail("JPMorgan Chase & Co. Human Resources",
                "eino.fa.sender@workflow.mail.us2.cloud.oracle.com",
                "Your job application status (Job number: 210742917)", """
                Thank you for your interest in the Software Engineer I position at JPMorganChase. We received quite a \
                few applications for this job opportunity, and at this time we're sorry to let you know we're moving \
                forward with other candidates.
                We wish you continued success in your job search,
                JPMorganChase Talent Team
                """);
        assertThat(classify(m)).isEqualTo(EmailClassification.REJECTION);
        ExtractedJobDetails d = service.extractJobDetails(m);
        assertThat(d.companyName()).startsWith("JPMorgan");
        assertThat(d.companyDomain()).isNull();
        assertThat(d.applicationRef()).isEqualTo("210742917");
        assertThat(d.jobTitle()).isEqualTo("Software Engineer I");
    }

    @Test
    void creditCardRejectionIsNotJobRelated() {
        ParsedEmail m = mail("SBM Bank", "info@sbmbank.co.in", "Application Rejection", """
                Dear Customer,
                We regret to inform you that after careful review, your credit card application has been currently \
                declined due to non-fulfilment of KYC norms of the Bank. For any assistance, please write to \
                customer care.
                Regards,
                SBM Bank (India)
                """);
        assertThat(classify(m)).isEqualTo(EmailClassification.NOT_JOB_RELATED);
    }

    @Test
    void talentCommunityPreferencesMailIsNotAnApplication() {
        ParsedEmail m = mail("Qualcomm Careers", "noreply@qualcomm.com",
                "Your Qualcomm Candidate Profile — Tell Us How to Stay in Touch", """
                At Qualcomm, we're always looking for the people behind the next breakthrough. Since you've expressed \
                interest in our work before, we want to make sure you stay connected to the right opportunities.
                We're asking prior applicants to sign in to their Qualcomm Careers Candidate Home Account and confirm \
                how you'd like Qualcomm to contact you about opportunities.
                Roles I've already applied to — Only contact me about roles I have applied to.
                """);
        assertThat(classify(m)).isEqualTo(EmailClassification.NOT_JOB_RELATED);
    }

    @Test
    void bainCompanyAndTitle() {
        ParsedEmail m = mail("Bain & Company Experienced Talent Recruiting Team",
                "experiencedtalent.recruiting@bain.com", "Thank you for applying to Bain & Company", """
                Dear Candidate,
                Thank you very much for your recent application to the Engineer, Software Engineering position at \
                Bain & Co. Your application will be reviewed by our recruiting team, and we will be in touch should \
                your experience meet our needs.
                Sincerely,
                Bain & Company
                """);
        assertThat(classify(m)).isEqualTo(EmailClassification.APPLICATION_CONFIRMATION);
        ExtractedJobDetails d = service.extractJobDetails(m);
        assertThat(d.companyName()).isEqualTo("Bain & Company");
        assertThat(d.jobTitle()).isEqualTo("Engineer, Software Engineering");
    }

    @Test
    void amexConfirmationAndVerificationShareTitleAndReference() {
        ParsedEmail confirmation = mail("Amex Careers", "careers@recruitment.americanexpress.com",
                "Thank you for applying to Apprentice - Enterprise Technology Services - 26009713", """
                Hello Candidate,
                Thank you for applying to the Apprentice - Enterprise Technology Services position. We will review \
                your application.
                Amex Talent Acquisition Team
                """);
        ParsedEmail verify = mail("Amex Careers", "careers@recruitment.americanexpress.com",
                "Please verify your identity for Apprentice - Enterprise Technology Services - 26009713 position", """
                Hello Candidate,
                Thank you for your interest in the Apprentice - Enterprise Technology Services position – 26009713
                To continue with your application, please confirm your identity using the one-time passcode below:
                000000
                This code will expire in 10 minutes.
                Amex Talent Acquisition Team
                """);
        ExtractedJobDetails a = service.extractJobDetails(confirmation);
        ExtractedJobDetails b = service.extractJobDetails(verify);
        assertThat(a.jobTitle()).isEqualTo("Apprentice - Enterprise Technology Services");
        assertThat(b.jobTitle()).isEqualTo(a.jobTitle());
        assertThat(a.applicationRef()).isEqualTo("26009713");
        assertThat(b.applicationRef()).isEqualTo("26009713");
        assertThat(classify(confirmation)).isEqualTo(EmailClassification.APPLICATION_CONFIRMATION);
        assertThat(classify(verify)).isNotEqualTo(EmailClassification.NOT_JOB_RELATED);
    }

    @Test
    void titleWrappedAcrossLinesIsFound() {
        ParsedEmail m = mail("Bain & Company Experienced Talent Recruiting Team",
                "experiencedtalent.recruiting@bain.com", "Thank you for applying to Bain & Company", """
                Thank you very much for your recent application to the Engineer, Software
                Engineering position at Bain & Co. Your application will be reviewed by our recruiting team.
                """);
        assertThat(service.extractJobDetails(m).jobTitle()).isEqualTo("Engineer, Software Engineering");
    }

    @Test
    void narrowNoBreakSpaceDoesNotHideTheTitle() {
        ParsedEmail m = mail("Bain & Company Experienced Talent Recruiting Team",
                "experiencedtalent.recruiting@bain.com", "Thank you for applying to Bain & Company",
                "Thank you very much for your recent application to the Engineer, Software Engineering position "
                        + "at Bain & Co.");
        assertThat(service.extractJobDetails(m).jobTitle()).isEqualTo("Engineer, Software Engineering");
    }

    @Test
    void titleWithoutCompanyPrefix() {
        ParsedEmail m = mail("EY Talent Attraction", "talentattractionandacquisition@ey.com",
                "Thank you for applying to EY", """
                Thank you for your interest in EY and the Java Developer, Associate/Senior Associate, Technology \
                Consulting position.
                """);
        assertThat(service.extractJobDetails(m).jobTitle())
                .isEqualTo("Java Developer, Associate/Senior Associate, Technology Consulting");
    }

    @Test
    void companyNameKeepsSenderSpellingOfDomain() {
        ParsedEmail m = mail("HCA Healthcare", "noreply@hrms.hcahealthcare.com",
                "Senior - Software Engineer(Des_961): Position", """
                Thank you for applying. After careful consideration we have decided not to proceed with your \
                application.
                """);
        ExtractedJobDetails d = service.extractJobDetails(m);
        assertThat(d.companyName()).isEqualTo("HCA Healthcare");
        assertThat(d.jobTitle()).isEqualTo("Software Engineer");
        assertThat(d.applicationRef()).isEqualTo("DES_961");
    }

    @Test
    void withdrawalForAnotherPositionCarriesItsOwnCode() {
        ParsedEmail m = mail("HCA Healthcare", "noreply@hrms.hcahealthcare.com", "Application Withdrawn Successfully",
                "You have successfully withdrawn your application for the Senior - Software Engineer (ITG1) "
                        + "position. Your application will no longer be considered for this opportunity.");
        assertThat(classify(m)).isEqualTo(EmailClassification.WITHDRAWAL);
        assertThat(service.extractJobDetails(m).applicationRef()).isEqualTo("ITG1");
    }

    @Test
    void logoImageIsNotAJobPostingUrl() {
        ParsedEmail m = ParsedEmail.simple("JPMorgan Chase & Co. Human Resources",
                "eino.fa.sender@workflow.mail.us2.cloud.oracle.com",
                "Your job application status (Job number: 210742917)",
                "Thank you for your interest in the Software Engineer I position at JPMorganChase.\n"
                        + "https://careers.jpmorgan.com/content/dam/careers/external/orc-emails/jpmc-logo-200-height-transp.png",
                NOW);
        assertThat(service.extractJobDetails(m).jobUrl()).isNull();
    }

    @Test
    void amazonReferenceIdIsExtracted() {
        ParsedEmail m = mail(null, "noreply@mail.amazon.jobs", "Keep track of your application", """
                Thank you for your interest in SDE-I, Rewards (ID: 10555910).
                If you have completed the application: Great! You can now check the application status here.
                Amazon Recruiting Team
                """);
        assertThat(service.extractJobDetails(m).applicationRef()).isEqualTo("10555910");
    }
}
