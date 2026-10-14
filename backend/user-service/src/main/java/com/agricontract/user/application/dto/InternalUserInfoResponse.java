package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Builder;

// Service-to-service only; never reuse the public UserInfoResponse for internal callers (KI-3).
@Builder
public record InternalUserInfoResponse(String userId,
                                       String organizationName,
                                       String email,
                                       Role role,
                                       VerificationStatus verificationStatus
) {
}
