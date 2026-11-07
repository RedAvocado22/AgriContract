package com.agricontract.contract.application.dto;

import java.time.Instant;

/**
 * user-service InternalUserInfoResponse (§4.3). A null authorizationExpiresAt means the authorization has no end;
 * lockedUntil is the reputation lock projection.
 */
public record UserInfo(
        String userId,
        String organizationName,
        String email,
        String role,
        String verificationStatus,
        Instant authorizationExpiresAt,
        Instant lockedUntil
) {}
