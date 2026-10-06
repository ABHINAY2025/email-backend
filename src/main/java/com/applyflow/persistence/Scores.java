package com.applyflow.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Confidences/scores are stored like the former NUMERIC(4,3) columns: clamped to [0,1], 3 decimals. */
public final class Scores {

    private Scores() {
    }

    public static Double round3(Double value) {
        if (value == null) {
            return null;
        }
        if (value.isNaN()) {
            return 0.0;
        }
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
