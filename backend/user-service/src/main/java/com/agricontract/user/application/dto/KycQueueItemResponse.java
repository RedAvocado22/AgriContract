package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;

// KYC queue row: no contact or decision detail, so listing is not a Restricted read. The full profile is only
// available through the audited GET /api/v1/admin/users/{userId} (#155).
public record KycQueueItemResponse(
        String userId,
        String organizationName,
        Role role,
        VerificationStatus verificationStatus
) {
    public static KycQueueItemResponse from(UserProfile profile) {
        return new KycQueueItemResponse(
                profile.getUserId().value(),
                profile.getOrganizationName(),
                profile.getRole(),
                profile.getVerificationStatus());
    }
}
