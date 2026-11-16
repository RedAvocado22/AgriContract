package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationConfirmation;

public interface ConfirmEmailVerificationUseCase {
    // userId: the authenticated principal, never from the request.
    EmailVerificationConfirmation execute(String userId, String code);
}
