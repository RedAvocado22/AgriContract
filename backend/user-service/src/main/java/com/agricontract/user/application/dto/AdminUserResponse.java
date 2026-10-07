package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

import java.time.Instant;

// KYC review view for ADMIN/OPERATOR only; contact info is needed to reach the applicant.
@Builder
public record AdminUserResponse(
        String userId,
        String organizationName,
        Role role,
        String email,
        String phone,
        VerificationStatus verificationStatus,
        Instant authorizationExpiresAt,
        String verifiedByActorId,
        Instant verifiedAt,
        String rejectionReason
) {
    public static AdminUserResponse from(UserProfile profile) {
        return AdminUserResponse.builder()
                .userId(profile.getUserId().value())
                .organizationName(profile.getOrganizationName())
                .role(profile.getRole())
                .email(profile.getContactInfo().email())
                .phone(profile.getContactInfo().phone())
                .verificationStatus(profile.getVerificationStatus())
                .authorizationExpiresAt(profile.getAuthorizationExpiresAt())
                .verifiedByActorId(profile.getVerifiedByActorId())
                .verifiedAt(profile.getVerifiedAt())
                .rejectionReason(profile.getRejectionReason())
                .build();
    }
}
