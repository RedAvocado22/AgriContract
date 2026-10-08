package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.util.Objects;

/** The authoritative subset of user-service InternalUserInfoResponse needed by inspection. */
public record InspectorEligibility(String userId, String role, String verificationStatus,
                                   Instant authorizationExpiresAt) {
    public InspectorEligibility {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(verificationStatus);
    }

    public boolean isEligibleAt(Instant now) {
        // user-service design section 3 explicitly excludes buyer/seller reputation locks.
        return "INSPECTOR".equals(role) && "VERIFIED".equals(verificationStatus)
                && (authorizationExpiresAt == null || authorizationExpiresAt.isAfter(now));
    }
}
