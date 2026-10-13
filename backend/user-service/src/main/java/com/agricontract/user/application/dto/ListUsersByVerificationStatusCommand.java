package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.VerificationStatus;

// status null means the review queue, i.e. PENDING. page is zero-based.
public record ListUsersByVerificationStatusCommand(
        VerificationStatus status,
        int page,
        int size
) {
}
