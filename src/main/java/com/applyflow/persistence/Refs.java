package com.applyflow.persistence;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Component;

/**
 * Fallback resolver for id-based references between documents ({@code companyId}, {@code applicationId}, ...).
 * List endpoints batch-load referenced documents through {@link RefLoader}; this resolver only loads a single
 * reference on first access when it has not been pre-loaded (e.g. a freshly loaded single document). Uses the shared
 * {@link MongoOperations}, so it participates in the current Mongo transaction when there is one.
 */
@Component
public class Refs {

    private static volatile MongoOperations operations;

    public Refs(MongoOperations mongoOperations) {
        operations = mongoOperations;
    }

    public static <T> T find(Class<T> type, Long id) {
        if (id == null) {
            return null;
        }
        MongoOperations ops = operations;
        if (ops == null) {
            throw new IllegalStateException("MongoDB is not initialised yet");
        }
        return ops.findById(id, type);
    }
}
