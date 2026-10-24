package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

import java.time.Instant;

// Result of a verify/reject decision. Carries no contact info: the reviewer already opened the profile through the
// audited detail read, and a decision response must not become a second unaudited way to read it (#155).
@Builder
public record KycDecisionResponse(
        String userId,
        VerificationStatus verificationStatus,
        Instant authorizationExpiresAt,
        String verifiedByActorId,
        Instant verifiedAt,
        String rejectionReason
) {
    public static KycDecisionResponse from(UserProfile profile) {
        return KycDecisionResponse.builder()
                .userId(profile.getUserId().value())
                .verificationStatus(profile.getVerificationStatus())
                .authorizationExpiresAt(profile.getAuthorizationExpiresAt())
                .verifiedByActorId(profile.getVerifiedByActorId())
                .verifiedAt(profile.getVerifiedAt())
                .rejectionReason(profile.getRejectionReason())
                .build();
    }
}
