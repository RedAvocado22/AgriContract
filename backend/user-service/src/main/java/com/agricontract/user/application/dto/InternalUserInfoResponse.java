package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

// Frozen service-to-service DTO (user-service design §4.3); never reuse the public UserInfoResponse.
// Nulls are always written: callers read a JSON null authorizationExpiresAt as "no expiry" and a missing key as malformed.
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InternalUserInfoResponse(String userId,
                                       String organizationName,
                                       String email,
                                       Role role,
                                       VerificationStatus verificationStatus,
                                       Instant authorizationExpiresAt,
                                       Instant lockedUntil
) {
}
