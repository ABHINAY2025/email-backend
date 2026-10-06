package com.applyflow.intelligence;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.mail.matcher.MatchResult;
import com.applyflow.mail.parser.EmailHeaders;
import com.applyflow.mail.parser.ParsedEmail;

import java.util.Optional;

/**
 * Email understanding API. The rule-based implementation is the default; an AI-backed implementation may be added
 * behind {@code app.intelligence.provider}. Privacy contract: any non-rule implementation must only ever receive
 * emails that already passed the rule-based job-related pre-filter/classification.
 */
public interface EmailIntelligenceService {

    /** Cheap header-only pre-filter used before downloading message bodies. */
    PrefilterDecision prefilter(EmailHeaders headers);

    ClassificationResult classifyEmail(ParsedEmail email);

    ExtractedApplication extractApplication(ParsedEmail email);

    ExtractedJobDetails extractJobDetails(ParsedEmail email);

    EmailSummary summarizeEmail(ParsedEmail email, ClassificationResult classification, ExtractedJobDetails details);

    default EmailSummary summarizeEmail(ParsedEmail email, ClassificationResult classification) {
        return summarizeEmail(email, classification, extractJobDetails(email));
    }

    Optional<ApplicationStatus> detectStatus(ClassificationResult classification);

    MatchResult matchApplication(ParsedEmail email, ExtractedJobDetails details);
}
