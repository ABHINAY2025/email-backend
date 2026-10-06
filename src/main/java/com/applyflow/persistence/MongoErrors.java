package com.applyflow.persistence;

import com.mongodb.MongoException;
import org.springframework.dao.DuplicateKeyException;

/** Classification of MongoDB failures (unique violations, retryable transaction conflicts). */
public final class MongoErrors {

    private MongoErrors() {
    }

    /** A unique-index violation (E11000), however Spring wrapped it. */
    public static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof DuplicateKeyException || c instanceof com.mongodb.DuplicateKeyException) {
                return true;
            }
            if (c instanceof MongoException me && me.getCode() == 11000) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    /**
     * A transaction that may succeed if simply run again: a write conflict with a concurrent transaction
     * ({@code TransientTransactionError}) or an ambiguous commit ({@code UnknownTransactionCommitResult}).
     */
    public static boolean isTransientTransactionError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof MongoException me && (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || me.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                    || me.getCode() == 112 /* WriteConflict */)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
