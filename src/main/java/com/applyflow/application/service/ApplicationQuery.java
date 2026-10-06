package com.applyflow.application.service;

import com.applyflow.common.ApplicationStatus;

import java.time.LocalDate;
import java.util.List;

/** Filters for GET /api/applications. */
public record ApplicationQuery(
        String q,
        String bucket,
        List<ApplicationStatus> statuses,
        Long companyId,
        LocalDate from,
        LocalDate to,
        String location,
        String source,
        Long emailAccountId,
        String jobTitle,
        boolean archived,
        String sort,
        String dir,
        int page,
        int size) {
}
