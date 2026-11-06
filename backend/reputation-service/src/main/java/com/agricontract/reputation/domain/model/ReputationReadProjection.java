package com.agricontract.reputation.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Shared score and effective lock projection for public and internal reads. */
public record ReputationReadProjection(UUID userId, BigDecimal score, boolean locked) {
    public static BigDecimal score(long completed, long locks) {
        if (completed < 0 || locks < 0) {
            throw new IllegalArgumentException("Negative fact count");
        }
        BigDecimal positive = BigDecimal.valueOf(completed);
        BigDecimal total = positive.add(BigDecimal.valueOf(locks));
        return total.signum() == 0 ? null
                : positive.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP);
    }
}
