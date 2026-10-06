package com.applyflow.mail.matcher;

import com.applyflow.entity.Company;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.intelligence.ExtractedJobDetails;
import com.applyflow.mail.classifier.CompanyNames;
import com.applyflow.mail.classifier.TitleExtractor;
import com.applyflow.mail.parser.ParsedEmail;
import com.applyflow.repository.CompanyRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the existing application an email belongs to (thread → reference → URL → company/title scoring). Only the
 * current owner's data (see {@link CurrentUser}) is ever considered.
 */
@Component
public class ApplicationMatcher {

    /** Placeholder that never equals a real Message-ID (avoids empty IN lists). */
    private static final String NONE = "<none@applyflow.invalid>";

    private final EmailMessageRepository emailRepository;
    private final JobApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;

    public ApplicationMatcher(EmailMessageRepository emailRepository, JobApplicationRepository applicationRepository,
                              CompanyRepository companyRepository) {
        this.emailRepository = emailRepository;
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
    }

    public MatchResult match(ParsedEmail email, ExtractedJobDetails details) {
        Long userId = CurrentUser.id();
        // 1. Same conversation → definitive.
        Set<String> ids = new LinkedHashSet<>(email.references());
        if (email.inReplyTo() != null) {
            ids.add(email.inReplyTo());
        }
        String threadId = email.threadId();
        if (!ids.isEmpty() || threadId != null) {
            List<String> idList = ids.isEmpty() ? List.of(NONE) : new ArrayList<>(ids);
            List<EmailMessage> linked = emailRepository.findLinkedInThread(userId, idList,
                    threadId == null ? NONE : threadId);
            for (EmailMessage e : linked) {
                if (e.getApplication() != null && (email.accountId() == null || e.getEmailAccount() == null
                        || Objects.equals(e.getEmailAccount().getId(), email.accountId()))) {
                    return MatchResult.link(e.getApplication().getId(), 1.0, "same email thread", true);
                }
            }
        }

        // 2-7. Score candidates.
        String normCompany = details.companyName() == null ? null : CompanyNames.normalize(details.companyName());
        Map<Long, JobApplication> candidates = new LinkedHashMap<>();
        if (details.applicationRef() != null) {
            applicationRepository.findByApplicationRef(userId, details.applicationRef())
                    .forEach(a -> candidates.put(a.getId(), a));
        }
        if (details.jobUrl() != null) {
            applicationRepository.findByJobUrl(userId, details.jobUrl()).forEach(a -> candidates.put(a.getId(), a));
        }
        List<Long> companyIds = new ArrayList<>();
        String domainLabel = CompanyNames.domainLabel(details.companyDomain());
        for (Company c : companyRepository.findByUserId(userId)) {
            boolean nameMatch = normCompany != null && !normCompany.isBlank() && normCompany.equals(c.getNormalizedName());
            boolean domainMatch = domainLabel != null && c.getDomain() != null
                    && domainLabel.equals(CompanyNames.domainLabel(c.getDomain()));
            if (nameMatch || domainMatch) {
                companyIds.add(c.getId());
            }
        }
        if (!companyIds.isEmpty()) {
            applicationRepository.findByCompanyIds(userId, companyIds).forEach(a -> candidates.put(a.getId(), a));
        }
        if (candidates.isEmpty()) {
            return MatchResult.noMatch();
        }

        MatchQuery query = new MatchQuery(normCompany, details.companyDomain(),
                details.jobTitle() == null ? null : TitleExtractor.normalize(details.jobTitle()),
                details.applicationRef(), details.jobUrl(), email.senderEmail(), email.receivedAt());
        List<MatchCandidate> list = candidates.values().stream()
                .filter(a -> !a.isArchived() || details.applicationRef() != null)
                .map(ApplicationMatcher::toCandidate)
                .toList();
        List<ScoredMatch> scored = MatchScorer.scoreAll(query, list);
        if (scored.isEmpty()) {
            return MatchResult.noMatch();
        }
        ScoredMatch top = scored.get(0);
        if (top.score() >= MatchResult.AUTO_LINK_THRESHOLD) {
            boolean ambiguous = scored.size() > 1 && scored.get(1).score() >= MatchResult.AUTO_LINK_THRESHOLD
                    && top.score() - scored.get(1).score() < 0.05;
            if (!ambiguous) {
                return MatchResult.link(top.applicationId(), top.score(), top.reason(), top.exact());
            }
        }
        List<ScoredMatch> suggestions = scored.stream()
                .filter(s -> s.score() >= MatchResult.SUGGEST_THRESHOLD - 0.1).limit(3).toList();
        if (top.score() >= MatchResult.SUGGEST_THRESHOLD && !suggestions.isEmpty()) {
            return MatchResult.review(suggestions);
        }
        return MatchResult.noMatch();
    }

    static MatchCandidate toCandidate(JobApplication a) {
        Company c = a.getCompany();
        return new MatchCandidate(a.getId(), c.getName(), c.getNormalizedName(), c.getDomain(), a.getJobTitle(),
                a.getNormalizedTitle(), a.getApplicationRef(), a.getJobUrl(), a.getRecruiterEmail(),
                a.getLastActivityAt(), a.getStatus().isActive());
    }
}
