package com.agricontract.user.domain.model;

import java.time.Duration;
import java.time.Instant;

// D59: a self-registered account whose email is not verified within maxAge of its creation is deleted. The deadline
// is fixed at creation; sending a new code does not extend it.
public record UnverifiedAccountPolicy(Duration maxAge) {

    public UnverifiedAccountPolicy {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Unverified account max age must be positive");
        }
    }

    // Accounts created before this instant are past their deadline.
    public Instant deletionCutoff(Instant now) {
        return now.minus(maxAge);
    }
}
