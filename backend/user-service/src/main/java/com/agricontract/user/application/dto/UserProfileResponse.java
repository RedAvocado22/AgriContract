package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

import java.time.Instant;

// The owner's own view: email is shown even before it is verified, with emailVerified telling the client to ask for a code.
@Builder
public record UserProfileResponse(
        String userId,
        String organizationName,
        Role role,
        String email,
        boolean emailVerified,
        String phone,
        String address,
        VerificationStatus verificationStatus,
        Instant authorizationExpiresAt
) {
    public static UserProfileResponse of(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId().value())
                .organizationName(profile.getOrganizationName())
                .role(profile.getRole())
                .email(profile.getContactInfo().email())
                .emailVerified(profile.isEmailVerified())
                .phone(profile.getContactInfo().phone())
                .address(profile.getContactInfo().address())
                .verificationStatus(profile.getVerificationStatus())
                .authorizationExpiresAt(profile.getAuthorizationExpiresAt())
                .build();
    }
}
