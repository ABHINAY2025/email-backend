package com.applyflow.service;

import com.applyflow.analytics.AnalyticsService;
import com.applyflow.analytics.ApplicationFacts;
import com.applyflow.application.service.ApplicationService;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.dto.CompanyDtos.CompanyDetail;
import com.applyflow.dto.CompanyDtos.CompanySummary;
import com.applyflow.dto.CompanyDtos.ContactDto;
import com.applyflow.dto.CompanyDtos.ResponseStats;
import com.applyflow.entity.Company;
import com.applyflow.entity.JobApplication;
import com.applyflow.exception.NotFoundException;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.CompanyRepository;
import com.applyflow.repository.ContactRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class CompanyQueryService {

    private final CompanyRepository companyRepository;
    private final JobApplicationRepository applicationRepository;
    private final EmailMessageRepository emailRepository;
    private final ContactRepository contactRepository;
    private final ApplicationEventRepository eventRepository;
    private final ApplicationService applicationService;
    private final ApplicationFacts factsLoader;
    private final AnalyticsService analytics;
    private final DtoMapper mapper;

    public CompanyQueryService(CompanyRepository companyRepository, JobApplicationRepository applicationRepository,
                               EmailMessageRepository emailRepository, ContactRepository contactRepository,
                               ApplicationEventRepository eventRepository, ApplicationService applicationService,
                               ApplicationFacts factsLoader, AnalyticsService analytics, DtoMapper mapper) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.emailRepository = emailRepository;
        this.contactRepository = contactRepository;
        this.eventRepository = eventRepository;
        this.applicationService = applicationService;
        this.factsLoader = factsLoader;
        this.analytics = analytics;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CompanySummary> list(String q, int limit) {
        Long userId = CurrentUser.id();
        Map<Long, List<JobApplication>> byCompany = new HashMap<>();
        for (JobApplication a : applicationRepository.findAllWithCompany(userId)) {
            byCompany.computeIfAbsent(a.getCompany().getId(), k -> new java.util.ArrayList<>()).add(a);
        }
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return companyRepository.findByUserIdOrderByNameAsc(userId).stream()
                .filter(c -> needle == null || needle.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (c.getDomain() != null && c.getDomain().toLowerCase(Locale.ROOT).contains(needle)))
                .filter(c -> byCompany.containsKey(c.getId()))
                .map(c -> summary(c, byCompany.getOrDefault(c.getId(), List.of())))
                .sorted(Comparator.comparing(CompanySummary::latestActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(CompanySummary::name))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyDetail get(Long id) {
        Long userId = CurrentUser.id();
        Company c = companyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.of("Company", id));
        List<JobApplication> apps = applicationRepository.findByCompanyIdWithCompany(userId, id).stream()
                .sorted(Comparator.comparing(JobApplication::getLastActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        CompanySummary s = summary(c, apps);
        Map<String, Long> emailCounts = emailRepository.countBySenderForCompany(userId, id);
        List<ContactDto> contacts = contactRepository.findByUserIdAndCompanyIdOrderByLastContactAtDesc(userId, id).stream()
                .map(ct -> new ContactDto(ct.getId(), ct.getName(), ct.getEmail(), ct.getRole(), ct.getLastContactAt(),
                        emailCounts.getOrDefault(ct.getEmail().toLowerCase(Locale.ROOT), 0L)))
                .toList();
        List<ApplicationFacts.Fact> facts = factsLoader.compute(apps.stream().filter(a -> !a.isArchived()).toList());
        ResponseStats stats = new ResponseStats(analytics.responseRate(facts), analytics.avgResponseDays(facts),
                emailRepository.countForCompany(userId, id));
        return new CompanyDetail(c.getId(), c.getName(), c.getDomain(), applicationService.summaries(apps),
                s.applications(), s.active(), s.interviews(), s.offers(), s.rejected(), s.latestActivityAt(),
                c.getWebsite(), analytics.distribution(apps),
                emailRepository.findForCompany(userId, id, c.getName(), 25).stream()
                        .map(mapper::toInboxItem).toList(),
                contacts,
                eventRepository.findRecentForCompany(userId, id, 50).stream()
                        .map(mapper::toActivity).toList(),
                stats);
    }

    public CompanySummary summary(Company c, List<JobApplication> apps) {
        long active = apps.stream().filter(a -> a.getStatus().isActive()).count();
        long interviews = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW).count();
        long offers = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFER).count();
        long rejected = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count();
        Instant latest = apps.stream().map(JobApplication::getLastActivityAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new CompanySummary(c.getId(), c.getName(), c.getDomain(), apps.size(), active, interviews, offers,
                rejected, latest);
    }
}
