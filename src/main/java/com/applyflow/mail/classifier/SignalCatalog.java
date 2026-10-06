package com.applyflow.mail.classifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted indicator phrases. Weights: ~1.0 = unambiguous, ~0.6 = strong in job context, ~0.3 = weak.
 * Subject matches are boosted by the classifier.
 */
public final class SignalCatalog {

    private SignalCatalog() {
    }

    private static List<Phrase> list(Object... pairs) {
        List<Phrase> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(Phrase.of((String) pairs[i], ((Number) pairs[i + 1]).doubleValue()));
        }
        return List.copyOf(out);
    }

    public static final List<Phrase> CONFIRMATION = list(
            "thank you for applying", 1.0,
            "thanks for applying", 1.0,
            "thank you for your application", 1.0,
            "thanks for your application", 1.0,
            "thank you for submitting your application", 1.0,
            "we have received your application", 1.0,
            "we've received your application", 1.0,
            "we received your application", 1.0,
            "your application has been received", 1.0,
            "your application was received", 1.0,
            "has received your application", 1.0,
            "your application was sent", 1.0,
            "your application has been submitted", 1.0,
            "your application was submitted", 1.0,
            "successfully submitted your application", 1.0,
            "you have successfully applied", 1.0,
            "you successfully applied", 1.0,
            "application received", 0.9,
            "application submitted", 0.9,
            "application confirmation", 0.9,
            "confirming your application", 0.9,
            "confirmation of your application", 0.9,
            "indeed application", 0.8,
            "we will review your application", 0.7,
            "will be reviewing your application", 0.7,
            "our team will review", 0.5,
            "our recruiting team will", 0.4,
            "if your qualifications match", 0.6,
            "if your skills and experience match", 0.6,
            "if your experience matches", 0.6,
            "if there is a match", 0.5,
            "if your profile matches", 0.6,
            "thank you for your interest in", 0.5,
            "thanks for your interest in", 0.5,
            "your application for", 0.5,
            "your application to", 0.5,
            "you applied for", 0.6,
            "you applied to", 0.6,
            "we appreciate your interest", 0.4,
            "application has been sent", 0.9,
            "thanks again for applying", 0.6,
            "track the status of your application", 0.6,
            "keep you updated about next steps", 0.5,
            "get started on carefully reviewing", 0.5);

    public static final List<Phrase> UPDATE = list(
            "application status", 0.7,
            "status of your application", 0.8,
            "update on your application", 0.8,
            "update regarding your application", 0.8,
            "application update", 0.8,
            "an update on your", 0.6,
            "regarding your application", 0.6,
            "your application is under review", 1.0,
            "application is being reviewed", 1.0,
            "is currently under review", 0.8,
            "under review", 0.5,
            "being reviewed", 0.5,
            "currently reviewing", 0.6,
            "reviewing your application", 0.7,
            "your application has been viewed", 0.8,
            "application was viewed", 0.8,
            "moved to the next stage", 0.9,
            "moved forward to the next", 0.9,
            "move forward with your application", 0.8,
            "progressed to the next", 0.9,
            "next stage of the process", 0.6,
            "shortlisted", 0.7,
            "your profile has been shortlisted", 1.0,
            "application is being considered", 0.8,
            "hiring manager is reviewing", 0.9,
            "still under consideration", 0.8);

    /** Subset of update phrases indicating review/progress (maps to UNDER_REVIEW). */
    public static final List<Phrase> PROGRESS = list(
            "under review", 1, "being reviewed", 1, "currently reviewing", 1, "reviewing your application", 1,
            "moved to the next stage", 1, "moved forward to the next", 1, "move forward with your application", 1,
            "progressed to the next", 1, "shortlisted", 1, "being considered", 1, "hiring manager is reviewing", 1,
            "has been viewed", 1, "was viewed", 1, "under consideration", 1, "next stage", 1);

    public static final List<Phrase> REJECTION = list(
            "unfortunately", 0.35,
            "after careful consideration", 0.7,
            "after careful review", 0.6,
            "decided not to proceed", 1.0,
            "not to proceed with your application", 1.0,
            "decided to not move forward", 1.0,
            "decided not to move forward", 1.0,
            "not to move forward", 0.9,
            "not moving forward", 0.9,
            "will not be moving forward", 1.0,
            "won't be moving forward", 1.0,
            "we will not be proceeding", 1.0,
            "not be progressing", 1.0,
            "will not be progressing", 1.0,
            "move forward with other candidates", 1.0,
            "moving forward with other candidates", 1.0,
            "proceed with other candidates", 1.0,
            "pursue other candidates", 1.0,
            "decided to pursue other", 1.0,
            "other candidates whose", 0.9,
            "candidates whose experience more closely", 1.0,
            "more closely matches", 0.7,
            "more closely aligns", 0.7,
            "more closely align", 0.7,
            "position has been filled", 1.0,
            "role has been filled", 1.0,
            "filled that role", 1.0,
            "filled the role", 1.0,
            "filled the position", 1.0,
            "filled this position", 1.0,
            "have now filled", 1.0,
            "not moving forward with your application", 1.0,
            "no longer considering", 0.9,
            "no longer under consideration", 1.0,
            "have not been selected", 1.0,
            "you were not selected", 1.0,
            "not been selected", 0.9,
            "not selected for", 0.9,
            "regret to inform", 1.0,
            "we regret", 0.6,
            "not a match at this time", 0.9,
            "not the right fit", 0.7,
            "unable to offer you", 1.0,
            "decided to go with another", 1.0,
            "decided to move forward with another", 1.0,
            "keep your resume on file", 0.5,
            "keep your details on file", 0.5,
            "keep your profile on file", 0.5,
            "wish you the best in your job search", 0.6,
            "best of luck in your search", 0.6,
            "best of luck with your job search", 0.6,
            "success in your job search", 0.5,
            "wish you every success", 0.4,
            "your application was unsuccessful", 1.0,
            "application has been unsuccessful", 1.0,
            "unsuccessful on this occasion", 1.0,
            "will not be taking your application further", 1.0);

    public static final List<Phrase> INTERVIEW_INVITE = list(
            "invite you to interview", 1.0,
            "invite you for an interview", 1.0,
            "invite you to an interview", 1.0,
            "invitation to interview", 1.0,
            "interview invitation", 1.0,
            "invite you for a", 0.5,
            "like to invite you", 0.6,
            "would like to schedule", 0.8,
            "schedule an interview", 1.0,
            "schedule a call", 0.6,
            "schedule a time", 0.6,
            "set up a call", 0.6,
            "set up an interview", 1.0,
            "phone screen", 0.9,
            "phone interview", 1.0,
            "video interview", 1.0,
            "technical interview", 1.0,
            "onsite interview", 1.0,
            "on-site interview", 1.0,
            "virtual onsite", 1.0,
            "hiring manager interview", 1.0,
            "panel interview", 1.0,
            "interview loop", 1.0,
            "first round", 0.6,
            "second round", 0.6,
            "final round", 0.7,
            "next round", 0.6,
            "interview with", 0.6,
            "your availability", 0.6,
            "share your availability", 0.8,
            "let us know your availability", 0.8,
            "availability for", 0.5,
            "calendly.com", 0.7,
            "book a time", 0.6,
            "pick a time", 0.6,
            "select a time slot", 0.7,
            "goodtime.io", 0.8,
            "meet the team", 0.4,
            "interview", 0.3);

    public static final List<Phrase> INTERVIEW_UPDATE = list(
            "interview has been rescheduled", 1.0,
            "interview rescheduled", 1.0,
            "reschedule your interview", 0.9,
            "interview reminder", 1.0,
            "reminder: interview", 1.0,
            "interview confirmation", 1.0,
            "interview is confirmed", 1.0,
            "interview has been confirmed", 1.0,
            "interview has been scheduled", 1.0,
            "interview scheduled", 1.0,
            "interview confirmed", 1.0,
            "has been scheduled", 0.6,
            "has been rescheduled", 0.9,
            "interview details", 0.8,
            "updated interview", 0.9,
            "interview has been moved", 1.0,
            "interview has been cancelled", 1.0,
            "interview cancelled", 1.0);

    /** Words that mention interviews but do NOT indicate a new interview (e.g. inside rejections). */
    public static final List<Phrase> POST_INTERVIEW = list(
            "thank you for interviewing", 1,
            "thanks for interviewing", 1,
            "thank you for taking the time to interview", 1,
            "taking the time to interview", 1,
            "after interviewing", 1,
            "following your interview", 1,
            "following your recent interview", 1,
            "time you spent interviewing", 1,
            "for your time interviewing", 1);

    public static final List<Phrase> ASSESSMENT = list(
            "online assessment", 1.0,
            "coding assessment", 1.0,
            "technical assessment", 1.0,
            "assessment link", 1.0,
            "assessment invitation", 1.0,
            "invitation to complete", 0.7,
            "complete the assessment", 1.0,
            "complete your assessment", 1.0,
            "complete the following assessment", 1.0,
            "take-home", 0.9,
            "take home assignment", 1.0,
            "take-home assignment", 1.0,
            "take home test", 1.0,
            "coding challenge", 1.0,
            "coding test", 1.0,
            "online test", 0.9,
            "technical test", 0.9,
            "programming test", 0.9,
            "aptitude test", 0.9,
            "psychometric", 0.8,
            "skills assessment", 0.9,
            "case study assignment", 0.9,
            "hackerrank", 0.6,
            "codility", 0.6,
            "codesignal", 0.6,
            "hirevue", 0.6,
            "testgorilla", 0.6,
            "mettl", 0.6,
            "hackerearth", 0.6,
            "assessment", 0.4,
            "complete within", 0.4,
            "test link", 0.6,
            "test invitation", 0.8);

    public static final List<Phrase> OFFER = list(
            "offer letter", 1.0,
            "pleased to offer you", 1.0,
            "happy to offer you", 1.0,
            "delighted to offer you", 1.0,
            "excited to offer you", 1.0,
            "thrilled to offer you", 1.0,
            "pleased to extend", 1.0,
            "extend an offer", 1.0,
            "extend you an offer", 1.0,
            "extending an offer", 1.0,
            "offer of employment", 1.0,
            "employment offer", 1.0,
            "job offer", 0.9,
            "verbal offer", 1.0,
            "formal offer", 0.9,
            "written offer", 0.9,
            "offer details", 0.7,
            "accept the offer", 0.7,
            "accept this offer", 0.7,
            "compensation package", 0.6,
            "your offer", 0.5,
            "we are delighted", 0.3,
            "welcome to the team", 0.4,
            "joining bonus", 0.6,
            "signing bonus", 0.6,
            "sign-on bonus", 0.6,
            "start date", 0.3);

    public static final List<Phrase> RECRUITER = list(
            "came across your profile", 1.0,
            "came across your resume", 1.0,
            "came across your linkedin", 1.0,
            "found your profile", 0.8,
            "reached out about a role", 1.0,
            "reaching out about", 0.6,
            "reaching out regarding", 0.6,
            "reaching out to you", 0.5,
            "i'm reaching out", 0.5,
            "i am reaching out", 0.5,
            "i am a recruiter", 1.0,
            "i'm a recruiter", 1.0,
            "technical recruiter", 0.6,
            "talent partner", 0.5,
            "opportunity at", 0.6,
            "exciting opportunity", 0.6,
            "a role that", 0.4,
            "would you be interested", 0.7,
            "would you be open to", 0.7,
            "are you open to", 0.6,
            "open to new opportunities", 0.8,
            "interested in exploring", 0.6,
            "your background", 0.5,
            "your experience with", 0.4,
            "quick chat", 0.6,
            "quick call", 0.6,
            "position that might interest", 0.8,
            "role that might interest", 0.8,
            "we are hiring", 0.5,
            "hiring for", 0.4,
            "your profile caught", 0.8,
            "impressed by your", 0.6,
            "a good fit for", 0.5,
            "great fit for", 0.5,
            "connect with you", 0.4);

    public static final List<Phrase> WITHDRAWAL = list(
            "withdrawn your application", 1.0,
            "application has been withdrawn", 1.0,
            "application was withdrawn", 1.0,
            "you have withdrawn", 1.0,
            "you withdrew", 1.0,
            "withdrawal of your application", 1.0,
            "withdrawal confirmation", 1.0,
            "confirm your withdrawal", 0.9);

    public static final List<Phrase> FOLLOW_UP = list(
            "following up", 0.6,
            "follow up on", 0.5,
            "follow-up on", 0.5,
            "just checking in", 0.6,
            "checking in on", 0.5,
            "circling back", 0.6,
            "wanted to follow up", 0.8);

    /** Generic job vocabulary; weak but establishes "job context". */
    public static final List<Phrase> JOB_CONTEXT = list(
            "application", 0.12, "applied", 0.12, "applying", 0.12, "candidate", 0.12, "candidacy", 0.15,
            "position", 0.1, "role", 0.08, "recruiter", 0.12, "recruiting", 0.12, "hiring", 0.1, "resume", 0.12,
            "cv", 0.1, "job", 0.08, "career", 0.08, "careers", 0.08, "interview", 0.12, "talent acquisition", 0.15,
            "requisition", 0.15, "opening", 0.05, "hiring manager", 0.15, "hiring team", 0.15,
            "recruitment", 0.12, "job id", 0.15);

    /** Conditional language in confirmations ("if selected, we will invite you to interview"). */
    public static final List<Phrase> CONDITIONAL = list(
            "if selected", 1, "if you are selected", 1, "should you be selected", 1, "if shortlisted", 1,
            "if your profile", 1, "if your skills", 1, "if your qualifications", 1, "if your experience", 1,
            "if there is a match", 1, "may include", 1, "next steps may", 1, "may be asked", 1, "may invite", 1,
            "if we decide to move forward", 1, "if we would like to", 1, "will be in touch", 1);

    // ---------------------------------------------------------------- negatives

    public static final List<Phrase> JOB_ALERT = list(
            "jobs you may be interested in", 1.2,
            "jobs you might be interested in", 1.2,
            "jobs you might like", 1.2,
            "new jobs for you", 1.2,
            "new jobs matching", 1.2,
            "jobs matching your", 1.2,
            "job alert", 1.0,
            "job alerts", 1.0,
            "jobs alert", 1.0,
            "recommended jobs", 1.0,
            "jobs recommended for you", 1.2,
            "recommended for you", 0.6,
            "jobs based on your", 1.0,
            "top job picks", 1.2,
            "job recommendations", 1.0,
            "jobs similar to", 0.8,
            "similar jobs", 0.5,
            "new opportunities matching", 1.0,
            "is hiring", 0.4,
            "are hiring", 0.3,
            "apply now", 0.3,
            "easy apply", 0.4,
            "jobs in your area", 1.0,
            "new job postings", 1.0,
            "job matches", 0.9,
            "matching jobs", 0.9,
            "daily digest", 0.8,
            "weekly digest", 0.8,
            "new jobs posted", 1.2,
            "this job is a match", 1.0,
            "talent community", 0.8,
            "talent network", 0.8,
            "stay in touch", 0.5,
            "stay connected to", 0.5,
            "contact you about opportunities", 0.6,
            "how you'd like", 0.4,
            "explore your opportunities", 0.8,
            "prior applicants", 0.6);

    /** Non-employment "applications" (credit cards, loans, accounts) that reuse rejection/confirmation language. */
    public static final List<Phrase> FINANCIAL_APPLICATION = list(
            "credit card application", 1.2, "card application", 0.9, "loan application", 1.2,
            "account opening", 1.0, "account application", 0.9, "dear customer", 0.6, "kyc", 0.6,
            "credit limit", 0.8, "customer care", 0.4, "customercare", 0.4, "insurance application", 1.0,
            "policy application", 0.9, "mortgage", 0.9, "visa application", 1.0, "passport application", 1.0,
            "admission application", 0.9, "rental application", 1.0);

    /** Words that only occur when an email is really about employment. */
    public static final List<Phrase> EMPLOYMENT_WORDS = list(
            "position", 1, "role", 1, "job", 1, "candidate", 1, "recruiter", 1, "recruiting", 1, "recruitment", 1,
            "interview", 1, "hiring", 1, "resume", 1, "career", 1, "careers", 1, "employment", 1,
            "talent acquisition", 1, "requisition", 1, "internship", 1);

    /** Legal/anti-fraud/confidentiality boilerplate whose wording ("job offer", "equal opportunity") is noise. */
    public static final java.util.regex.Pattern BOILERPLATE_SENTENCE = java.util.regex.Pattern.compile(
            "(?i)fraud|scam|fake|beware|misrepresent|in exchange for (?:a |any )?(?:money|fee|payment)|demand money|"
                    + "never (?:ask|charge|request)|(?:does|do|will) not (?:charge|ask|request)|not authori[sz]ed|"
                    + "equal opportunity|equal employment|confidential information|intended recipient|"
                    + "privacy notice|privacy policy|business ethics");

    public static final List<Phrase> SHOPPING = list(
            "your order", 0.8, "order confirmation", 1.0, "order #", 0.9, "order number", 0.8, "has shipped", 1.0,
            "have shipped", 1.0, "shipped", 0.5, "out for delivery", 1.0, "delivered", 0.4, "tracking number", 1.0,
            "track your package", 1.0, "track your order", 1.0, "shipment", 0.7, "your package", 0.8,
            "refund", 0.6, "your cart", 0.9, "items in your cart", 1.0, "purchase", 0.4, "receipt", 0.5,
            "invoice", 0.5, "arriving", 0.4, "return request", 0.8, "add to cart", 0.9, "wishlist", 0.6);

    public static final List<Phrase> BANKING = list(
            "otp", 1.0, "one-time password", 1.0, "one time password", 1.0, "verification code", 0.9,
            "account statement", 1.0, "statement is ready", 1.0, "e-statement", 1.0, "transaction", 0.6,
            "debited", 1.0, "credited", 0.8, "credit card", 0.8, "debit card", 0.8, "a/c", 0.7,
            "available balance", 1.0, "upi", 0.8, "payment received", 0.7, "payment due", 0.9, "emi", 0.6,
            "minimum amount due", 1.0, "loan", 0.4, "kyc", 0.8, "net banking", 1.0, "netbanking", 1.0,
            "mutual fund", 0.8, "portfolio", 0.3, "insurance premium", 1.0, "policy renewal", 0.9);

    public static final List<Phrase> SOCIAL = list(
            "liked your", 0.9, "commented on your", 0.9, "mentioned you", 0.9, "new follower", 1.0,
            "started following you", 1.0, "friend request", 1.0, "tagged you", 1.0, "connection request", 0.9,
            "invitation to connect", 0.9, "wants to connect", 0.9, "accepted your invitation", 0.8,
            "endorsed you", 1.0, "viewed your profile", 0.9, "who viewed your profile", 1.0,
            "people you may know", 1.0, "appeared in", 0.5, "search appearances", 1.0, "reacted to", 0.8,
            "your post", 0.6, "new message from", 0.3, "birthday", 0.5, "work anniversary", 1.0,
            "congratulate", 0.4, "trending", 0.5);

    public static final List<Phrase> PROMOTION = list(
            "% off", 1.0, "sale", 0.4, "flash sale", 1.0, "discount", 0.7, "coupon", 1.0, "promo code", 1.0,
            "limited time", 0.8, "limited-time", 0.8, "special offer", 1.0, "exclusive offer", 1.0,
            "limited time offer", 1.0, "offer ends", 1.0, "offer expires", 0.8, "deal of the day", 1.0, "deals", 0.6,
            "free shipping", 1.0, "buy now", 1.0, "shop now", 1.0, "black friday", 1.0, "cyber monday", 1.0,
            "cashback", 1.0, "save up to", 1.0, "upgrade now", 0.8, "free trial", 0.8, "subscribe now", 0.7,
            "early bird", 0.6, "register now", 0.5, "webinar", 0.7, "don't miss", 0.5, "best price", 0.9,
            "price drop", 1.0, "new arrivals", 1.0, "gift card", 0.7, "reward points", 0.9, "earn rewards", 0.9);

    public static final List<Phrase> TECH_NOTIFICATIONS = list(
            "billing", 0.6, "your bill", 0.9, "billing statement", 1.0, "usage alert", 1.0, "budget alert", 1.0,
            "amazon web services", 0.8, "aws account", 1.0, "aws free tier", 1.0, "google cloud", 0.6,
            "gcp", 0.5, "azure subscription", 1.0, "pull request", 1.0, "merged", 0.5, "commit", 0.4,
            "build failed", 1.0, "build succeeded", 1.0, "workflow run", 1.0, "pipeline failed", 1.0,
            "pipeline", 0.2, "deployment", 0.4, "repository", 0.6, "issue #", 0.8, "opened an issue", 1.0,
            "assigned to you", 0.6, "jira", 0.6, "sprint", 0.4, "ticket", 0.3, "dependabot", 1.0,
            "security vulnerability", 0.8, "api key", 0.6, "your subscription", 0.6, "renewal", 0.4);

    public static final List<Phrase> SECURITY = list(
            "security alert", 1.0, "new sign-in", 1.0, "new login", 0.9, "sign-in attempt", 1.0,
            "login attempt", 1.0, "password reset", 1.0, "reset your password", 1.0, "verify your email", 0.9,
            "confirm your email", 0.7, "two-factor", 0.7, "2-step verification", 0.7, "suspicious activity", 1.0,
            "unusual activity", 1.0, "recovery email", 0.9, "app password", 0.5);

    public static final List<Phrase> NEWSLETTER = list(
            "newsletter", 0.8, "view in browser", 0.4, "view this email in your browser", 0.5,
            "this week in", 0.6, "weekly roundup", 0.8, "monthly roundup", 0.8, "read more", 0.2,
            "unsubscribe", 0.15, "manage your preferences", 0.2, "email preferences", 0.2, "podcast", 0.5,
            "episode", 0.4, "top stories", 0.8, "editor's pick", 0.8);

    public static final List<Phrase> CALENDAR_SPAM = list(
            "invitation from google calendar", 0.6, "accept invitation", 0.3, "you have been invited to the following event",
            0.4);
}
