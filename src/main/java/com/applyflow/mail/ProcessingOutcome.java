package com.applyflow.mail;

/** Result of processing a single email through the pipeline. */
public record ProcessingOutcome(Result result, Long emailId, Long applicationId, boolean applicationCreated,
                                boolean applicationUpdated) {

    public enum Result {
        DUPLICATE,
        NOT_JOB_RELATED,
        STORED,
        FAILED
    }

    public static ProcessingOutcome duplicate() {
        return new ProcessingOutcome(Result.DUPLICATE, null, null, false, false);
    }

    public static ProcessingOutcome notJobRelated() {
        return new ProcessingOutcome(Result.NOT_JOB_RELATED, null, null, false, false);
    }

    public static ProcessingOutcome failed() {
        return new ProcessingOutcome(Result.FAILED, null, null, false, false);
    }

    public boolean stored() {
        return result == Result.STORED;
    }
}
