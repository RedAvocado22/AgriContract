package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

// Public view of another user (contract PublicUserResponse, user design §4.1): no email, phone or address.
// Services that need contact data use GET /internal/v1/users/{userId} instead (design §4.3, KI-3).
@Builder
public record PublicUserResponse(String userId,
                                 String organizationName,
                                 Role role,
                                 VerificationStatus verificationStatus
) {
}
