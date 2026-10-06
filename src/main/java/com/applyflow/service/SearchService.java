package com.applyflow.service;

import com.applyflow.application.service.ApplicationService;
import com.applyflow.application.service.ApplicationFilters;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.dto.ApplicationDtos.ApplicationSummary;
import com.applyflow.dto.InboxDtos.InboxItem;
import com.applyflow.dto.MiscDtos.SearchResults;
import com.applyflow.entity.JobApplication;
import com.applyflow.exception.BadRequestException;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SearchService {

    private final JobApplicationRepository applicationRepository;
    private final EmailMessageRepository emailRepository;
    private final ApplicationService applicationService;
    private final CompanyQueryService companyQueryService;
    private final DtoMapper mapper;

    public SearchService(JobApplicationRepository applicationRepository, EmailMessageRepository emailRepository,
                         ApplicationService applicationService, CompanyQueryService companyQueryService,
                         DtoMapper mapper) {
        this.applicationRepository = applicationRepository;
        this.emailRepository = emailRepository;
        this.applicationService = applicationService;
        this.companyQueryService = companyQueryService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SearchResults search(String q) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("Query parameter 'q' must contain at least 1 character.");
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        Long id = ApplicationFilters.parseId(needle);
        List<JobApplication> apps = applicationRepository.findAllWithCompany().stream()
                .filter(a -> matches(a, needle, id))
                .sorted(Comparator.comparing(JobApplication::isArchived)
                        .thenComparing(JobApplication::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .toList();
        List<ApplicationSummary> summaries = applicationService.summaries(apps);
        List<InboxItem> emails = emailRepository.search(needle, 6).stream()
                .map(mapper::toInboxItem).toList();
        return new SearchResults(summaries, emails, companyQueryService.list(needle, 5));
    }

    private static boolean matches(JobApplication a, String needle, Long id) {
        if (id != null && id.equals(a.getId())) {
            return true;
        }
        ApplicationStatus s = a.getStatus();
        return contains(a.getCompany().getName(), needle) || contains(a.getJobTitle(), needle)
                || contains(a.getLocation(), needle) || contains(a.getRecruiterName(), needle)
                || contains(a.getRecruiterEmail(), needle) || contains(a.displayId(), needle)
                || contains(s.name().replace('_', ' '), needle) || contains(s.label(), needle);
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle);
    }
}
