package com.applyflow.application.service;

import com.applyflow.entity.Company;
import com.applyflow.mail.classifier.CompanyNames;
import com.applyflow.mail.classifier.SenderAnalyzer;
import com.applyflow.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    /** Finds a company by normalized name (or domain) or creates it. */
    @Transactional
    public Company findOrCreate(String name, String domain, boolean demo) {
        String display = name == null || name.isBlank() ? "Unknown company" : name.trim();
        String normalized = CompanyNames.normalize(display);
        if (normalized.isBlank()) {
            normalized = display.toLowerCase();
        }
        String cleanDomain = domain == null || SenderAnalyzer.isIntermediaryDomain(domain) ? null
                : CompanyNames.registrableDomain(domain);
        Optional<Company> existing = repository.findByNormalizedName(normalized);
        if (existing.isEmpty() && cleanDomain != null) {
            existing = repository.findByDomainIgnoreCase(cleanDomain).stream().findFirst();
        }
        if (existing.isPresent()) {
            Company c = existing.get();
            boolean changed = false;
            if (c.getDomain() == null && cleanDomain != null) {
                c.setDomain(cleanDomain);
                changed = true;
            }
            if (!demo && c.isDemo()) {
                c.setDemo(false); // real data now references it; keep it when demo data is cleared
                changed = true;
            }
            return changed ? repository.save(c) : c;
        }
        Company c = new Company();
        c.setName(display.length() > 255 ? display.substring(0, 255) : display);
        c.setNormalizedName(normalized.length() > 255 ? normalized.substring(0, 255) : normalized);
        c.setDomain(cleanDomain);
        c.setWebsite(cleanDomain == null ? null : "https://" + cleanDomain);
        c.setDemo(demo);
        return repository.save(c);
    }
}
