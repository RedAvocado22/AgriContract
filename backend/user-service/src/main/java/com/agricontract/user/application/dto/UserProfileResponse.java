package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

@Builder
public record UserProfileResponse(
        String userId,
        String organizationName,
        Role role,
        String email,
        String phone,
        String address,
        VerificationStatus verificationStatus
) {
}
