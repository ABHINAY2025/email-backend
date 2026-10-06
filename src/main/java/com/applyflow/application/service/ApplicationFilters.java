package com.applyflow.application.service;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.entity.Company;
import com.applyflow.entity.EmailMessage;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** MongoDB criteria for the GET /api/applications filters. User input is always regex-quoted. */
public final class ApplicationFilters {

    private ApplicationFilters() {
    }

    public static Criteria build(ApplicationQuery f, ZoneId zone, MongoOperations ops) {
        List<Criteria> p = new ArrayList<>();
        p.add(where("archived").is(f.archived()));

        Set<ApplicationStatus> bucket = bucketStatuses(f.bucket());
        if (bucket != null) {
            p.add(where("status").in(bucket));
        }
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            p.add(where("status").in(f.statuses()));
        }
        if (f.companyId() != null) {
            p.add(where("companyId").is(f.companyId()));
        }
        if (f.from() != null) {
            p.add(where("appliedAt").gte(f.from().atStartOfDay(zone).toInstant()));
        }
        if (f.to() != null) {
            p.add(where("appliedAt").lt(f.to().plusDays(1).atStartOfDay(zone).toInstant()));
        }
        if (notBlank(f.location())) {
            p.add(where("location").regex(contains(f.location()), "i"));
        }
        if (notBlank(f.source())) {
            p.add(where("source").regex("^" + Pattern.quote(f.source().trim()) + "$", "i"));
        }
        if (f.emailAccountId() != null) {
            p.add(where("emailAccountId").is(f.emailAccountId()));
        }
        if (notBlank(f.jobTitle())) {
            p.add(where("jobTitle").regex(contains(f.jobTitle()), "i"));
        }
        if (notBlank(f.q())) {
            p.add(text(f.q(), ops));
        }
        return new Criteria().andOperator(p);
    }

    /**
     * Free text: company name, title, location, recruiter, reference, display id ("AF-12") or the subject of a linked
     * email.
     */
    private static Criteria text(String raw, MongoOperations ops) {
        String q = raw.trim();
        String regex = contains(q);
        List<Criteria> or = new ArrayList<>();

        Query companies = query(where("name").regex(regex, "i"));
        companies.fields().include("_id");
        List<Long> companyIds = ops.find(companies, Company.class).stream().map(Company::getId).toList();
        if (!companyIds.isEmpty()) {
            or.add(where("companyId").in(companyIds));
        }
        or.add(where("jobTitle").regex(regex, "i"));
        or.add(where("location").regex(regex, "i"));
        or.add(where("recruiterName").regex(regex, "i"));
        or.add(where("recruiterEmail").regex(regex, "i"));
        or.add(where("applicationRef").regex(regex, "i"));
        Long id = parseId(q);
        if (id != null) {
            or.add(where("_id").is(id));
        }
        List<Long> bySubject = ops.findDistinct(
                query(where("applicationId").ne(null).and("subject").regex(regex, "i")), "applicationId",
                EmailMessage.class, Long.class);
        if (!bySubject.isEmpty()) {
            or.add(where("_id").in(bySubject));
        }
        return new Criteria().orOperator(or);
    }

    public static Set<ApplicationStatus> bucketStatuses(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return null;
        }
        return switch (bucket.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ApplicationStatus.active();
            case "interviews" -> EnumSet.of(ApplicationStatus.INTERVIEW);
            case "offers" -> EnumSet.of(ApplicationStatus.OFFER);
            case "rejected" -> EnumSet.of(ApplicationStatus.REJECTED);
            case "waiting" -> EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW);
            default -> null; // "all"
        };
    }

    /** Accepts "AF-123", "af123" or "123". */
    public static Long parseId(String q) {
        String s = q.trim().toUpperCase(Locale.ROOT).replaceFirst("^AF-?", "");
        if (s.matches("\\d{1,18}")) {
            return Long.parseLong(s);
        }
        return null;
    }

    /** Case-insensitive "contains" regex for user input (quoted: no regex injection). */
    private static String contains(String s) {
        return Pattern.quote(s.trim());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
