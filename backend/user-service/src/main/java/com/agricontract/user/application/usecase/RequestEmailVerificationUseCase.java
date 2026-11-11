package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;

public interface RequestEmailVerificationUseCase {
    // userId: the authenticated principal, never from the request.
    EmailVerificationSentResponse execute(String userId);
}
