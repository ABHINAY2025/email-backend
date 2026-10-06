package com.applyflow.repository;

import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.persistence.RefLoader;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

class EmailMatchSuggestionRepositoryCustomImpl implements EmailMatchSuggestionRepositoryCustom {

    private final MongoOperations ops;
    private final RefLoader refs;

    EmailMatchSuggestionRepositoryCustomImpl(MongoOperations ops, RefLoader refs) {
        this.ops = ops;
        this.refs = refs;
    }

    @Override
    public List<EmailMatchSuggestion> findForEmail(Long emailId) {
        Query q = query(where("emailId").is(emailId)).with(Sort.by(Sort.Order.desc("score"), Sort.Order.asc("_id")));
        List<EmailMatchSuggestion> list = refs.suggestions(ops.find(q, EmailMatchSuggestion.class));
        // Inner-join semantics: drop suggestions whose application no longer exists.
        return list.stream().filter(s -> s.getApplication() != null).toList();
    }

    @Override
    public List<Long> findEmailIdsWithSuggestions(Collection<Long> emailIds) {
        if (emailIds.isEmpty()) {
            return List.of();
        }
        return ops.findDistinct(query(where("emailId").in(emailIds)), "emailId", EmailMatchSuggestion.class,
                Long.class);
    }
}
