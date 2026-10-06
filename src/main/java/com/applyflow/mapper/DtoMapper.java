package com.applyflow.mapper;

import com.applyflow.dto.AccountDtos.EmailAccountDto;
import com.applyflow.dto.AccountDtos.SyncJobDto;
import com.applyflow.dto.ApplicationDtos.ApplicationDetail;
import com.applyflow.dto.ApplicationDtos.ApplicationSummary;
import com.applyflow.dto.ApplicationDtos.NoteDto;
import com.applyflow.dto.ApplicationDtos.StatusHistoryEntry;
import com.applyflow.dto.ApplicationDtos.TimelineEvent;
import com.applyflow.dto.DashboardDtos.ActivityItem;
import com.applyflow.dto.InboxDtos.EmailDetail;
import com.applyflow.dto.InboxDtos.InboxItem;
import com.applyflow.dto.InboxDtos.MatchSuggestion;
import com.applyflow.dto.InboxDtos.StatusImpact;
import com.applyflow.dto.MiscDtos.NotificationDto;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.Company;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.Notification;
import com.applyflow.entity.StatusHistory;
import com.applyflow.entity.SyncJob;
import org.springframework.stereotype.Component;

import java.util.List;

/** Document → DTO mapping. References not batch-loaded by the caller are loaded on first access. */
@Component
public class DtoMapper {

    public ApplicationSummary toSummary(JobApplication a, long emailCount) {
        Company c = a.getCompany();
        EmailAccount acc = a.getEmailAccount();
        return new ApplicationSummary(
                a.getId(), a.displayId(), c.getId(), c.getName(), c.getDomain(), a.getJobTitle(), a.getLocation(),
                a.getSource(), a.getStatus(), a.getAppliedAt(), a.getLastActivityAt(), emailCount,
                acc == null ? null : acc.getId(), acc == null ? null : acc.getEmail(),
                acc == null ? null : acc.getProvider(), a.isNeedsReview(), a.isArchived());
    }

    public ApplicationDetail toDetail(JobApplication a, long emailCount, List<Note> notes,
                                      List<StatusHistory> history) {
        Company c = a.getCompany();
        EmailAccount acc = a.getEmailAccount();
        return new ApplicationDetail(
                a.getId(), a.displayId(), c.getId(), c.getName(), c.getDomain(), a.getJobTitle(), a.getLocation(),
                a.getSource(), a.getStatus(), a.getAppliedAt(), a.getLastActivityAt(), emailCount,
                acc == null ? null : acc.getId(), acc == null ? null : acc.getEmail(),
                acc == null ? null : acc.getProvider(), a.isNeedsReview(), a.isArchived(),
                a.getJobUrl(), a.getEmploymentType(), a.getSalaryMin(), a.getSalaryMax(), a.getSalaryCurrency(),
                a.getRecruiterName(), a.getRecruiterEmail(), a.getApplicationRef(), a.getCurrentStage(),
                a.getConfidence(), a.getCreatedAt(), a.getUpdatedAt(),
                notes.stream().map(this::toNote).toList(),
                history.stream().map(this::toHistory).toList());
    }

    public NoteDto toNote(Note n) {
        return new NoteDto(n.getId(), n.getApplicationId(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt());
    }

    public StatusHistoryEntry toHistory(StatusHistory h) {
        return new StatusHistoryEntry(h.getId(), h.getFromStatus(), h.getToStatus(), h.getActor(), h.getReason(),
                h.getEmailId(), h.getConfidence(), h.getChangedAt());
    }

    /**
     * @param emailExists  whether the linked email (if any) still exists
     * @param emailSubject subject of the linked email (batch-loaded by the caller)
     */
    public TimelineEvent toTimeline(ApplicationEventEntity e, boolean emailExists, String emailSubject) {
        Long emailId = emailExists ? e.getEmailId() : null;
        return new TimelineEvent(e.getId(), e.getApplicationId(), e.getEventType(), e.getTitle(),
                e.getDescription(), e.getEventDate(), e.getPreviousStatus(), e.getNewStatus(), e.getConfidence(),
                e.getActor(), emailId, emailId == null ? null : emailSubject, e.getScheduledAt());
    }

    public ActivityItem toActivity(ApplicationEventEntity e) {
        JobApplication a = e.getApplication();
        return new ActivityItem(e.getId(), a.getId(), a.getCompany().getName(), a.getJobTitle(), e.getEventType(),
                e.getTitle(), e.getDescription(), e.getPreviousStatus(), e.getNewStatus(), e.getActor(),
                e.getEventDate());
    }

    public InboxItem toInboxItem(EmailMessage e) {
        JobApplication app = e.getApplication();
        EmailAccount acc = e.getEmailAccount();
        String company = app != null ? app.getCompany().getName() : e.getDetectedCompany();
        return new InboxItem(e.getId(), acc == null ? null : acc.getId(), acc == null ? null : acc.getEmail(),
                company, e.getSenderName(), e.getSenderEmail(), e.getSubject(), e.getSnippet(), e.getSummary(),
                app == null ? null : app.getId(), app == null ? null : app.getJobTitle(), e.getClassification(),
                e.getDetectedStatus(), e.getClassificationConfidence() == null ? 0 : e.getClassificationConfidence(),
                e.isActionRequired(), e.getActionText(), e.isNeedsReview(), e.isRead(), e.getReceivedAt());
    }

    public EmailDetail toEmailDetail(EmailMessage e, List<EmailMatchSuggestion> suggestions) {
        InboxItem i = toInboxItem(e);
        StatusImpact impact = e.getDetectedStatus() == null ? null
                : new StatusImpact(e.getPreviousStatus(), e.getDetectedStatus(), e.isStatusApplied());
        List<MatchSuggestion> matches = e.isNeedsReview()
                ? suggestions.stream().filter(s -> s.getApplication() != null)
                .map(s -> new MatchSuggestion(s.getApplication().getId(),
                s.getApplication().getCompany().getName(), s.getApplication().getJobTitle(), s.getScore(),
                s.getReason())).toList()
                : List.of();
        return new EmailDetail(i.id(), i.emailAccountId(), i.emailAccountEmail(), i.companyName(), i.senderName(),
                i.senderEmail(), i.subject(), i.snippet(), i.summary(), i.applicationId(), i.applicationJobTitle(),
                i.classification(), i.detectedStatus(), i.confidence(), i.actionRequired(), i.actionText(),
                i.needsReview(), i.isRead(), i.receivedAt(), e.getRecipient(), e.getThreadId(), e.getBodyText(),
                e.getBodyHtml(), e.getClassificationReason(), impact, matches);
    }

    public EmailAccountDto toAccount(EmailAccount a) {
        return new EmailAccountDto(a.getId(), a.getEmail(), a.getProvider(), a.getHost(), a.getPort(), a.isSsl(),
                a.getUsername(), a.getFolder(), a.getSyncStatus(), a.isEnabled(), a.getLastSyncAt(), a.getLastError(),
                a.getEmailsProcessed(), a.getJobEmails(), a.getInitialSyncDays(),
                a.getEncryptedPassword() != null && !a.getEncryptedPassword().isBlank(), a.getCreatedAt());
    }

    public SyncJobDto toSyncJob(SyncJob j) {
        EmailAccount acc = j.getEmailAccount();
        return new SyncJobDto(j.getId(), acc.getId(), acc.getEmail(), j.getStatus(), j.getStartedAt(),
                j.getFinishedAt(), j.getMessagesFetched(), j.getMessagesProcessed(), j.getJobEmailsFound(),
                j.getApplicationsCreated(), j.getApplicationsUpdated(), j.getError());
    }

    public NotificationDto toNotification(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getApplicationId(),
                n.getEmailId(), n.isRead(), n.getCreatedAt());
    }
}
