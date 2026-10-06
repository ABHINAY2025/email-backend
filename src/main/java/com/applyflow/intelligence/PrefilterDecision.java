package com.applyflow.intelligence;

/** Result of the cheap header-only pre-filter run before message bodies are downloaded. */
public enum PrefilterDecision {
    /** Clearly not job related; the body is never downloaded. */
    REJECT,
    /** Strong job signal in headers. */
    PASS,
    /** Unknown; download the body and classify fully. */
    AMBIGUOUS
}
