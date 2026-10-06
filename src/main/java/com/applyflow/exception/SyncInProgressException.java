package com.applyflow.exception;

public class SyncInProgressException extends ConflictException {

    public SyncInProgressException(String email) {
        super("SYNC_IN_PROGRESS", "A sync is already running for " + email + ".");
    }
}
