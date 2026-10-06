package com.applyflow.seed;

import com.applyflow.common.EmailClassification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.applyflow.common.EmailClassification.*;

/**
 * Realistic demo mailbox. All dates are relative to "now" so the UI always looks fresh. Each mail carries a thread
 * key (mails of one application share a conversation, like real ATS threads) and the classification the rule engine
 * is expected to produce (verified by a unit test).
 */
public final class DemoScenario {

    public record DemoMail(String key, String thread, Instant receivedAt, String fromName, String fromEmail,
                           String subject, String body, EmailClassification expected) {
    }

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final ZoneId zone;
    private final Instant now;
    private final LocalDate today;
    private final List<DemoMail> mails = new ArrayList<>();
    private int seq;

    private DemoScenario(Clock clock, ZoneId zone) {
        this.zone = zone;
        this.now = clock.instant();
        this.today = LocalDate.ofInstant(now, zone);
    }

    public static List<DemoMail> build(Clock clock, ZoneId zone) {
        DemoScenario s = new DemoScenario(clock, zone);
        s.define();
        s.mails.sort(Comparator.comparing(DemoMail::receivedAt));
        return List.copyOf(s.mails);
    }

    private Instant ago(int days, int hour, int minute) {
        Instant t = today.minusDays(days).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant();
        if (t.isAfter(now.minusSeconds(600))) {
            t = now.minusSeconds(600L + 60L * (seq % 50));
        }
        return t;
    }

    private String future(int days) {
        return today.plusDays(days).format(LONG_DATE);
    }

    private String futureShort(int days) {
        return today.plusDays(days).format(SHORT_DATE);
    }

    private void mail(String thread, Instant at, String fromName, String fromEmail, String subject, String body,
                      EmailClassification expected) {
        mails.add(new DemoMail("m" + (++seq), thread, at, fromName, fromEmail, subject, body, expected));
    }

    private void define() {
        // ---------------------------------------------------------------- Amazon – SDE (anchor)
        mail("amazon-sde", ago(18, 9, 12), "Amazon Jobs", "no-reply@amazon.jobs",
                "Thank you for applying to Amazon",
                """
                Hi Abhinay,

                Thank you for applying to Amazon! We have received your application for the Software Development \
                Engineer position (Job ID: 2865412).

                Location: Seattle, WA (Remote)

                Our recruiting team will review your application. If your qualifications match the needs of the \
                role, a recruiter will contact you about next steps.

                Track your application status: https://www.amazon.jobs/en/jobs/2865412/software-development-engineer

                Best regards,
                Amazon Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("amazon-sde", ago(11, 16, 40), "Amazon Jobs", "no-reply@amazon.jobs",
                "Update on your application for Software Development Engineer",
                """
                Hi Abhinay,

                Good news — your application for the Software Development Engineer role (Job ID: 2865412) is \
                currently under review by the hiring team. We will reach out as soon as there is an update.

                Amazon Recruiting
                """, APPLICATION_UPDATE);
        mail("amazon-sde", ago(1, 11, 5), "Amazon Recruiting", "recruiting@amazon.jobs",
                "Interview Invitation: Software Development Engineer",
                """
                Hi Abhinay,

                Congratulations! The hiring team would like to invite you to interview for the Software Development \
                Engineer role (Job ID: 2865412).

                Your virtual onsite interview loop is scheduled for %s at 10:30 AM IST on Amazon Chime. It \
                consists of three 55-minute technical interviews (coding, system design and leadership principles).

                Please reply to confirm your availability or let us know if you need to reschedule.

                Best,
                Amazon Recruiting
                """.formatted(future(3)), INTERVIEW_INVITATION);

        // ---------------------------------------------------------------- Amazon – SDE II AWS
        mail("amazon-aws", ago(24, 10, 2), "Amazon Jobs", "no-reply@amazon.jobs",
                "Thank you for applying to Amazon",
                """
                Hi Abhinay,

                Thank you for applying to Amazon! We have received your application for the Software Development \
                Engineer II, AWS Lambda position (Job ID: 2799013).

                Location: Bengaluru, India

                We will review your application and contact you if your experience matches the role.

                Amazon Recruiting
                """, APPLICATION_CONFIRMATION);
        // Needs review: Amazon recruiter without a job title (two active Amazon applications).
        mail(null, ago(0, 9, 20), "Priya Sharma", "priya.sharma@amazon.com",
                "Quick chat about your Amazon application?",
                """
                Hi Abhinay,

                I'm a technical recruiter at Amazon and I'm reaching out regarding your recent application. Your \
                background in distributed systems caught my attention. Would you be open to a quick call this week \
                to discuss next steps?

                Thanks,
                Priya Sharma
                Technical Recruiter, Amazon
                """, RECRUITER_CONTACT);

        // ---------------------------------------------------------------- Google – Backend SWE (anchor)
        mail("google-backend", ago(30, 8, 45), "Google Careers", "careers-noreply@google.com",
                "Thank you for applying to Google",
                """
                Hi Abhinay,

                Thanks for applying to Google. We have received your application for the Backend Software \
                Engineer position.

                Location: Bangalore, Karnataka, India

                We'll review your application and get in touch if your skills and experience match the role.

                Google Careers
                """, APPLICATION_CONFIRMATION);
        mail("google-backend", ago(9, 14, 30), "Ananya Iyer", "ananya.iyer@google.com",
                "Interview Invitation: Backend Software Engineer",
                """
                Hi Abhinay,

                I'm happy to share that we would like to invite you to interview for the Backend Software Engineer \
                role in Bangalore. The first round will be a 45-minute technical phone screen focused on data \
                structures and algorithms.

                Please share your availability for next week and I'll send a calendar invite.

                Best,
                Ananya Iyer
                Technical Recruiter, Google
                """, INTERVIEW_INVITATION);
        mail("google-backend", ago(2, 17, 15), "Ananya Iyer", "ananya.iyer@google.com",
                "Next round: Backend Software Engineer interview confirmed",
                """
                Hi Abhinay,

                Great job on the phone screen! Your next round (two technical interviews) has been scheduled for \
                %s at 2:00 PM IST over Google Meet: https://meet.google.com/abc-defg-hij

                Let me know if you have any questions.

                Ananya
                """.formatted(future(6)), INTERVIEW_UPDATE);

        // ---------------------------------------------------------------- Google – SRE (+ needs-review update)
        mail("google-sre", ago(14, 12, 10), "Google Careers", "careers-noreply@google.com",
                "Thank you for applying to Google",
                """
                Hi Abhinay,

                Thanks for applying to Google. We have received your application for the Site Reliability \
                Engineer position.

                Location: Hyderabad, Telangana, India

                Google Careers
                """, APPLICATION_CONFIRMATION);
        mail(null, ago(0, 8, 5), "Google Careers", "careers-noreply@google.com",
                "An update on your Google application",
                """
                Hi Abhinay,

                Thank you for your continued interest in Google. Your application has moved to the next stage of \
                review and is currently being reviewed by the hiring committee. A recruiter will reach out soon.

                Google Careers
                """, APPLICATION_UPDATE);

        // ---------------------------------------------------------------- Microsoft – Java Developer (rejected)
        mail("ms-java", ago(40, 10, 0), "Microsoft Careers", "no-reply@greenhouse.io",
                "Thank you for your application to Microsoft",
                """
                Dear Abhinay,

                Thank you for your application for the Java Developer position at Microsoft. We have received your \
                application and our team will review it shortly.

                Location: Hyderabad, India
                Job ID: R-1675530

                Microsoft Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("ms-java", ago(20, 15, 25), "Microsoft Careers", "no-reply@greenhouse.io",
                "Update on your application for Java Developer",
                """
                Dear Abhinay,

                Thank you for your interest in the Java Developer position at Microsoft. After careful \
                consideration, we have decided not to move forward with your application at this time.

                We will keep your resume on file and wish you the best in your job search.

                Microsoft Talent Acquisition
                """, REJECTION);

        // ---------------------------------------------------------------- Microsoft – SWE II (under review)
        mail("ms-swe2", ago(8, 9, 30), "Microsoft Careers", "no-reply@greenhouse.io",
                "Thank you for your application to Microsoft",
                """
                Dear Abhinay,

                Thank you for your application for the Software Engineer II position at Microsoft. We have \
                received your application.

                Location: Redmond, WA (Remote)
                Job ID: R-1702291

                Microsoft Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("ms-swe2", ago(3, 13, 0), "Microsoft Careers", "no-reply@greenhouse.io",
                "Application status update: Software Engineer II",
                """
                Dear Abhinay,

                Your application for the Software Engineer II role is currently under review by the hiring \
                manager. We appreciate your patience.

                Microsoft Talent Acquisition
                """, APPLICATION_UPDATE);

        // ---------------------------------------------------------------- Microsoft – Data Engineer (old rejection)
        mail("ms-data", ago(100, 11, 0), "Microsoft Careers", "no-reply@greenhouse.io",
                "Thank you for your application to Microsoft",
                """
                Dear Abhinay,

                Thank you for your application for the Data Engineer position at Microsoft. We have received your \
                application.

                Location: Bengaluru, India

                Microsoft Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("ms-data", ago(85, 16, 0), "Microsoft Careers", "no-reply@greenhouse.io",
                "Update on your application for Data Engineer",
                """
                Dear Abhinay,

                Unfortunately, we will not be moving forward with your application for the Data Engineer role. \
                The position has been filled.

                Microsoft Talent Acquisition
                """, REJECTION);

        // ---------------------------------------------------------------- Deloitte – just submitted (anchor)
        mail("deloitte-analyst", ago(0, 7, 40), "Deloitte Careers", "careers@deloitte.com",
                "Application received: Analyst, Technology Consulting",
                """
                Dear Abhinay,

                Thank you for applying to Deloitte. We have received your application for the Analyst, Technology \
                Consulting role.

                Location: Hyderabad, India
                Requisition ID: DEL-58213

                Our talent team will review your profile and contact you if there is a match.

                Deloitte Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        // ---------------------------------------------------------------- Deloitte – needs follow-up
        mail("deloitte-swe", ago(22, 10, 15), "Deloitte Careers", "careers@deloitte.com",
                "Application received: Software Engineer",
                """
                Dear Abhinay,

                Thank you for applying to Deloitte. We have received your application for the Software Engineer \
                role.

                Location: Bengaluru, India
                Requisition ID: DEL-57102

                Deloitte Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        // ---------------------------------------------------------------- Deloitte – old process
        mail("deloitte-cloud", ago(85, 9, 0), "Deloitte Careers", "careers@deloitte.com",
                "Application received: Consultant, Cloud Engineering",
                """
                Dear Abhinay,

                Thank you for applying to Deloitte. We have received your application for the Consultant, Cloud \
                Engineering role.

                Location: Mumbai, India

                Deloitte Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("deloitte-cloud", ago(72, 12, 0), "Deloitte Careers", "careers@deloitte.com",
                "Interview invitation: Consultant, Cloud Engineering",
                """
                Dear Abhinay,

                We would like to invite you to interview for the Consultant, Cloud Engineering role. Please share \
                your availability for a video interview with the hiring manager next week.

                Deloitte Talent Acquisition
                """, INTERVIEW_INVITATION);
        mail("deloitte-cloud", ago(60, 15, 0), "Deloitte Careers", "careers@deloitte.com",
                "Your application for Consultant, Cloud Engineering",
                """
                Dear Abhinay,

                Thank you for interviewing with us. Unfortunately, after careful consideration we have decided to \
                move forward with other candidates whose experience more closely matches the role.

                Deloitte Talent Acquisition
                """, REJECTION);

        // ---------------------------------------------------------------- Accenture – assessment due tomorrow (anchor)
        mail("accenture-ase", ago(12, 10, 30), "Accenture Careers", "careers@accenture.com",
                "Thank you for applying to Accenture",
                """
                Dear Abhinay,

                Thank you for applying to Accenture. We have received your application for the Associate Software \
                Engineer role.

                Location: Pune, India

                Accenture Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("accenture-ase", ago(1, 9, 45), "Accenture Talent Acquisition", "careers@accenture.com",
                "Online assessment: Associate Software Engineer",
                """
                Dear Abhinay,

                As the next step in our hiring process for the Associate Software Engineer role, please complete \
                the online assessment (coding + aptitude, 90 minutes).

                Assessment link: https://accenture.mettl.com/test/ase-2026
                Please complete the assessment by %s, 11:59 PM IST.

                Accenture Talent Acquisition
                """.formatted(futureShort(1)), ASSESSMENT);
        // ---------------------------------------------------------------- Accenture – old rejection
        mail("accenture-cloud", ago(55, 11, 0), "Accenture Careers", "careers@accenture.com",
                "Thank you for applying to Accenture",
                """
                Dear Abhinay,

                Thank you for applying to Accenture. We have received your application for the Cloud Engineer role.

                Location: Bengaluru, India

                Accenture Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("accenture-cloud", ago(41, 14, 0), "Accenture Careers", "careers@accenture.com",
                "Regarding your application for Cloud Engineer",
                """
                Dear Abhinay,

                We regret to inform you that we will not be progressing your application for the Cloud Engineer \
                role. We wish you every success in your job search.

                Accenture Talent Acquisition
                """, REJECTION);

        // ---------------------------------------------------------------- Stripe – offer
        mail("stripe-backend", ago(27, 19, 5), "Stripe", "no-reply@greenhouse.io",
                "Thank you for applying to Stripe",
                """
                Hi Abhinay,

                Thanks for applying to Stripe! We have received your application for the Backend Engineer, Payments \
                role. Our team will review your application and, if there is a match, reach out about next steps.

                https://boards.greenhouse.io/stripe/jobs/5512345

                Stripe Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("stripe-backend", ago(15, 18, 20), "Maya Chen", "maya.chen@stripe.com",
                "Stripe: phone screen for Backend Engineer, Payments",
                """
                Hi Abhinay,

                Thanks for your interest in Stripe! I'd love to set up a phone screen with an engineer on the \
                Payments team. Please pick a time that works for you: https://calendly.com/maya-stripe/phone-screen

                Best,
                Maya Chen
                Recruiter, Stripe
                """, INTERVIEW_INVITATION);
        mail("stripe-backend", ago(2, 20, 10), "Maya Chen", "maya.chen@stripe.com",
                "Your offer from Stripe",
                """
                Hi Abhinay,

                On behalf of the whole team, we are delighted to offer you the position of Backend Engineer, \
                Payments at Stripe! Your offer letter with the full compensation package is attached.

                Base salary: $150,000 - $165,000 plus equity. Please let us know if you would like to accept the \
                offer by next Friday.

                Maya
                """, OFFER);
        // ---------------------------------------------------------------- Stripe – infra (rejected)
        mail("stripe-infra", ago(65, 19, 0), "Stripe", "no-reply@greenhouse.io",
                "Thank you for applying to Stripe",
                """
                Hi Abhinay,

                Thanks for applying to Stripe! We have received your application for the Software Engineer, \
                Infrastructure role.

                Stripe Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("stripe-infra", ago(56, 18, 0), "Stripe", "no-reply@greenhouse.io",
                "Your application to Stripe",
                """
                Hi Abhinay,

                Thank you for your interest in Stripe. Unfortunately, we've decided to move forward with other \
                candidates for the Software Engineer, Infrastructure role.

                Stripe Recruiting
                """, REJECTION);

        // ---------------------------------------------------------------- Atlassian – recruiter → interview
        mail("atlassian-java", ago(35, 12, 0), "LinkedIn", "jobs-noreply@linkedin.com",
                "Abhinay, your application was sent to Atlassian",
                """
                Your application was sent to Atlassian

                Senior Java Developer
                Atlassian · Bengaluru, Karnataka, India

                Applied on LinkedIn
                https://www.linkedin.com/jobs/view/3998877665
                """, APPLICATION_CONFIRMATION);
        mail("atlassian-java", ago(20, 11, 30), "Rahul Menon", "rahul.menon@atlassian.com",
                "Your application for Senior Java Developer at Atlassian",
                """
                Hi Abhinay,

                I'm a technical recruiter at Atlassian and I came across your application for the Senior Java \
                Developer role. Your background with Java and Kafka looks like a great fit for our Jira Platform \
                team. Would you be open to a quick chat this week?

                Cheers,
                Rahul Menon
                Talent Partner, Atlassian
                """, RECRUITER_CONTACT);
        mail("atlassian-java", ago(1, 18, 0), "Atlassian Recruiting", "no-reply@greenhouse-mail.io",
                "Interview reminder: Senior Java Developer",
                """
                Hi Abhinay,

                This is a reminder that your technical interview for the Senior Java Developer role is confirmed \
                for %s at 11:00 AM IST via Zoom.

                Atlassian Recruiting
                """.formatted(future(2)), INTERVIEW_UPDATE);
        // ---------------------------------------------------------------- Atlassian – backend (applied)
        mail("atlassian-backend", ago(9, 13, 15), "LinkedIn", "jobs-noreply@linkedin.com",
                "Abhinay, your application was sent to Atlassian",
                """
                Your application was sent to Atlassian

                Software Engineer, Backend
                Atlassian · Remote

                Applied on LinkedIn
                """, APPLICATION_CONFIRMATION);

        // ---------------------------------------------------------------- Netflix – rejected after interview
        mail("netflix-senior", ago(45, 7, 30), "Netflix Recruiting", "recruiting@netflix.com",
                "Thank you for applying to Netflix",
                """
                Hi Abhinay,

                Thank you for applying to Netflix. We have received your application for the Senior Software \
                Engineer role on the Streaming Platform team.

                Location: Remote

                Netflix Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("netflix-senior", ago(30, 21, 0), "Netflix Recruiting", "recruiting@netflix.com",
                "Interview invitation: Senior Software Engineer",
                """
                Hi Abhinay,

                We'd like to invite you to interview for the Senior Software Engineer role. Please share your \
                availability for a 60-minute technical interview with the hiring manager.

                Netflix Recruiting
                """, INTERVIEW_INVITATION);
        mail("netflix-senior", ago(10, 22, 15), "Netflix Recruiting", "recruiting@netflix.com",
                "Your interview with Netflix",
                """
                Hi Abhinay,

                Thank you for interviewing with us for the Senior Software Engineer role. Unfortunately, we will \
                not be moving forward with your candidacy at this time. We truly appreciated the time you spent \
                with the team.

                Netflix Recruiting
                """, REJECTION);
        // ---------------------------------------------------------------- Netflix – just applied
        mail("netflix-ui", ago(2, 8, 0), "Netflix Recruiting", "recruiting@netflix.com",
                "Thank you for applying to Netflix",
                """
                Hi Abhinay,

                Thank you for applying to Netflix. We have received your application for the UI Engineer role.

                Location: Remote

                Netflix Recruiting
                """, APPLICATION_CONFIRMATION);

        // ---------------------------------------------------------------- Uber – recruiter outreach
        mail("uber-swe2", ago(6, 15, 45), "Sarah Lee", "sarah.lee.talent@gmail.com",
                "Exciting opportunity at Uber - Software Engineer II",
                """
                Hi Abhinay,

                I came across your profile on LinkedIn and was impressed by your background in backend systems. I'm \
                a technical recruiter hiring for the Software Engineer II role at Uber in Bengaluru (Payments \
                Platform). Would you be open to a quick chat this week?

                Best,
                Sarah Lee
                """, RECRUITER_CONTACT);
        // ---------------------------------------------------------------- Uber – old rejection (Lever)
        mail("uber-backend", ago(70, 10, 0), "Uber", "no-reply@hire.lever.co",
                "Thank you for applying to Uber",
                """
                Hi Abhinay,

                Thanks for applying to Uber! We have received your application for the Backend Engineer role.

                https://jobs.lever.co/uber/7b1e2c3d-4f5a-6b7c-8d9e-0f1a2b3c4d5e

                Uber Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("uber-backend", ago(60, 9, 0), "Uber", "no-reply@hire.lever.co",
                "Your application to Uber",
                """
                Hi Abhinay,

                Thanks for your interest in Uber. Unfortunately, we have decided not to proceed with your \
                application for the Backend Engineer role.

                Uber Recruiting
                """, REJECTION);

        // ---------------------------------------------------------------- Flipkart – OA → interview
        mail("flipkart-sde2", ago(16, 10, 0), "Naukri", "applications@naukri.com",
                "Your application for SDE II at Flipkart has been submitted",
                """
                Dear Abhinay,

                Your application has been submitted successfully.

                Job title: SDE II
                Company: Flipkart
                Location: Bengaluru

                Naukri.com
                """, APPLICATION_CONFIRMATION);
        mail("flipkart-sde2", ago(8, 12, 0), "HackerRank", "support@hackerrankforwork.com",
                "Flipkart invites you to take the SDE II coding assessment",
                """
                Hi Abhinay,

                Flipkart has invited you to complete a coding assessment for the SDE II role. The test contains 3 \
                questions and must be completed within 3 days.

                Test link: https://www.hackerrank.com/tests/flipkart-sde2

                HackerRank
                """, ASSESSMENT);
        mail("flipkart-sde2", ago(3, 16, 30), "Neha Gupta", "neha.gupta@flipkart.com",
                "Next steps - SDE II interview",
                """
                Hi Abhinay,

                Congratulations on clearing the coding assessment! The hiring manager would like to schedule an \
                interview with you. Please pick a slot that works for you: https://calendly.com/neha-flipkart/sde2

                Regards,
                Neha Gupta
                Talent Acquisition, Flipkart
                """, INTERVIEW_INVITATION);
        // ---------------------------------------------------------------- Flipkart – backend developer
        mail("flipkart-backend", ago(5, 11, 20), "Naukri", "applications@naukri.com",
                "Your application for Backend Developer at Flipkart has been submitted",
                """
                Dear Abhinay,

                Your application has been submitted successfully.

                Job title: Backend Developer
                Company: Flipkart
                Location: Bengaluru

                Naukri.com
                """, APPLICATION_CONFIRMATION);

        // ---------------------------------------------------------------- Salesforce – offer (Workday)
        mail("salesforce-swe", ago(50, 9, 0), "Salesforce Careers", "salesforce@myworkday.com",
                "Application Received - Software Engineer (JR245566)",
                """
                Thank you for submitting your application for the Software Engineer position at Salesforce.

                Location: Hyderabad, India

                We are reviewing applications and will contact you if your qualifications match.
                """, APPLICATION_CONFIRMATION);
        mail("salesforce-swe", ago(40, 13, 0), "Salesforce Recruiting", "recruiting@salesforce.com",
                "Technical assessment for Software Engineer",
                """
                Hi Abhinay,

                As the next step, please complete the technical assessment (take-home assignment) for the Software \
                Engineer role within 5 days. Assessment link: https://salesforce.codesignal.com/test/se-2026

                Salesforce Recruiting
                """, ASSESSMENT);
        mail("salesforce-swe", ago(30, 14, 0), "Salesforce Recruiting", "recruiting@salesforce.com",
                "Interview invitation: Software Engineer",
                """
                Hi Abhinay,

                Great work on the assessment! We would like to invite you to interview for the Software Engineer \
                role. The virtual onsite includes a technical interview and a hiring manager interview.

                Salesforce Recruiting
                """, INTERVIEW_INVITATION);
        mail("salesforce-swe", ago(5, 18, 30), "Salesforce Recruiting", "recruiting@salesforce.com",
                "Your offer from Salesforce",
                """
                Hi Abhinay,

                We are delighted to offer you the position of Software Engineer at Salesforce! Please find your \
                offer letter attached with the compensation package details. CTC: ₹ 32 - 36 LPA.

                Salesforce Recruiting
                """, OFFER);
        // ---------------------------------------------------------------- Salesforce – MTS (stale under review)
        mail("salesforce-mts", ago(33, 10, 0), "Salesforce Careers", "salesforce@myworkday.com",
                "Application Received - Member of Technical Staff (JR247781)",
                """
                Thank you for submitting your application for the Member of Technical Staff position at Salesforce.

                Location: Bengaluru, India
                """, APPLICATION_CONFIRMATION);
        mail("salesforce-mts", ago(25, 12, 0), "Salesforce Careers", "salesforce@myworkday.com",
                "Application status update - Member of Technical Staff",
                """
                Your application for the Member of Technical Staff position is currently under review by the \
                hiring team.
                """, APPLICATION_UPDATE);

        // ---------------------------------------------------------------- Adobe – assessment pending
        mail("adobe-swe", ago(20, 11, 0), "Adobe Careers", "careers@adobe.com",
                "Thank you for applying to Adobe",
                """
                Hi Abhinay,

                Thank you for applying to Adobe. We have received your application for the Software Engineer role \
                on the Adobe Express team.

                Location: Noida, India

                Adobe Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("adobe-swe", ago(4, 10, 0), "HackerRank", "support@hackerrankforwork.com",
                "Adobe invites you to take the Software Engineer online assessment",
                """
                Hi Abhinay,

                Adobe has invited you to complete an online assessment for the Software Engineer role. The test \
                link expires on %s.

                Test link: https://www.hackerrank.com/tests/adobe-se

                HackerRank
                """.formatted(futureShort(5)), ASSESSMENT);
        // ---------------------------------------------------------------- Adobe – old rejection
        mail("adobe-cs", ago(75, 11, 0), "Adobe Careers", "careers@adobe.com",
                "Thank you for applying to Adobe",
                """
                Hi Abhinay,

                Thank you for applying to Adobe. We have received your application for the Computer Scientist \
                role.

                Location: Bengaluru, India

                Adobe Talent Acquisition
                """, APPLICATION_CONFIRMATION);
        mail("adobe-cs", ago(66, 12, 0), "Adobe Careers", "careers@adobe.com",
                "Update on your application for Computer Scientist",
                """
                Hi Abhinay,

                After careful review, we have decided to move forward with other candidates for the Computer \
                Scientist role. We wish you the best in your job search.

                Adobe Talent Acquisition
                """, REJECTION);

        // ---------------------------------------------------------------- Infosys – withdrawn
        mail("infosys-ta", ago(60, 10, 0), "Naukri", "applications@naukri.com",
                "Your application for Technology Analyst at Infosys has been submitted",
                """
                Dear Abhinay,

                Your application has been submitted successfully.

                Job title: Technology Analyst
                Company: Infosys
                Location: Pune

                Naukri.com
                """, APPLICATION_CONFIRMATION);
        mail("infosys-ta", ago(35, 15, 0), "Infosys Careers", "careers@infosys.com",
                "Withdrawal confirmation: Technology Analyst",
                """
                Dear Abhinay,

                This confirms that you have withdrawn your application for the Technology Analyst position. We wish \
                you all the best.

                Infosys Talent Acquisition
                """, WITHDRAWAL);
        // ---------------------------------------------------------------- Infosys – applied
        mail("infosys-sse", ago(4, 9, 0), "Infosys Careers", "careers@infosys.com",
                "Thank you for applying to Infosys",
                """
                Dear Abhinay,

                Thank you for applying to Infosys. We have received your application for the Senior Systems \
                Engineer role.

                Location: Mysuru, India

                Infosys Talent Acquisition
                """, APPLICATION_CONFIRMATION);

        // ---------------------------------------------------------------- Amazon – old rejection
        mail("amazon-support", ago(90, 9, 0), "Amazon Jobs", "no-reply@amazon.jobs",
                "Thank you for applying to Amazon",
                """
                Hi Abhinay,

                Thank you for applying to Amazon! We have received your application for the Cloud Support Associate \
                position (Job ID: 2401188).

                Location: Hyderabad, India

                Amazon Recruiting
                """, APPLICATION_CONFIRMATION);
        mail("amazon-support", ago(80, 10, 0), "Amazon Jobs", "no-reply@amazon.jobs",
                "Update on your application for Cloud Support Associate",
                """
                Hi Abhinay,

                Thank you for your interest in the Cloud Support Associate position (Job ID: 2401188). After \
                careful consideration, we have decided not to proceed with your application.

                Amazon Recruiting
                """, REJECTION);
    }
}
