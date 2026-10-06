package com.applyflow.analytics;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.StatusHistory;
import com.applyflow.repository.EmailMessageRepositoryCustom.LinkedEmailRow;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.repository.StatusHistoryRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads per-application derived facts (responses, interviews, offers) used by analytics and dashboards. */
@Component
public class ApplicationFacts {

    private static final Set<ApplicationStatus> NON_RESPONSE = EnumSet.of(ApplicationStatus.APPLIED,
            ApplicationStatus.WITHDRAWN, ApplicationStatus.CLOSED);

    private final JobApplicationRepository applicationRepository;
    private final StatusHistoryRepository historyRepository;
    private final EmailMessageRepository emailRepository;

    public ApplicationFacts(JobApplicationRepository applicationRepository, StatusHistoryRepository historyRepository,
                            EmailMessageRepository emailRepository) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.emailRepository = emailRepository;
    }

    public record Fact(JobApplication app, boolean responded, Instant firstResponseAt, boolean reachedInterview,
                       Instant firstInterviewAt, boolean reachedOffer, Instant rejectedAt) {

        public ApplicationStatus status() {
            return app.getStatus();
        }

        public Instant appliedAt() {
            return app.getAppliedAt();
        }
    }

    /** Facts for all of the current user's non-archived applications. Must be called inside a transaction. */
    public List<Fact> load() {
        return compute(applicationRepository.findAllActiveWithCompany(CurrentUser.id()));
    }

    /** Facts for the given applications of the current user. */
    public List<Fact> compute(List<JobApplication> apps) {
        Long userId = CurrentUser.id();
        Map<Long, List<StatusHistory>> history = new HashMap<>();
        for (StatusHistory row : historyRepository.findAllRows(userId)) {
            history.computeIfAbsent(row.getApplicationId(), k -> new ArrayList<>()).add(row);
        }
        Map<Long, List<LinkedEmailRow>> emails = new HashMap<>();
        for (LinkedEmailRow row : emailRepository.findLinkedClassificationRows(userId)) {
            emails.computeIfAbsent(row.applicationId(), k -> new ArrayList<>()).add(row);
        }
        List<Fact> facts = new ArrayList<>(apps.size());
        for (JobApplication a : apps) {
            Instant firstResponse = null;
            Instant firstInterview = null;
            Instant rejected = null;
            boolean interview = a.getStatus() == ApplicationStatus.INTERVIEW || a.getStatus() == ApplicationStatus.OFFER;
            boolean offer = a.getStatus() == ApplicationStatus.OFFER;
            boolean responded = !NON_RESPONSE.contains(a.getStatus());
            for (StatusHistory h : history.getOrDefault(a.getId(), List.of())) {
                ApplicationStatus to = h.getToStatus();
                Instant at = h.getChangedAt();
                if (!NON_RESPONSE.contains(to)) {
                    responded = true;
                    firstResponse = min(firstResponse, at);
                }
                if (to == ApplicationStatus.INTERVIEW || to == ApplicationStatus.OFFER) {
                    interview = true;
                    firstInterview = min(firstInterview, at);
                }
                if (to == ApplicationStatus.OFFER) {
                    offer = true;
                }
                if (to == ApplicationStatus.REJECTED) {
                    rejected = min(rejected, at);
                }
            }
            for (LinkedEmailRow e : emails.getOrDefault(a.getId(), List.of())) {
                EmailClassification c = e.classification();
                if (c != null && c.isEmployerResponse()) {
                    responded = true;
                    firstResponse = min(firstResponse, e.receivedAt());
                }
            }
            facts.add(new Fact(a, responded, firstResponse, interview, firstInterview, offer, rejected));
        }
        return facts;
    }

    private static Instant min(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }
}
