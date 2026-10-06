package com.applyflow.intelligence;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.mail.parser.ParsedEmail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static com.applyflow.common.EmailClassification.*;
import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClassificationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private final RuleBasedEmailIntelligenceService service =
            new RuleBasedEmailIntelligenceService(null, ZoneId.of("Asia/Kolkata"));

    private static ParsedEmail mail(String name, String from, String subject, String body) {
        return ParsedEmail.simple(name, from, subject, body, NOW);
    }

    private static ParsedEmail bulk(String name, String from, String subject, String body) {
        return new ParsedEmail(null, null, null, null, java.util.List.of(), null, from, name, null, subject, NOW,
                body, null, true, null, false);
    }

    static Stream<Arguments> samples() {
        return Stream.of(
                // ---------------- confirmations
                Arguments.of("greenhouse confirmation", mail("Stripe", "no-reply@greenhouse.io",
                        "Thank you for applying to Stripe",
                        "Hi Abhinay,\n\nThanks for applying to Stripe! We have received your application for the "
                                + "Backend Engineer, Payments role. Our team will review your application and if your "
                                + "skills and experience match, we will reach out about next steps, which may include "
                                + "an interview.\n\nBest,\nStripe Recruiting"), APPLICATION_CONFIRMATION),
                Arguments.of("amazon jobs confirmation", mail("Amazon Jobs", "no-reply@amazon.jobs",
                        "Thank you for applying to Amazon",
                        "Dear Abhinay,\nThank you for your application for Software Development Engineer (Job ID: "
                                + "2865412). We've received your application and will review it shortly."),
                        APPLICATION_CONFIRMATION),
                Arguments.of("linkedin application sent", bulk("LinkedIn", "jobs-noreply@linkedin.com",
                        "Abhinay, your application was sent to Atlassian",
                        "Your application was sent to Atlassian\nSenior Java Developer\nAtlassian - Bengaluru\n\n"
                                + "Jobs you may be interested in\nSoftware Engineer at Uber"),
                        APPLICATION_CONFIRMATION),
                Arguments.of("workday confirmation", mail("Salesforce Careers", "salesforce@myworkday.com",
                        "Application Received - Software Engineer (JR123456)",
                        "Thank you for submitting your application for the Software Engineer position. "
                                + "We are reviewing applications and will contact you if your qualifications match."),
                        APPLICATION_CONFIRMATION),
                // ---------------- rejections
                Arguments.of("plain rejection", mail("Microsoft Careers", "no-reply@greenhouse.io",
                        "Update on your application for Java Developer",
                        "Dear Abhinay,\nThank you for your interest in the Java Developer position at Microsoft. "
                                + "After careful consideration, we have decided not to move forward with your "
                                + "application at this time. We wish you the best in your job search."), REJECTION),
                Arguments.of("rejection after interview", mail("Netflix Recruiting", "recruiting@netflix.com",
                        "Your interview with Netflix",
                        "Hi Abhinay,\nThank you for interviewing with us for the Senior Software Engineer role. "
                                + "Unfortunately, we will not be moving forward with your candidacy. We appreciated "
                                + "the time you spent with the team."), REJECTION),
                Arguments.of("position filled", mail("Uber Talent", "talent@uber.com",
                        "Regarding your application",
                        "Hello,\nWe regret to inform you that the position has been filled. We will keep your resume "
                                + "on file for future opportunities."), REJECTION),
                Arguments.of("lever rejection", mail("Wellfound Co", "no-reply@hire.lever.co",
                        "Your application to Razorpay",
                        "Hi Abhinay, thanks for your interest in Razorpay. Unfortunately, we've decided to move "
                                + "forward with other candidates whose experience more closely matches the role."),
                        REJECTION),
                // ---------------- interviews
                Arguments.of("interview invite", mail("Google Careers", "careers-noreply@google.com",
                        "Interview Invitation: Backend Software Engineer",
                        "Hi Abhinay,\nWe'd like to invite you to interview for the Backend Software Engineer role "
                                + "in Bangalore. Please share your availability for a 45-minute technical interview "
                                + "next week."), INTERVIEW_INVITATION),
                Arguments.of("calendly scheduling", mail("Priya Sharma", "priya.sharma@flipkart.com",
                        "Next steps - SDE II",
                        "Hi Abhinay, great news! The hiring manager would like to schedule a call with you. "
                                + "Please pick a time that works for you here: https://calendly.com/priya-flipkart/30min"),
                        INTERVIEW_INVITATION),
                Arguments.of("interview reminder", mail("Atlassian Recruiting", "noreply@greenhouse-mail.io",
                        "Interview reminder: Senior Java Developer",
                        "This is a reminder that your interview is confirmed for Thursday, October 8, 2026 at "
                                + "11:00 AM IST via Zoom."), INTERVIEW_UPDATE),
                // ---------------- assessments
                Arguments.of("hackerrank OA", mail("HackerRank", "support@hackerrankforwork.com",
                        "Adobe invites you to take the Online Assessment",
                        "Hi Abhinay,\nAdobe has invited you to complete a coding assessment for the Software "
                                + "Engineer role. The test link expires on October 12, 2026. Please complete within "
                                + "7 days."), ASSESSMENT),
                Arguments.of("codility test", mail("Codility", "no-reply@codility.com",
                        "Your Codility test for Deloitte",
                        "You have been invited by Deloitte to take a coding test. Click the test link to start. "
                                + "The invitation expires in 5 days."), ASSESSMENT),
                Arguments.of("take-home assignment", mail("Accenture Talent Acquisition", "careers@accenture.com",
                        "Next step: technical assessment for Cloud Engineer",
                        "Dear candidate, as the next step in our hiring process please complete the technical "
                                + "assessment. Kindly submit the take-home assignment by October 6, 2026."),
                        ASSESSMENT),
                // ---------------- offers
                Arguments.of("offer", mail("Salesforce Recruiting", "recruiting@salesforce.com",
                        "Your offer from Salesforce",
                        "Hi Abhinay,\nWe are delighted to offer you the position of Senior Software Engineer. "
                                + "Please find attached your offer letter with the compensation package details."),
                        OFFER),
                Arguments.of("verbal offer", mail("Ravi Kumar", "ravi.kumar@infosys.com",
                        "Great news!",
                        "Hi Abhinay, following our conversation, I am happy to confirm a verbal offer for the "
                                + "Technology Analyst role. The formal offer letter will follow shortly."), OFFER),
                // ---------------- recruiter outreach
                Arguments.of("recruiter outreach", mail("Sarah Lee", "sarah.lee@gmail.com",
                        "Exciting opportunity at Uber",
                        "Hi Abhinay, I came across your profile on LinkedIn and was impressed by your background "
                                + "in distributed systems. I'm a technical recruiter hiring for a Senior Backend "
                                + "Engineer role at Uber. Would you be open to a quick chat this week?"),
                        RECRUITER_CONTACT),
                Arguments.of("withdrawal", mail("Workday", "accenture@myworkday.com",
                        "Withdrawal confirmation",
                        "You have withdrawn your application for the Associate Software Engineer position."),
                        WITHDRAWAL),
                Arguments.of("under review update", mail("Deloitte Careers", "careers@deloitte.com",
                        "Application status update",
                        "Dear Abhinay, your application for the Analyst role is currently under review by the "
                                + "hiring manager. We will contact you with next steps."), APPLICATION_UPDATE),
                // ---------------- negatives
                Arguments.of("amazon order shipped", bulk("Amazon.in", "shipment-tracking@amazon.in",
                        "Your Amazon.in order #403-1234567 has shipped",
                        "Hello Abhinay, your package has shipped and is out for delivery soon. Track your package. "
                                + "Order total Rs. 1,299."), NOT_JOB_RELATED),
                Arguments.of("bank otp", mail("HDFC Bank", "alerts@hdfcbank.net",
                        "OTP for transaction",
                        "Your OTP for the transaction of INR 2,000 on your HDFC Bank credit card is 482910. "
                                + "Do not share it with anyone."), NOT_JOB_RELATED),
                Arguments.of("linkedin job alert", bulk("LinkedIn Job Alerts", "jobalerts-noreply@linkedin.com",
                        "30+ new jobs for Java Developer in Bengaluru",
                        "Jobs you may be interested in: Java Developer at Infosys, Backend Engineer at Swiggy. "
                                + "Apply now. Easy Apply."), NOT_JOB_RELATED),
                Arguments.of("naukri recommended jobs", bulk("Naukri", "info@naukri.com",
                        "Recommended jobs for you",
                        "Jobs based on your profile: Senior Java Developer at TCS. Apply now."), NOT_JOB_RELATED),
                Arguments.of("github notification", mail("GitHub", "notifications@github.com",
                        "[applyflow/backend] Fix flaky test (#42)",
                        "@abhinay commented on this pull request. Merged #42 into main. Build failed on workflow run."),
                        NOT_JOB_RELATED),
                Arguments.of("aws billing", mail("Amazon Web Services", "aws-billing@amazon.com",
                        "Your AWS Billing Statement is available",
                        "Your Amazon Web Services billing statement for September is now available. Total: $12.40."),
                        NOT_JOB_RELATED),
                Arguments.of("special offer promo", bulk("Myntra", "offers@myntra.com",
                        "Special offer just for you: 60% off",
                        "Limited time offer! Shop now and get flat 60% off. Use coupon code SAVE60. Offer ends "
                                + "tonight. Free shipping on all orders."), NOT_JOB_RELATED),
                Arguments.of("newsletter", bulk("The Pragmatic Engineer", "newsletter@substack.com",
                        "This week in tech: AI agents and hiring trends",
                        "Read more in this week's newsletter. Hiring trends in big tech, interview loops and what it "
                                + "means for engineers. Unsubscribe."), NOT_JOB_RELATED),
                Arguments.of("security alert", mail("Google", "no-reply@accounts.google.com",
                        "Security alert",
                        "New sign-in to your Google Account on Windows. If this was you, you don't need to do "
                                + "anything."), NOT_JOB_RELATED),
                Arguments.of("coding test marketing", bulk("Udemy", "deals@udemy.com",
                        "Ace your coding test with 85% off",
                        "Big sale! Prepare for the coding test of your dreams. Buy now with discount. Limited time."),
                        NOT_JOB_RELATED),
                Arguments.of("personal mail", mail("Mom", "mom.family@gmail.com",
                        "Dinner on Sunday?",
                        "Hi beta, are you coming home for dinner on Sunday? Let me know."), NOT_JOB_RELATED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void classifiesSamples(String name, ParsedEmail email, EmailClassification expected) {
        ClassificationResult result = service.classifyEmail(email);
        assertThat(result.classification())
                .as("%s → reason: %s, signals: %s", name, result.reason(), result.signals())
                .isEqualTo(expected);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        if (expected != NOT_JOB_RELATED) {
            assertThat(result.reason()).isNotBlank();
        }
    }

    @Test
    void confidentForClearAtsConfirmation() {
        ClassificationResult r = service.classifyEmail(mail("Stripe", "no-reply@greenhouse.io",
                "Thank you for applying to Stripe", "We have received your application for Backend Engineer."));
        assertThat(r.confidence()).isGreaterThanOrEqualTo(0.75);
        assertThat(r.reason()).contains("Greenhouse");
    }

    @Test
    void mapsStatuses() {
        ClassificationResult update = service.classifyEmail(mail("Deloitte Careers", "careers@deloitte.com",
                "Application status update", "Your application is under review by the hiring manager."));
        assertThat(service.detectStatus(update)).contains(ApplicationStatus.UNDER_REVIEW);
        ClassificationResult rejection = service.classifyEmail(mail("Microsoft Careers", "no-reply@greenhouse.io",
                "Your application", "Unfortunately we have decided not to move forward with your application."));
        assertThat(service.detectStatus(rejection)).contains(ApplicationStatus.REJECTED);
    }
}
