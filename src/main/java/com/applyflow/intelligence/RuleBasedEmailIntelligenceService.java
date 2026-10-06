package com.applyflow.intelligence;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.mail.classifier.CompanyExtractor;
import com.applyflow.mail.classifier.DateExtractor;
import com.applyflow.mail.classifier.EmailClassifier;
import com.applyflow.mail.classifier.FieldExtractor;
import com.applyflow.mail.classifier.SenderAnalyzer;
import com.applyflow.mail.classifier.SenderProfile;
import com.applyflow.mail.classifier.TitleExtractor;
import com.applyflow.mail.matcher.ApplicationMatcher;
import com.applyflow.mail.matcher.MatchResult;
import com.applyflow.mail.parser.EmailHeaders;
import com.applyflow.mail.parser.ParsedEmail;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Optional;

/** Deterministic, privacy-preserving implementation of {@link EmailIntelligenceService}. */
@Service
@Primary
public class RuleBasedEmailIntelligenceService implements EmailIntelligenceService {

    private final ApplicationMatcher matcher;
    private final ZoneId zone;

    public RuleBasedEmailIntelligenceService(ApplicationMatcher matcher, ZoneId appZone) {
        this.matcher = matcher;
        this.zone = appZone;
    }

    @Override
    public PrefilterDecision prefilter(EmailHeaders headers) {
        return EmailClassifier.prefilter(headers);
    }

    @Override
    public ClassificationResult classifyEmail(ParsedEmail email) {
        return EmailClassifier.classify(email);
    }

    @Override
    public ExtractedApplication extractApplication(ParsedEmail email) {
        SenderProfile sender = SenderAnalyzer.analyze(email.senderEmail(), email.senderName(), email.subject());
        CompanyExtractor.CompanyGuess guess = CompanyExtractor.extract(email, sender);
        String title = TitleExtractor.extract(email.subject(), email.bodyText(), guess.name());
        return new ExtractedApplication(guess.name(), guess.domain(), title, sourceFor(sender));
    }

    @Override
    public ExtractedJobDetails extractJobDetails(ParsedEmail email) {
        SenderProfile sender = SenderAnalyzer.analyze(email.senderEmail(), email.senderName(), email.subject());
        CompanyExtractor.CompanyGuess guess = CompanyExtractor.extract(email, sender);
        String body = email.bodyText();
        String title = TitleExtractor.extract(email.subject(), body, guess.name());
        FieldExtractor.Salary salary = FieldExtractor.salary(body);
        DateExtractor.ScheduledDate date = DateExtractor.extract(email.subject(), body, email.receivedAt(), zone);

        String recruiterName = null;
        String recruiterEmail = null;
        if ((sender.kind() == SenderProfile.Kind.COMPANY || sender.kind() == SenderProfile.Kind.PERSONAL_PROVIDER)
                && !SenderAnalyzer.isNoReply(email.senderLocalPart()) && looksLikePerson(email.senderName())) {
            recruiterName = email.senderName().trim();
            recruiterEmail = email.senderEmail();
        }
        return new ExtractedJobDetails(
                guess.name(),
                guess.domain(),
                title,
                sourceFor(sender),
                FieldExtractor.location(email.subject(), body),
                FieldExtractor.jobUrl(body, email.bodyHtml()),
                FieldExtractor.applicationRef(email.subject(), body),
                FieldExtractor.employmentType(email.subject(), body),
                salary == null ? null : salary.min(),
                salary == null ? null : salary.max(),
                salary == null ? null : salary.currency(),
                recruiterName,
                recruiterEmail,
                date == null ? null : date.at(),
                date != null && date.deadline());
    }

    @Override
    public EmailSummary summarizeEmail(ParsedEmail email, ClassificationResult classification,
                                       ExtractedJobDetails details) {
        return SummaryGenerator.summarize(classification, details, email.senderName(), zone);
    }

    @Override
    public Optional<ApplicationStatus> detectStatus(ClassificationResult c) {
        if (c == null || c.classification() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (c.classification()) {
            case APPLICATION_CONFIRMATION -> ApplicationStatus.APPLIED;
            case APPLICATION_UPDATE -> c.progressIndicated() ? ApplicationStatus.UNDER_REVIEW : null;
            case ASSESSMENT -> ApplicationStatus.ASSESSMENT;
            case RECRUITER_CONTACT -> ApplicationStatus.RECRUITER_CONTACT;
            case INTERVIEW_INVITATION, INTERVIEW_UPDATE -> ApplicationStatus.INTERVIEW;
            case OFFER -> ApplicationStatus.OFFER;
            case REJECTION -> ApplicationStatus.REJECTED;
            case WITHDRAWAL -> ApplicationStatus.WITHDRAWN;
            case FOLLOW_UP, OTHER_JOB_RELATED, NOT_JOB_RELATED -> null;
        });
    }

    @Override
    public MatchResult matchApplication(ParsedEmail email, ExtractedJobDetails details) {
        if (matcher == null) {
            return MatchResult.noMatch();
        }
        return matcher.match(email, details);
    }

    private static String sourceFor(SenderProfile sender) {
        return switch (sender.kind()) {
            case ATS, JOB_BOARD_APPLICATION, JOB_BOARD_OTHER, JOB_BOARD_ALERT -> sender.source();
            case PERSONAL_PROVIDER -> "Recruiter";
            case ASSESSMENT_PLATFORM, NON_JOB_SERVICE -> null;
            case RECRUITING_MAILBOX, COMPANY -> "Company site";
        };
    }

    static boolean looksLikePerson(String name) {
        if (name == null) {
            return false;
        }
        String n = name.trim();
        if (n.isEmpty() || n.length() > 60) {
            return false;
        }
        String[] parts = n.split("\\s+");
        if (parts.length < 1 || parts.length > 4) {
            return false;
        }
        for (String p : parts) {
            if (!p.matches("[\\p{L}][\\p{L}.'\\-]*")) {
                return false;
            }
        }
        return !n.matches("(?i).*\\b(team|recruiting|careers|jobs|talent|hr|notifications?|support|no.?reply|"
                + "hiring|info|admin|service)\\b.*");
    }
}
