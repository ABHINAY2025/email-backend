package com.applyflow.controller;

import com.applyflow.analytics.AnalyticsService;
import com.applyflow.dto.AnalyticsDtos.AnalyticsOverview;
import com.applyflow.dto.AnalyticsDtos.ApplicationsAnalytics;
import com.applyflow.dto.AnalyticsDtos.ResponseRateAnalytics;
import com.applyflow.dto.AnalyticsDtos.StatusAnalytics;
import com.applyflow.dto.CompanyDtos.CompanyDetail;
import com.applyflow.dto.CompanyDtos.CompanySummary;
import com.applyflow.dto.MiscDtos.SearchResults;
import com.applyflow.service.CompanyQueryService;
import com.applyflow.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Companies, analytics and global search (read-only views). */
@RestController
public class InsightsController {

    private final CompanyQueryService companyQueryService;
    private final AnalyticsService analyticsService;
    private final SearchService searchService;

    public InsightsController(CompanyQueryService companyQueryService, AnalyticsService analyticsService,
                              SearchService searchService) {
        this.companyQueryService = companyQueryService;
        this.analyticsService = analyticsService;
        this.searchService = searchService;
    }

    @GetMapping("/api/companies")
    public List<CompanySummary> companies(@RequestParam(required = false) String q) {
        return companyQueryService.list(q, Integer.MAX_VALUE);
    }

    @GetMapping("/api/companies/{id}")
    public CompanyDetail company(@PathVariable Long id) {
        return companyQueryService.get(id);
    }

    @GetMapping("/api/analytics/overview")
    public AnalyticsOverview overview() {
        return analyticsService.overview();
    }

    @GetMapping("/api/analytics/applications")
    public ApplicationsAnalytics applications() {
        return analyticsService.applications();
    }

    @GetMapping("/api/analytics/status")
    public StatusAnalytics status() {
        return analyticsService.status();
    }

    @GetMapping("/api/analytics/response-rate")
    public ResponseRateAnalytics responseRate() {
        return analyticsService.responseRate();
    }

    @GetMapping("/api/search")
    public SearchResults search(@RequestParam(required = false) String q) {
        return searchService.search(q);
    }
}
