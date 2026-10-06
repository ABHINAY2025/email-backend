package com.applyflow.repository;

import com.applyflow.entity.EmailMatchSuggestion;

import java.util.Collection;
import java.util.List;

public interface EmailMatchSuggestionRepositoryCustom {

    /** Suggestions for an email, best first, with their application and company loaded. */
    List<EmailMatchSuggestion> findForEmail(Long emailId);

    List<Long> findEmailIdsWithSuggestions(Collection<Long> emailIds);
}
