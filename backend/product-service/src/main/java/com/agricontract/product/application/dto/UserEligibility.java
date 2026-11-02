package com.agricontract.product.application.dto;

import java.time.Instant;

// authorizationExpiresAt null = no expiry; lockedUntil null = not locked (user-service design §2, §4.3).
public record UserEligibility(String userId, String verificationStatus, Instant authorizationExpiresAt,
                              Instant lockedUntil) {

    // Enforcement matrix (user-service design §3): VERIFIED, authorization not expired, not reputation-locked.
    public boolean allowsTradingAt(Instant now) {
        return "VERIFIED".equals(verificationStatus)
                && (authorizationExpiresAt == null || authorizationExpiresAt.isAfter(now))
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }
}
