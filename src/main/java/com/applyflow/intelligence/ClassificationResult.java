package com.applyflow.intelligence;

import com.applyflow.common.EmailClassification;

import java.util.List;

/**
 * Output of email classification.
 *
 * @param confidence         0..1
 * @param reason             human-readable list of the strongest matched signals
 * @param signals            all matched signals (for the audit log)
 * @param progressIndicated  true when an APPLICATION_UPDATE indicates review/progress (maps to UNDER_REVIEW)
 */
public record ClassificationResult(EmailClassification classification, double confidence, String reason,
                                   List<String> signals, boolean progressIndicated) {

    public boolean isJobRelated() {
        return classification != null && classification.isJobRelated();
    }

    public static ClassificationResult notJobRelated(String reason, List<String> signals, double confidence) {
        return new ClassificationResult(EmailClassification.NOT_JOB_RELATED, confidence, reason, signals, false);
    }
}
